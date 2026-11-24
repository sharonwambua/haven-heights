@file:OptIn(ExperimentalMaterial3Api::class)

package com.havenheights.app.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.data.*
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- shared bits

@Composable
fun UnitBadge(unit: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp).background(HavenColors.SageMist, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(unit, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = HavenColors.SageDeep)
    }
}

@Composable
fun ReadingRow(r: Reading, onPhoto: ((Reading) -> Unit)? = null) {
    HavenCard(onClick = if (r.hasPhoto && onPhoto != null) ({ onPhoto(r) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(units(r.value), style = MaterialTheme.typography.titleMedium)
                Text(friendlyDate(r.createdAt), style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                if (r.source == "AI" || r.hasPhoto) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (r.source == "AI") Pill("Auto-read", HavenColors.SageMist, HavenColors.SageDeep)
                        if (r.hasPhoto) Pill("View photo", HavenColors.Nude, HavenColors.Brown)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${units(r.unitsUsed)} used", style = MaterialTheme.typography.titleMedium)
                Text(kes(r.cost), style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
            }
        }
    }
}

/** Shows the meter photo kept with a reading, fetched with the user's sign-in. */
@Composable
fun PhotoDialog(vm: HavenViewModel, reading: Reading, onDismiss: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(reading.id) {
        vm.loadPhoto(reading.id) { bytes ->
            val decoded = bytes?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
            if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HavenColors.Paper,
        title = { Text("Meter photo, ${units(reading.value)}") },
        text = {
            val img = bitmap
            when {
                img != null -> Image(img, "Photo of the meter", Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                failed -> Text("The photo couldn't be loaded.")
                else -> CircularProgressIndicator(color = HavenColors.Burgundy)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun statementText(t: Tenant, r: Reading) = buildString {
    appendLine("Haven Heights water statement")
    appendLine("Unit ${t.unitNumber}, ${t.name}")
    appendLine("Date: ${friendlyDate(r.createdAt)}")
    appendLine("Previous reading: ${units(r.previousValue)}")
    appendLine("Current reading: ${units(r.value)}")
    appendLine("Units used: ${units(r.unitsUsed)}")
    append("Amount due: ${kes(r.cost)}")
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HavenColors.Brown, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- overview

@Composable
private fun StatTile(label: String, value: Int?, container: Color, content: Color, modifier: Modifier) {
    HavenCard(modifier, container = container) {
        Text(value?.toString() ?: "-", style = MaterialTheme.typography.headlineLarge, color = content)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

@Composable
fun OverviewScreen(vm: HavenViewModel, onOpenTab: (HomeTab) -> Unit, onAddTenant: () -> Unit, onOpenNotices: () -> Unit) {
    val ov by vm.overview.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()
    val urgent = requests.filter { it.status != "RESOLVED" && it.urgency == "HIGH" }.take(3)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val approvals = ov?.awaitingApproval ?: 0
        if (approvals > 0) {
            item {
                HavenCard(onClick = { onOpenTab(HomeTab.Bills) }, container = HavenColors.Burgundy) {
                    Text(
                        if (approvals == 1) "1 payment to approve" else "$approvals payments to approve",
                        style = MaterialTheme.typography.titleLarge, color = HavenColors.Cream,
                    )
                    Text("Tenants say they have paid. Check M-Pesa, then approve or reject.", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Nude)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Open requests", ov?.openRequests, HavenColors.Burgundy, HavenColors.Cream, Modifier.weight(1f))
                StatTile("Tenants", ov?.tenants, HavenColors.Nude, HavenColors.Cocoa, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Vacant units", ov?.vacantUnits, HavenColors.SageMist, HavenColors.SageDeep, Modifier.weight(1f))
                StatTile("Readings this month", ov?.readingsThisMonth, HavenColors.Paper, HavenColors.Cocoa, Modifier.weight(1f))
            }
        }
        item {
            val owed = ov?.pendingBillsTotal ?: 0.0
            val overdue = ov?.overdueBills ?: 0
            HavenCard(onClick = { onOpenTab(HomeTab.Bills) }, container = if (overdue > 0) HavenColors.BurgundyMist else HavenColors.Paper) {
                Text("Unpaid bills", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                Text(kes(owed), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (overdue > 0) "$overdue overdue" else "Nothing overdue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue > 0) HavenColors.Burgundy else HavenColors.SageDeep,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Quick actions")
                SageButton("Record a meter reading", { onOpenTab(HomeTab.Meters) }, Modifier.fillMaxWidth().height(52.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("Register tenant", onAddTenant, Modifier.weight(1f))
                    OutlineButton("Notices", onOpenNotices, Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle("Needs attention") }
        if (urgent.isEmpty()) {
            item { Text("No urgent repairs right now.", color = HavenColors.Brown) }
        } else {
            items(urgent, key = { it.id }) { r ->
                HavenCard(onClick = { onOpenTab(HomeTab.Requests) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Text("Unit ${r.unitNumber}, ${r.tenantName}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                        }
                        UrgencyPill(r.urgency)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tenants & meters

@Composable
fun TenantsScreen(vm: HavenViewModel, onOpen: (Int) -> Unit, onAdd: () -> Unit) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        if (tenants.isEmpty()) {
            EmptyState("No tenants yet", "Register your first tenant to start tracking their water use.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tenants, key = { it.tenancyId }) { t ->
                    HavenCard(onClick = { onOpen(t.tenancyId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UnitBadge(t.unitNumber)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(t.name, style = MaterialTheme.typography.titleMedium)
                                Text(t.phone, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                                Text(t.unitType, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = HavenColors.Burgundy,
            contentColor = HavenColors.Cream,
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text("Register tenant") },
        )
    }
}

@Composable
fun MetersScreen(vm: HavenViewModel, onRecord: (Int) -> Unit) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    val queued by vm.pending.collectAsStateWithLifecycle()
    val readings = queued.filter { it.kind == KIND_READING }
    if (tenants.isEmpty() && readings.isEmpty()) {
        EmptyState("No meters to read", "Once you register a tenant, their unit appears here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (readings.isNotEmpty()) {
            item { SectionTitle("Saved on this phone") }
            items(readings, key = { "q${it.id}" }) { item ->
                val p = HavenJson.gson.fromJson(item.payload, PendingReading::class.java)
                HavenCard(container = if (item.error != null) HavenColors.BurgundyMist else HavenColors.SageMist) {
                    Text("Unit ${p.unitNumber}: ${units(p.value)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.error ?: "Waiting for a connection. It will upload by itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.error != null) HavenColors.Burgundy else HavenColors.SageDeep,
                    )
                    if (item.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.errorCode == 409) SageButton("Save anyway", { vm.retryPending(item, true) })
                            OutlineButton("Discard", { vm.discardPending(item) })
                        }
                    }
                }
            }
            item { SectionTitle("All meters") }
        }
        items(tenants, key = { it.tenancyId }) { t ->
            HavenCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitBadge(t.unitNumber)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text("Last reading ${units(t.latestReading)}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                    }
                    SageButton("Record", { onRecord(t.tenancyId) })
                }
            }
        }
    }
}

@Composable
fun TenantDetailScreen(vm: HavenViewModel, id: Int, nav: NavController) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    val readings by vm.readings.collectAsStateWithLifecycle()
    val tenant = tenants.firstOrNull { it.tenancyId == id }
    val history = readings[id].orEmpty()
    var confirmMoveOut by remember { mutableStateOf(false) }
    var photoFor by remember { mutableStateOf<Reading?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bills by vm.bills.collectAsStateWithLifecycle()
    val opener = rememberAgreementOpener(vm)

    LaunchedEffect(id) { vm.loadReadings(id); vm.loadBills() }

    Scaffold(
        containerColor = HavenColors.Cream,
        topBar = { HavenTopBar(tenant?.let { "Unit ${it.unitNumber}" } ?: "Tenant", onBack = { nav.popBackStack() }) },
    ) { pad ->
        if (tenant == null) {
            EmptyState("Tenant not found", "They may have moved out.", Modifier.padding(pad))
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    HavenCard {
                        Text(tenant.name, style = MaterialTheme.typography.headlineSmall)
                        Text(tenant.phone, style = MaterialTheme.typography.bodyLarge, color = HavenColors.Brown)
                        Spacer(Modifier.height(10.dp))
                        DetailRow("Unit type", tenant.unitType)
                        DetailRow("Moved in", friendlyDate(tenant.moveInDate))
                        DetailRow("Starting reading", units(tenant.initialMeterReading))
                        DetailRow("Latest reading", units(tenant.latestReading))
                    }
                }
                item { SageButton("Record a reading", { nav.navigate(Routes.reading(id)) }, Modifier.fillMaxWidth().height(52.dp)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LeaseCard(tenant, opener)
                        OutlineButton("Edit lease and agreement", { nav.navigate(Routes.lease(id)) }, Modifier.fillMaxWidth())
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Bills", Modifier.weight(1f))
                        OutlineButton("Add bill", { nav.navigate(Routes.newBill(id)) })
                    }
                }
                val tenantBills = bills.filter { it.tenancyId == id }.take(4)
                if (tenantBills.isEmpty()) item { Text("No bills yet.", color = HavenColors.Brown) }
                items(tenantBills, key = { "bill${it.id}" }) { BillCard(it, showTenant = false) }
                item { SectionTitle("Reading history") }
                if (history.isEmpty()) {
                    item { Text("No readings yet. Record the first one the next time you check the meter.", color = HavenColors.Brown) }
                }
                items(history, key = { it.id }) { ReadingRow(it, onPhoto = { photoFor = it }) }
                if (history.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineButton("Share statement", {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, statementText(tenant, history.first()))
                                }
                                context.startActivity(Intent.createChooser(send, "Share water statement"))
                            })
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Delete latest reading", color = HavenColors.Burgundy)
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { confirmMoveOut = true }) {
                        Text("Mark as moved out", color = HavenColors.Burgundy)
                    }
                }
            }
        }
    }

    photoFor?.let { PhotoDialog(vm, it) { photoFor = null } }

    if (confirmDelete && history.isNotEmpty()) {
        val latest = history.first()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = HavenColors.Paper,
            title = { Text("Delete the reading ${units(latest.value)}?") },
            text = { Text("Use this if you entered it wrongly. The previous reading becomes the latest again, and you can record it afresh.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteReading(latest.id, id) }) {
                    Text("Delete reading", color = HavenColors.Burgundy)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (confirmMoveOut && tenant != null) {
        AlertDialog(
            onDismissRequest = { confirmMoveOut = false },
            containerColor = HavenColors.Paper,
            title = { Text("Mark ${tenant.name} as moved out?") },
            text = { Text("Unit ${tenant.unitNumber} becomes vacant and ${tenant.name.substringBefore(' ')} can no longer sign in. Their reading history is kept.") },
            confirmButton = {
                TextButton(onClick = { confirmMoveOut = false; vm.moveOut(id) { nav.popBackStack() } }) {
                    Text("Mark as moved out", color = HavenColors.Burgundy)
                }
            },
            dismissButton = { TextButton(onClick = { confirmMoveOut = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- register tenant

@Composable
fun AddTenantScreen(vm: HavenViewModel, nav: NavController) {
    val unitTypes = listOf("Bedsitter", "Single room", "One bedroom")
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var unitType by rememberSaveable { mutableStateOf(unitTypes.first()) }
    var moveInDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var startReading by rememberSaveable { mutableStateOf("") }
    var rent by rememberSaveable { mutableStateOf("") }
    var deposit by rememberSaveable { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // [name, unit, phone, password]: kept in saveable state so the password survives rotation.
    var created by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }

    val moveIn = LocalDate.ofEpochDay(moveInDay)
    val valid = name.isNotBlank() && phone.filter { it.isDigit() }.length >= 9 &&
        unit.isNotBlank() && startReading.toDoubleOrNull() != null

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Register tenant", onBack = { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HavenField("Full name", name, { name = it; error = null })
            HavenField("Phone number", phone, { phone = it; error = null }, keyboard = KeyboardType.Phone,
                hint = "They sign in with this number")
            HavenField("Unit number", unit, { unit = it; error = null }, hint = "For example B4")
            Text("Unit type", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unitTypes.forEach { t ->
                    FilterChip(selected = unitType == t, onClick = { unitType = t }, label = { Text(t) }, colors = havenChipColors())
                }
            }
            OutlineButton("Move-in date: ${displayDate(moveIn)}", { showPicker = true }, Modifier.fillMaxWidth())
            HavenField("Starting meter reading", startReading, { startReading = it.filter { c -> c.isDigit() || c == '.' }; error = null },
                keyboard = KeyboardType.Decimal, hint = "Read the meter on move-in day")
            HavenField("Monthly rent (KES)", rent, { rent = it.filter { c -> c.isDigit() || c == '.' } },
                keyboard = KeyboardType.Decimal, hint = "Optional. You can add lease details later.")
            HavenField("Deposit (KES)", deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Register tenant", enabled = valid, loading = busy, onClick = {
                vm.registerTenant(
                    TenantCreate(name.trim(), phone.trim(), unit.trim(), unitType, moveIn.toString(), startReading.toDouble(),
                        rent.toDoubleOrNull() ?: 0.0, deposit.toDoubleOrNull() ?: 0.0),
                    onError = { error = it },
                ) { c -> created = arrayListOf(c.tenant.name, c.tenant.unitNumber, c.tenant.phone, c.temporaryPassword) }
            })
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = moveIn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { moveInDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    created?.let { c ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = HavenColors.Paper,
            title = { Text("Tenant registered") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${c[0]} is now in unit ${c[1]}. Give them these sign-in details. The password is only shown once.")
                    Spacer(Modifier.height(4.dp))
                    Text("Phone: ${c[2]}", fontWeight = FontWeight.SemiBold)
                    Text("Password: ${c[3]}", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { TextButton(onClick = { nav.popBackStack() }) { Text("Done") } },
        )
    }
}

// ---------------------------------------------------------------- record reading

@Composable
fun RecordReadingScreen(vm: HavenViewModel, id: Int, nav: NavController) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    val tenant = tenants.firstOrNull { it.tenancyId == id }
    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Record reading", onBack = { nav.popBackStack() }) }) { pad ->
        if (tenant == null) EmptyState("Tenant not found", "Go back and pick a unit again.", Modifier.padding(pad))
        else RecordReadingForm(vm, tenant, nav, Modifier.padding(pad))
    }
}

@Composable
private fun RecordReadingForm(vm: HavenViewModel, tenant: Tenant, nav: NavController, modifier: Modifier) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var value by rememberSaveable { mutableStateOf("") }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var jumpMessage by remember { mutableStateOf<String?>(null) }
    var takenAt by rememberSaveable { mutableLongStateOf(-1L) }   // -1 = reading is for today
    var showDatePicker by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var readingSource by rememberSaveable { mutableStateOf("MANUAL") }
    var scanInfo by remember { mutableStateOf<String?>(null) }

    // Photos taken earlier can be picked from the gallery; their date is used when the phone recorded one.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri) { picked ->
            if (picked != null) {
                photoPath = picked.file.path
                readingSource = "MANUAL"
                scanInfo = null
                val millis = picked.takenAtMillis
                if (millis != null && Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now())) {
                    takenAt = millis
                    vm.toast("Using the date the photo was taken. You can change it below.")
                }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            photoPath = pendingPath
            readingSource = "MANUAL"
            scanInfo = null
        }
    }
    val thumbnail = remember(photoPath) {
        photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { f ->
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
        }
    }

    val last = tenant.latestReading
    val parsed = value.toDoubleOrNull()
    val tooLow = parsed != null && parsed < last

    fun submit(confirm: Boolean) {
        val v = parsed ?: return
        val recordedAt = if (takenAt > 0)
            Instant.ofEpochMilli(minOf(takenAt, System.currentTimeMillis())).truncatedTo(ChronoUnit.SECONDS).toString() else null
        vm.saveReading(tenant, v, photoPath?.let { File(it) }, confirm, recordedAt, readingSource) { result ->
            when (result) {
                is SaveResult.Saved -> { vm.toast("Reading saved for unit ${tenant.unitNumber}"); nav.popBackStack() }
                is SaveResult.Queued -> {
                    vm.toast("Saved on this phone. It uploads when you're back online.")
                    nav.popBackStack()
                }
                is SaveResult.NeedsConfirm -> jumpMessage = result.message
                is SaveResult.Failed -> error = result.message
            }
        }
    }

    fun takePhoto() {
        try {
            val dir = File(context.cacheDir, "meter").apply { mkdirs() }
            val file = File(dir, "meter_${System.currentTimeMillis()}.jpg")
            pendingPath = file.path
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            vm.toast("Couldn't open the camera on this phone.")
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HavenCard(container = HavenColors.Nude) {
            Text("Unit ${tenant.unitNumber}", style = MaterialTheme.typography.titleLarge)
            Text(tenant.name, color = HavenColors.Brown)
            Spacer(Modifier.height(10.dp))
            Text("Last reading", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
            Text(units(last), style = MaterialTheme.typography.headlineMedium)
        }

        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "Photo of the meter",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(if (thumbnail == null) "Take photo" else "Retake", { takePhoto() }, Modifier.weight(1f))
            OutlineButton("From gallery", {
                gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, Modifier.weight(1f))
        }
        OutlineButton(
            if (scanning) "Reading meter photo…" else "Auto-read digits",
            onClick = {
                val image = photoPath?.let(::File)
                if (image == null || !image.isFile) {
                    error = "Take or select a meter photo first."
                } else {
                    scanning = true
                    error = null
                    scanInfo = null
                    vm.scanMeter(image) { result ->
                        scanning = false
                        result.onSuccess { suggestion ->
                            value = String.format(java.util.Locale.US, "%.3f", suggestion.value)
                            readingSource = "AI"
                            scanInfo = "Suggested ${suggestion.digits.take(5)}.${suggestion.digits.drop(5)} · ${(suggestion.confidence * 100).toInt()}% average confidence. Check the digits before saving."
                            error = null
                        }.onFailure { e ->
                            error = e.message ?: "Could not read this meter photo."
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = photoPath != null && !scanning && !busy,
        )
        scanInfo?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HavenColors.SageDeep)
        }
        Text(
            "Type the digits shown on the meter. Photos you took earlier can be added from the gallery. This works without signal too; it uploads later.",
            style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(
                if (takenAt < 0) "Reading date: today"
                else "Reading date: ${displayDate(Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate())}",
                { showDatePicker = true }, Modifier.weight(1f),
            )
            if (takenAt >= 0) TextButton(onClick = { takenAt = -1L }) { Text("Today") }
        }
        HavenField(
            "Meter reading", value,
            { value = it.filter { c -> c.isDigit() || c == '.' }; error = null },
            keyboard = KeyboardType.Decimal,
            error = if (tooLow) "Lower than the last reading (${units(last)})" else error,
            hint = if (parsed != null && !tooLow) "${units(parsed - last)} units used since the last reading" else null,
        )
        PrimaryButton("Save reading", { submit(false) }, enabled = parsed != null && !tooLow, loading = busy)
    }

    if (showDatePicker) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val shown = if (takenAt > 0) Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = shown.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc   // not in the future
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        takenAt = if (!day.isBefore(LocalDate.now())) -1L
                        else day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    jumpMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { jumpMessage = null },
            containerColor = HavenColors.Paper,
            title = { Text("Check this reading") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { jumpMessage = null; submit(true) }) { Text("Save anyway", color = HavenColors.Burgundy) } },
            dismissButton = { TextButton(onClick = { jumpMessage = null }) { Text("Re-enter") } },
        )
    }
}
