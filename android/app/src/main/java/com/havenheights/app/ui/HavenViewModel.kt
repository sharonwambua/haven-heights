package com.havenheights.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havenheights.app.HavenApplication
import com.havenheights.app.data.*
import com.havenheights.app.data.local.PendingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class HavenViewModel(private val app: HavenApplication) : ViewModel() {

    private val repo = app.repository
    private val session = app.session
    private val dao = app.dao
    private val cache = app.cache

    private val _user = MutableStateFlow(session.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private var inFlight = 0

    /** Things done offline that have not reached the server yet. */
    val pending: StateFlow<List<PendingItem>> =
        dao.pendingFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tenants = MutableStateFlow<List<Tenant>>(emptyList())
    /** Tenants, with the latest reading adjusted for readings still waiting to upload. */
    val tenants: StateFlow<List<Tenant>> =
        combine(_tenants, pending) { list, items -> withQueuedReadings(list, items) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _readings = MutableStateFlow<Map<Int, List<Reading>>>(emptyMap())
    val readings = _readings.asStateFlow()
    private val _requests = MutableStateFlow<List<MaintenanceRequest>>(emptyList())
    val requests = _requests.asStateFlow()
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements = _announcements.asStateFlow()
    private val _overview = MutableStateFlow<Overview?>(null)
    val overview = _overview.asStateFlow()
    private val _usage = MutableStateFlow<MyUsage?>(null)
    val usage = _usage.asStateFlow()
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills = _bills.asStateFlow()
    private val _me = MutableStateFlow<Me?>(null)
    val me = _me.asStateFlow()
    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard = _dashboard.asStateFlow()
    private val _inbox = MutableStateFlow<Inbox?>(null)
    val inbox = _inbox.asStateFlow()

    /** One-off messages shown in a snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    fun toast(text: String) { messages.tryEmit(text) }

    init {
        // Show what was saved last time straight away, then refresh from the server.
        viewModelScope.launch {
            if (_user.value == null) return@launch
            cache.get<List<Tenant>>("tenants")?.let { _tenants.value = it }
            cache.get<List<MaintenanceRequest>>("requests")?.let { _requests.value = it }
            cache.get<List<Announcement>>("announcements")?.let { _announcements.value = it }
            cache.get<Overview>("overview")?.let { _overview.value = it }
            cache.get<MyUsage>("usage")?.let { _usage.value = it }
            cache.get<List<Bill>>("bills")?.let { _bills.value = it }
            cache.get<Me>("me")?.let { _me.value = it }
            cache.get<Dashboard>("dashboard")?.let { _dashboard.value = it }
            cache.get<Inbox>("inbox")?.let { _inbox.value = it }
        }
        // When queued items finish uploading, pull the fresh numbers.
        viewModelScope.launch {
            var last = -1
            pending.collect { if (last > it.size) refreshAll(); last = it.size }
        }
        if (_user.value != null) app.push.register()
        // A push arrived while the app is open: fetch the change.
        viewModelScope.launch { app.refreshSignal.collect { refreshAll() } }
    }

    private fun withQueuedReadings(list: List<Tenant>, items: List<PendingItem>): List<Tenant> {
        if (items.isEmpty()) return list
        val queued = items.filter { it.kind == KIND_READING }
            .mapNotNull { runCatching { HavenJson.gson.fromJson(it.payload, PendingReading::class.java) }.getOrNull() }
            .associate { it.tenancyId to it.value }   // later items win
        return list.map { t ->
            val v = queued[t.tenancyId]
            if (v != null && v > t.latestReading) t.copy(latestReading = v) else t
        }
    }

    // ------------------------------------------------------------ plumbing

    private var lastOfflineNotice = 0L
    private fun failed(ex: ApiException) {
        if (ex.code == 0) {
            val now = System.currentTimeMillis()
            if (now - lastOfflineNotice > 20_000) {
                lastOfflineNotice = now
                toast("You're offline. Showing what was saved on this phone.")
            }
        } else toast(ex.message)
    }

    private fun <T> request(
        call: suspend () -> Result<T>,
        onFail: (ApiException) -> Unit = ::failed,
        onOk: (T) -> Unit = {},
    ) {
        viewModelScope.launch {
            inFlight++; _busy.value = true
            call()
                .onSuccess(onOk)
                .onFailure { e ->
                    val ex = e as ApiException
                    if (ex.code == 401 && _user.value != null) {
                        logout()
                        toast("Your session has ended. Sign in again.")
                    } else onFail(ex)
                }
            inFlight--; _busy.value = inFlight > 0
        }
    }

    /** Fetch, publish to the screen, and remember it for offline use. */
    private fun <T : Any> load(key: String, call: suspend () -> Result<T>, set: (T) -> Unit) =
        request(call) { value -> set(value); viewModelScope.launch { cache.put(key, value) } }

    // ------------------------------------------------------------ session

    fun login(phone: String, password: String, onError: (String) -> Unit) = request(
        call = { repo.login(phone, password) },
        onFail = { onError(it.message) },
    ) { auth ->
        session.save(auth)
        _user.value = session.user()
        app.push.register()
        app.scheduleSync()
        refreshAll()
    }

    fun logout() {
        if (_user.value == null) return
        _user.value = null   // the UI returns to the sign-in screen at once
        viewModelScope.launch {
            withTimeoutOrNull(3000) { app.push.unregister() }   // stop notifications for this account
            session.clear()
            cache.dao.clearCache()
            _tenants.value = emptyList(); _readings.value = emptyMap(); _requests.value = emptyList()
            _announcements.value = emptyList(); _overview.value = null; _usage.value = null
            _bills.value = emptyList(); _me.value = null; _dashboard.value = null; _inbox.value = null
        }
    }

    fun changePassword(current: String, new: String, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.changePassword(current, new) },
        onFail = { onError(it.message) },
    ) { toast("Password changed"); onDone() }

    fun refreshAll() {
        val u = _user.value ?: return
        loadAnnouncements(); loadRequests(); loadInbox(); loadMe(); loadBills()
        if (u.isLandlord) { loadTenants(); loadOverview() } else { loadMyUsage(); loadDashboard() }
    }

    // ------------------------------------------------------------ loaders

    fun loadTenants() = load("tenants", { repo.tenants() }) { _tenants.value = it }
    fun loadOverview() = load("overview", { repo.overview() }) { _overview.value = it }
    fun loadRequests() = load("requests", { repo.requests() }) { _requests.value = it }
    fun loadAnnouncements() = load("announcements", { repo.announcements() }) { _announcements.value = it }
    fun loadBills() = load("bills", { repo.bills() }) { _bills.value = it }
    fun loadMe() = load("me", { repo.me() }) { _me.value = it }
    fun loadDashboard() = load("dashboard", { repo.dashboard() }) { _dashboard.value = it }
    fun loadInbox() = load("inbox", { repo.inbox() }) { _inbox.value = it }
    fun loadMyUsage() = load("usage", { repo.myUsage() }) { _usage.value = it }

    fun loadReadings(tenancyId: Int) {
        viewModelScope.launch {
            if (_readings.value[tenancyId] == null) {
                cache.get<List<Reading>>("readings:$tenancyId")?.let { _readings.value = _readings.value + (tenancyId to it) }
            }
        }
        load("readings:$tenancyId", { repo.readings(tenancyId) }) { _readings.value = _readings.value + (tenancyId to it) }
    }

    // ------------------------------------------------------------ tenants

    fun registerTenant(body: TenantCreate, onError: (String) -> Unit, onDone: (TenantCreated) -> Unit) = request(
        call = { repo.registerTenant(body) },
        onFail = { onError(if (it.code == 0) "You need a connection to register a tenant." else it.message) },
    ) { created -> loadTenants(); loadOverview(); onDone(created) }

    fun moveOut(tenancyId: Int, onDone: () -> Unit) = request({ repo.moveOut(tenancyId) }) {
        loadTenants(); loadOverview(); toast("${it.name} has moved out. Unit ${it.unitNumber} is vacant."); onDone()
    }

    // ------------------------------------------------------------ meter readings

    /** [recordedAt] is an ISO instant when the reading is for an earlier day (photos taken beforehand); null means now. */
    fun scanMeter(photo: File, onResult: (Result<ReadingSuggestion>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.scanReading(photo) }
            onResult(result)
        }
    }

    fun saveReading(
        tenant: Tenant, value: Double, photo: File?, confirmJump: Boolean, recordedAt: String?,
        source: String = "MANUAL", onResult: (SaveResult) -> Unit,
    ) = request(
            call = { repo.saveReading(tenant.tenancyId, value, photo, confirmJump, recordedAt, source) },
            onFail = { ex ->
                when (ex.code) {
                    0 -> queueReading(tenant, value, photo, confirmJump, recordedAt, source, onResult)
                    409 -> onResult(SaveResult.NeedsConfirm(ex.message))
                    else -> onResult(SaveResult.Failed(ex.message))
                }
            },
        ) { reading ->
            loadTenants(); loadOverview(); loadReadings(tenant.tenancyId); loadBills()
            onResult(SaveResult.Saved(reading))
        }

    private fun queueReading(
        tenant: Tenant, value: Double, photo: File?, confirmJump: Boolean, recordedAt: String?,
        source: String, onResult: (SaveResult) -> Unit,
    ) {
        val owner = _user.value?.let { ownerKey(it) } ?: return
        viewModelScope.launch {
            val kept = photo?.let { keepPhoto(it) }
            val payload = PendingReading(
                tenant.tenancyId, tenant.unitNumber, value, confirmJump,
                recordedAt ?: Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), source,
            )
            dao.addPending(PendingItem(kind = KIND_READING, payload = HavenJson.gson.toJson(payload),
                photoPath = kept?.path, owner = owner, createdAt = System.currentTimeMillis()))
            app.scheduleSync()
            onResult(SaveResult.Queued)
        }
    }

    /** Photos taken for a queued reading move out of the cache folder, which Android may clear. */
    private suspend fun keepPhoto(photo: File): File = withContext(Dispatchers.IO) {
        val dir = File(app.filesDir, "pending").apply { mkdirs() }
        File(dir, photo.name).also { photo.copyTo(it, overwrite = true) }
    }

    fun deleteReading(readingId: Int, tenancyId: Int) = request({ repo.deleteReading(readingId) }) {
        loadTenants(); loadOverview(); loadReadings(tenancyId); loadBills(); toast("Reading deleted")
    }

    fun loadPhoto(readingId: Int, onResult: (ByteArray?) -> Unit) = request(
        call = { repo.photo(readingId) },
        onFail = { toast(if (it.code == 0) "You need a connection to view photos." else it.message); onResult(null) },
    ) { onResult(it) }

    // ------------------------------------------------------------ queue management

    fun discardPending(item: PendingItem) = viewModelScope.launch {
        dao.deletePending(item)
        item.photoPath?.let { File(it).delete() }
    }

    /** After the server flagged a big jump, the landlord can send it anyway. */
    fun retryPending(item: PendingItem, confirmJump: Boolean) = viewModelScope.launch {
        var payload = item.payload
        if (confirmJump && item.kind == KIND_READING) {
            val p = HavenJson.gson.fromJson(item.payload, PendingReading::class.java)
            payload = HavenJson.gson.toJson(p.copy(confirmJump = true))
        }
        dao.updatePending(item.copy(payload = payload, error = null, errorCode = 0))
        app.scheduleSync()
    }

    // ------------------------------------------------------------ maintenance

    fun createRequest(body: NewRequest, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.createRequest(body) },
        onFail = { ex ->
            if (ex.code == 0) {
                val owner = _user.value?.let { ownerKey(it) }
                if (owner != null) viewModelScope.launch {
                    dao.addPending(PendingItem(kind = KIND_REQUEST, payload = HavenJson.gson.toJson(body),
                        photoPath = null, owner = owner, createdAt = System.currentTimeMillis()))
                    app.scheduleSync()
                    toast("Saved on this phone. It will send when you're back online.")
                    onDone()
                }
            } else onError(ex.message)
        },
    ) { loadRequests(); toast("Request sent to your landlord"); onDone() }

    fun updateRequest(id: Int, status: String, note: String?) = request({ repo.updateRequest(id, status, note) }) { updated ->
        _requests.value = _requests.value.map { if (it.id == id) updated else it }
        loadOverview(); toast("Request marked ${statusLabel(status).lowercase()}")
    }

    // ------------------------------------------------------------ announcements

    fun postAnnouncement(body: NewAnnouncement, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.postAnnouncement(body) },
        onFail = { onError(if (it.code == 0) "You need a connection to post a notice." else it.message) },
    ) { loadAnnouncements(); toast("Notice posted to all tenants"); onDone() }

    fun deleteAnnouncement(id: Int) = request({ repo.deleteAnnouncement(id) }) {
        _announcements.value = _announcements.value.filterNot { it.id == id }; toast("Notice deleted")
    }

    // ------------------------------------------------------------ profile, estate, lease

    private fun needsConnection(ex: ApiException, what: String) =
        if (ex.code == 0) "You need a connection to $what." else ex.message

    fun updateProfile(name: String, email: String, emergency: String, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.updateMe(ProfileUpdate(name.trim(), email.trim(), emergency.trim())) },
        onFail = { onError(needsConnection(it, "save your details")) },
    ) { me ->
        _me.value = me
        session.updateName(me.name); _user.value = session.user()
        if (_user.value?.isLandlord == false) loadDashboard()
        toast("Details saved"); onDone()
    }

    fun updateEstate(name: String, address: String, paymentInfo: String, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.updateEstate(EstateUpdate(name.trim(), address.trim(), paymentInfo.trim())) },
        onFail = { onError(needsConnection(it, "save estate details")) },
    ) { estate -> _me.value = _me.value?.copy(estate = estate); toast("Estate details saved"); onDone() }

    fun updateLease(tenancyId: Int, body: LeaseUpdate, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.updateLease(tenancyId, body) },
        onFail = { onError(needsConnection(it, "save the lease")) },
    ) { loadTenants(); toast("Lease saved"); onDone() }

    fun uploadLease(tenancyId: Int, uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            val resolver = app.contentResolver
            val mime = resolver.getType(uri) ?: "application/pdf"
            val name = withContext(Dispatchers.IO) {
                resolver.query(uri, null, null, null, null)?.use { c ->
                    val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0 && c.moveToFirst()) c.getString(col) else null
                }
            } ?: "lease"
            val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
            if (bytes == null) { toast("Couldn't read that file."); return@launch }
            request({ repo.uploadLease(tenancyId, name, mime, bytes) },
                onFail = { toast(needsConnection(it, "attach the agreement")) }) {
                loadTenants(); toast("Agreement attached"); onDone()
            }
        }
    }

    /** Downloads the agreement into the cache folder and hands back the file to open. */
    fun loadLeaseFile(tenancyId: Int, fileName: String?, onResult: (File?) -> Unit) = request(
        call = { repo.leaseFile(tenancyId) },
        onFail = { toast(needsConnection(it, "open the agreement")); onResult(null) },
    ) { bytes ->
        viewModelScope.launch {
            val ext = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in listOf("pdf", "jpg", "jpeg", "png") } ?: "pdf"
            val file = withContext(Dispatchers.IO) {
                File(app.cacheDir, "lease").apply { mkdirs() }.let { File(it, "agreement_$tenancyId.$ext").also { f -> f.writeBytes(bytes) } }
            }
            onResult(file)
        }
    }

    // ------------------------------------------------------------ bills

    fun createBill(body: NewBill, onError: (String) -> Unit, onDone: () -> Unit) = request(
        call = { repo.createBill(body) },
        onFail = { onError(needsConnection(it, "create a bill")) },
    ) { loadBills(); loadOverview(); toast("Bill sent to the tenant"); onDone() }

    fun setBillStatus(id: Int, status: String) = request({ repo.setBillStatus(id, status) }) { updated ->
        _bills.value = _bills.value.map { if (it.id == id) updated else it }
        loadOverview(); toast(if (status == "PAID") "Marked as paid" else "Marked as unpaid")
    }

    fun deleteBill(id: Int) = request({ repo.deleteBill(id) }) {
        _bills.value = _bills.value.filterNot { it.id == id }; loadOverview(); toast("Bill deleted")
    }

    fun generateRent() = request({ repo.generateRent() }) { run ->
        loadBills(); loadOverview()
        toast(if (run.created == 0) "Every tenant already has this month's rent bill." else "Created ${run.created} rent bill${if (run.created == 1) "" else "s"}.")
    }

    // ------------------------------------------------------------ notification inbox

    fun markInboxRead() = request({ repo.markInboxRead() }, onFail = {}) {
        _inbox.value = _inbox.value?.let { box -> box.copy(unread = 0, items = box.items.map { it.copy(read = true) }) }
    }

    // ------------------------------------------------------------ payments

    fun submitPayment(id: Int, reference: String, onDone: () -> Unit = {}) = request(
        call = { repo.submitPayment(id, reference) },
        onFail = { toast(needsConnection(it, "submit a payment")) },
    ) { updated ->
        _bills.value = _bills.value.map { if (it.id == id) updated else it }
        loadDashboard(); toast("Sent. Your landlord will confirm your payment."); onDone()
    }

    /** Landlord decision on a payment the tenant says they made. The tenant sees it on their next refresh or push. */
    fun reviewBill(id: Int, approve: Boolean, note: String?) = request({ repo.reviewBill(id, approve, note) }) { updated ->
        _bills.value = _bills.value.map { if (it.id == id) updated else it }
        loadOverview()
        toast(if (approve) "Payment approved. The tenant can see it now." else "Marked as not received. The tenant has been told.")
    }

    // ------------------------------------------------------------ photos chosen from the gallery

    class ImportedPhoto(val file: File, val takenAtMillis: Long?)

    /** Copies a gallery photo into the app, fixes its rotation, shrinks it, and reads the date it was taken. */
    fun importPhoto(uri: Uri, onResult: (ImportedPhoto?) -> Unit) {
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { copyAndShrink(uri) }.getOrNull() }
            if (photo == null) toast("Couldn't use that photo. Try another one.")
            onResult(photo)
        }
    }

    private fun copyAndShrink(uri: Uri): ImportedPhoto {
        val resolver = app.contentResolver
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var takenAt: Long? = null
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { text ->
                runCatching {
                    LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2   // phone photos are huge; 2048px is plenty
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not decode")
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (degrees == 0f) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
        val out = File(File(app.cacheDir, "meter").apply { mkdirs() }, "picked_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return ImportedPhoto(out, takenAt)
    }
}
