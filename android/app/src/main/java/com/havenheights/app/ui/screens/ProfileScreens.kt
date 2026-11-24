@file:OptIn(ExperimentalMaterial3Api::class)

package com.havenheights.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.data.LeaseUpdate
import com.havenheights.app.data.Tenant
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- shared helpers

fun ordinal(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

fun dial(context: Context, phone: String) {
    try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (_: ActivityNotFoundException) {}
}

/** Returns a function that downloads a tenant's lease agreement and opens it in the phone's viewer. */
@Composable
fun rememberAgreementOpener(vm: HavenViewModel): (Tenant) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { t: Tenant ->
            vm.loadLeaseFile(t.tenancyId, t.leaseFileName) { file: File? ->
                if (file != null) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val mime = if (file.extension == "pdf") "application/pdf" else "image/*"
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try { context.startActivity(view) } catch (_: ActivityNotFoundException) {
                        vm.toast("Install a PDF viewer app to open this agreement.")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HavenColors.Brown, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun LeaseCard(tenant: Tenant, onViewAgreement: ((Tenant) -> Unit)?) {
    val end = tenant.leaseEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val daysLeft = end?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    HavenCard {
        Text("Lease agreement", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        InfoRow("Unit", "${tenant.unitNumber}, ${tenant.unitType}")
        InfoRow("Monthly rent", if (tenant.monthlyRent > 0) kes(tenant.monthlyRent) else "Not set")
        InfoRow("Deposit", if (tenant.deposit > 0) kes(tenant.deposit) else "None")
        InfoRow("Rent due", "${ordinal(tenant.rentDueDay)} of each month")
        InfoRow("Lease started", friendlyDate(tenant.moveInDate))
        InfoRow("Lease ends", end?.let { displayDate(it) } ?: "Open-ended")
        if (daysLeft != null) {
            Spacer(Modifier.height(6.dp))
            when {
                daysLeft < 0 -> Pill("Lease ended", HavenColors.BurgundyMist, HavenColors.Burgundy)
                daysLeft <= 60 -> Pill("Ends in $daysLeft days", HavenColors.Nude, HavenColors.Cocoa)
            }
        }
        if (!tenant.leaseTerms.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Key terms", style = MaterialTheme.typography.titleMedium)
            Text(tenant.leaseTerms, style = MaterialTheme.typography.bodyMedium)
        }
        if (tenant.hasLeaseFile && onViewAgreement != null) {
            Spacer(Modifier.height(12.dp))
            SageButton("View signed agreement", { onViewAgreement(tenant) }, Modifier.fillMaxWidth())
            tenant.leaseFileName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = HavenColors.Brown, modifier = Modifier.padding(top = 4.dp)) }
        } else if (!tenant.hasLeaseFile) {
            Spacer(Modifier.height(8.dp))
            Text("No signed agreement has been attached yet.", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
fun ProfileScreen(vm: HavenViewModel, nav: NavController) {
    val me by vm.me.collectAsStateWithLifecycle()
    val opener = rememberAgreementOpener(vm)
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadMe() }

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("My profile", onBack = { nav.popBackStack() }) }) { pad ->
        val m = me
        if (m == null) {
            EmptyState("Loading your profile", "If this takes long, check your connection.", Modifier.padding(pad))
            return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initials = m.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
                Box(Modifier.size(64.dp).background(HavenColors.Burgundy, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initials, color = HavenColors.Cream, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(m.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    if (m.role == "LANDLORD") Pill("Landlord", HavenColors.BurgundyMist, HavenColors.Burgundy)
                    else Pill("Tenant, unit ${m.tenant?.unitNumber ?: ""}", HavenColors.SageMist, HavenColors.SageDeep)
                }
            }

            DetailsCard(m.name, m.phone, m.email, m.emergencyContact, showEmergency = m.role == "TENANT", vm = vm)

            if (m.role == "TENANT") {
                m.tenant?.let { LeaseCard(it, opener) }
                HavenCard {
                    Text("Your landlord", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Name", m.estate.landlordName)
                    InfoRow("Phone", m.estate.landlordPhone)
                    InfoRow("Estate", m.estate.estateName)
                    if (m.estate.address.isNotBlank()) InfoRow("Address", m.estate.address)
                    Spacer(Modifier.height(10.dp))
                    SageButton("Call landlord", { dial(context, m.estate.landlordPhone) }, Modifier.fillMaxWidth())
                    if (m.estate.paymentInfo.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("How to pay", style = MaterialTheme.typography.titleMedium)
                        Text(m.estate.paymentInfo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                EstateCard(m.estate.estateName, m.estate.address, m.estate.paymentInfo, vm)
            }

            OutlineButton("Change password", { nav.navigate(Routes.PASSWORD) }, Modifier.fillMaxWidth())
            TextButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out", color = HavenColors.Burgundy) }
        }
    }
}

@Composable
private fun DetailsCard(name: String, phone: String, email: String?, emergency: String?, showEmergency: Boolean, vm: HavenViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var n by rememberSaveable(editing) { mutableStateOf(name) }
    var e by rememberSaveable(editing) { mutableStateOf(email.orEmpty()) }
    var c by rememberSaveable(editing) { mutableStateOf(emergency.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    HavenCard {
        Text("Your details", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (!editing) {
            InfoRow("Phone", phone)
            InfoRow("Email", email ?: "Not added")
            if (showEmergency) InfoRow("Emergency contact", emergency ?: "Not added")
            Spacer(Modifier.height(8.dp))
            OutlineButton("Edit details", { editing = true })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HavenField("Full name", n, { n = it; error = null })
                HavenField("Email", e, { e = it; error = null }, keyboard = KeyboardType.Email, hint = "Optional")
                if (showEmergency) HavenField("Emergency contact", c, { c = it }, hint = "A name and phone number, optional")
                Text("Your phone number is your sign-in, so it can't be changed here.", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SageButton("Save", { vm.updateProfile(n, e, c, { error = it }) { editing = false } }, enabled = n.trim().length >= 2 && !busy)
                    OutlineButton("Cancel", { editing = false })
                }
            }
        }
    }
}

@Composable
private fun EstateCard(name: String, address: String, payment: String, vm: HavenViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var n by rememberSaveable(editing) { mutableStateOf(name) }
    var a by rememberSaveable(editing) { mutableStateOf(address) }
    var p by rememberSaveable(editing) { mutableStateOf(payment) }
    var error by remember { mutableStateOf<String?>(null) }

    HavenCard {
        Text("Your estate", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (!editing) {
            InfoRow("Name", name)
            InfoRow("Address", address.ifBlank { "Not added" })
            InfoRow("How tenants pay", payment.ifBlank { "Not added" })
            Spacer(Modifier.height(8.dp))
            OutlineButton("Edit estate details", { editing = true })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HavenField("Estate name", n, { n = it; error = null })
                HavenField("Address", a, { a = it })
                HavenField("How tenants pay", p, { p = it }, singleLine = false, minLines = 3,
                    hint = "Shown to tenants on their bills. For example: M-Pesa Paybill 123456, account = unit number")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SageButton("Save", { vm.updateEstate(n, a, p, { error = it }) { editing = false } }, enabled = n.trim().length >= 2 && !busy)
                    OutlineButton("Cancel", { editing = false })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- edit lease (landlord)

@Composable
fun EditLeaseScreen(vm: HavenViewModel, id: Int, nav: NavController) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    val tenant = tenants.firstOrNull { it.tenancyId == id }
    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Lease", onBack = { nav.popBackStack() }) }) { pad ->
        if (tenant == null) EmptyState("Tenant not found", "Go back and try again.", Modifier.padding(pad))
        else LeaseForm(vm, tenant, nav, Modifier.padding(pad))
    }
}

@Composable
private fun LeaseForm(vm: HavenViewModel, tenant: Tenant, nav: NavController, modifier: Modifier) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var rent by rememberSaveable { mutableStateOf(tenant.monthlyRent.takeIf { it > 0 }?.let { units(it) }.orEmpty()) }
    var deposit by rememberSaveable { mutableStateOf(tenant.deposit.takeIf { it > 0 }?.let { units(it) }.orEmpty()) }
    var dueDay by rememberSaveable { mutableStateOf(tenant.rentDueDay.toString()) }
    var endDay by rememberSaveable { mutableLongStateOf(tenant.leaseEnd?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: -1L) }
    var terms by rememberSaveable { mutableStateOf(tenant.leaseTerms.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.uploadLease(tenant.tenancyId, uri) {}
    }
    val day = dueDay.toIntOrNull()
    val valid = day != null && day in 1..28

    Column(modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Unit ${tenant.unitNumber}, ${tenant.name}", style = MaterialTheme.typography.titleLarge)
        HavenField("Monthly rent (KES)", rent, { rent = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
        HavenField("Deposit (KES)", deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
        HavenField("Rent due on day", dueDay, { dueDay = it.filter { c -> c.isDigit() }.take(2) }, keyboard = KeyboardType.Number,
            error = if (!valid) "Pick a day from 1 to 28" else null, hint = "Day of the month")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(if (endDay < 0) "Lease end date: none" else "Lease ends: ${displayDate(LocalDate.ofEpochDay(endDay))}", { showPicker = true }, Modifier.weight(1f))
            if (endDay >= 0) TextButton(onClick = { endDay = -1L }) { Text("Clear") }
        }
        HavenField("Key terms", terms, { terms = it }, singleLine = false, minLines = 4,
            hint = "For example: 12 months, one month notice, no pets. Tenants see this in the app.")

        HavenCard(container = HavenColors.Nude) {
            Text("Signed agreement", style = MaterialTheme.typography.titleMedium)
            Text(
                if (tenant.hasLeaseFile) "Attached: ${tenant.leaseFileName}" else "Attach a scan or photo so the tenant can open it in the app.",
                style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown,
            )
            Spacer(Modifier.height(8.dp))
            SageButton(if (tenant.hasLeaseFile) "Replace agreement" else "Attach PDF or photo",
                { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) })
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton("Save lease", enabled = valid, loading = busy, onClick = {
            vm.updateLease(
                tenant.tenancyId,
                LeaseUpdate(rent.toDoubleOrNull() ?: 0.0, deposit.toDoubleOrNull() ?: 0.0, day ?: 5,
                    if (endDay >= 0) LocalDate.ofEpochDay(endDay).toString() else null, terms.trim().ifBlank { null }),
                { error = it },
            ) { nav.popBackStack() }
        })
    }

    if (showPicker) {
        val initial = (if (endDay >= 0) LocalDate.ofEpochDay(endDay) else LocalDate.now().plusYears(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

// ---------------------------------------------------------------- notification inbox + landlord notices route

@Composable
fun NotificationsScreen(vm: HavenViewModel, nav: NavController) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadInbox() }
    // Items stay highlighted while you read; they are marked read when you leave.
    DisposableEffect(Unit) { onDispose { vm.markInboxRead() } }

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Notifications", onBack = { nav.popBackStack() }) }) { pad ->
        val list = inbox?.items.orEmpty()
        if (list.isEmpty()) {
            EmptyState("Nothing yet", "Bills, notices, repair updates and water readings show up here.", Modifier.padding(pad))
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list.size) { i ->
                    val n = list[i]
                    HavenCard(container = if (n.read) HavenColors.Paper else HavenColors.BurgundyMist) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!n.read) Box(Modifier.size(8.dp).background(HavenColors.Burgundy, CircleShape))
                            Pill(
                                when (n.kind) { "bill" -> "Bill"; "notice" -> "Notice"; "reading" -> "Water"; "request" -> "Repair"; else -> "Update" },
                                HavenColors.Nude, HavenColors.Cocoa,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(friendlyDate(n.createdAt), style = MaterialTheme.typography.labelMedium, color = HavenColors.Brown)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(n.title, style = MaterialTheme.typography.titleMedium)
                        Text(n.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun NoticesRouteScreen(vm: HavenViewModel, nav: NavController) {
    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Notices", onBack = { nav.popBackStack() }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { NoticesScreen(vm, true) { nav.navigate(Routes.NEW_NOTICE) } }
    }
}
