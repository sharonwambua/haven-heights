@file:OptIn(ExperimentalMaterial3Api::class)

package com.havenheights.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.data.Bill
import com.havenheights.app.data.NewBill
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun kindLabel(kind: String) = when (kind) { "RENT" -> "Rent"; "WATER" -> "Water"; else -> "Other" }

@Composable
fun BillCard(b: Bill, showTenant: Boolean, onClick: (() -> Unit)? = null) {
    HavenCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(kindLabel(b.kind), HavenColors.Nude, HavenColors.Cocoa)
            when {
                b.status == "PAID" -> Pill("Paid", HavenColors.SageMist, HavenColors.SageDeep)
                b.status == "SUBMITTED" -> Pill("Awaiting approval", HavenColors.Sage, HavenColors.Cocoa)
                b.overdue -> Pill("Overdue", HavenColors.Burgundy, HavenColors.Cream)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.description, style = MaterialTheme.typography.titleMedium)
                if (showTenant) Text("Unit ${b.unitNumber}, ${b.tenantName}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                Text(
                    when (b.status) {
                        "PAID" -> "Paid ${b.paidAt?.let { friendlyDate(it) } ?: ""}"
                        "SUBMITTED" -> "Payment sent, code ${b.paymentReference ?: ""}"
                        else -> "Due ${friendlyDate(b.dueDate)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (b.overdue) HavenColors.Burgundy else HavenColors.Brown,
                )
            }
            Text(kes(b.amount), style = MaterialTheme.typography.titleLarge)
        }
        if (b.status == "PENDING" && !b.reviewNote.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                b.reviewNote, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Burgundy,
                modifier = Modifier.fillMaxWidth().background(HavenColors.BurgundyMist, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        }
    }
}

@Composable
fun BillsScreen(vm: HavenViewModel, isLandlord: Boolean, onNew: () -> Unit) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf("") }
    var acting by remember { mutableStateOf<Bill?>(null) }
    var paying by remember { mutableStateOf<Bill?>(null) }
    var confirmRent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadBills() }   // always show the latest decision

    val toApprove = bills.count { it.status == "SUBMITTED" }
    // The landlord lands on payments waiting for a decision whenever there are any.
    val filter = chosen.ifEmpty { if (isLandlord && toApprove > 0) "SUBMITTED" else if (isLandlord) "PENDING" else "UNPAID" }
    val shown = bills
        .filter {
            when (filter) {
                "ALL" -> true
                "UNPAID" -> it.status != "PAID"
                else -> it.status == filter
            }
        }
        .sortedWith(compareBy<Bill>({ it.status == "PAID" }, { it.dueDate }))
    val owed = bills.filter { it.status == "PENDING" }.sumOf { it.amount }
    val waiting = bills.filter { it.status == "SUBMITTED" }.sumOf { it.amount }
    val chips = if (isLandlord)
        listOf("SUBMITTED" to "To approve ($toApprove)", "PENDING" to "Unpaid", "PAID" to "Paid", "ALL" to "All")
    else listOf("UNPAID" to "Unpaid", "PAID" to "Paid", "ALL" to "All")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (isLandlord) {
                    OutlineButton("Create this month's rent bills", { confirmRent = true }, Modifier.fillMaxWidth())
                } else {
                    HavenCard(container = if (owed > 0) HavenColors.Burgundy else HavenColors.SageMist) {
                        val on = if (owed > 0) HavenColors.Cream else HavenColors.SageDeep
                        val soft = if (owed > 0) HavenColors.Nude else HavenColors.SageDeep
                        Text(
                            when { owed > 0 -> "You owe"; waiting > 0 -> "Waiting for approval"; else -> "You're all paid up" },
                            style = MaterialTheme.typography.bodyMedium, color = soft,
                        )
                        if (owed > 0) Text(kes(owed), style = MaterialTheme.typography.headlineLarge, color = on)
                        if (waiting > 0) Text(
                            "${kes(waiting)} sent, waiting for your landlord to approve",
                            style = MaterialTheme.typography.bodyMedium, color = on,
                        )
                    }
                }
            }
            if (!isLandlord && me?.estate?.paymentInfo?.isNotBlank() == true) {
                item {
                    HavenCard(container = HavenColors.Nude) {
                        Text("How to pay", style = MaterialTheme.typography.titleMedium)
                        Text(me?.estate?.paymentInfo.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "After paying, tap the bill and enter your M-Pesa code so your landlord can approve it.",
                            style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { chosen = key }, label = { Text(label) }, colors = havenChipColors())
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        when (filter) { "SUBMITTED" -> "Nothing to approve"; "PAID" -> "No paid bills yet"; else -> "Nothing to pay" },
                        if (isLandlord) "When a tenant says they have paid, it appears here for you to approve. Water bills are created when you record a reading."
                        else "New bills from your landlord will show up here.",
                    )
                }
            }
            items(shown, key = { it.id }) { b ->
                BillCard(
                    b, showTenant = isLandlord,
                    onClick = when {
                        isLandlord -> ({ acting = b })
                        b.status == "PENDING" -> ({ paying = b })
                        else -> null
                    },
                )
            }
        }
        if (isLandlord) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = HavenColors.Burgundy,
                contentColor = HavenColors.Cream,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New bill") },
            )
        }
    }

    // Tenant: "I've paid"
    paying?.let { b ->
        var code by remember(b.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paying = null },
            containerColor = HavenColors.Paper,
            title = { Text("I've paid ${kes(b.amount)}") },
            text = {
                DialogBody {
                    Text(b.description, style = MaterialTheme.typography.titleMedium)
                    Text("Enter the confirmation code from your M-Pesa message. Your landlord will check it and approve the payment.", style = MaterialTheme.typography.bodyMedium)
                    HavenField("M-Pesa code", code, { code = it.filter { c -> c.isLetterOrDigit() }.uppercase() }, hint = "For example SGH7K2LMQ9")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.submitPayment(b.id, code); paying = null }, enabled = code.trim().length >= 4) {
                    Text("Send for approval", color = HavenColors.Burgundy)
                }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } },
        )
    }

    // Landlord: approve or reject, or manage the bill
    acting?.let { b ->
        var note by remember(b.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { acting = null },
            containerColor = HavenColors.Paper,
            title = { Text(if (b.status == "SUBMITTED") "Approve this payment?" else "${b.description}, ${kes(b.amount)}") },
            text = {
                DialogBody {
                    Text("Unit ${b.unitNumber}, ${b.tenantName}", style = MaterialTheme.typography.titleMedium)
                    Text("${b.description}, ${kes(b.amount)}. Due ${friendlyDate(b.dueDate)}.", style = MaterialTheme.typography.bodyMedium)
                    if (b.status == "SUBMITTED") {
                        Text(
                            "The tenant says they paid. M-Pesa code: ${b.paymentReference}. Check your M-Pesa messages before you approve.",
                            style = MaterialTheme.typography.bodyMedium, color = HavenColors.SageDeep,
                        )
                        HavenField("Reason, if not received", note, { note = it }, singleLine = false, minLines = 2, hint = "Optional. The tenant will read this.")
                    }
                }
            },
            confirmButton = {
                when (b.status) {
                    "SUBMITTED" -> TextButton(onClick = { vm.reviewBill(b.id, true, null); acting = null }) { Text("Approve", color = HavenColors.SageDeep) }
                    "PENDING" -> TextButton(onClick = { vm.setBillStatus(b.id, "PAID"); acting = null }) { Text("Mark as paid (cash)", color = HavenColors.SageDeep) }
                    else -> TextButton(onClick = { vm.setBillStatus(b.id, "PENDING"); acting = null }) { Text("Mark as unpaid", color = HavenColors.Burgundy) }
                }
            },
            dismissButton = {
                Row {
                    if (b.status == "SUBMITTED") TextButton(onClick = { vm.reviewBill(b.id, false, note); acting = null }) { Text("Not received", color = HavenColors.Burgundy) }
                    if (b.status == "PENDING") TextButton(onClick = { vm.deleteBill(b.id); acting = null }) { Text("Delete", color = HavenColors.Burgundy) }
                    TextButton(onClick = { acting = null }) { Text("Close") }
                }
            },
        )
    }

    if (confirmRent) {
        AlertDialog(
            onDismissRequest = { confirmRent = false },
            containerColor = HavenColors.Paper,
            title = { Text("Create this month's rent bills?") },
            text = { DialogBody { Text("Every tenant with a monthly rent gets a bill. Tenants who already have one for this month are skipped.") } },
            confirmButton = { TextButton(onClick = { confirmRent = false; vm.generateRent() }) { Text("Create bills", color = HavenColors.Burgundy) } },
            dismissButton = { TextButton(onClick = { confirmRent = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NewBillScreen(vm: HavenViewModel, nav: NavController, presetTenancy: Int) {
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tenancyId by rememberSaveable { mutableIntStateOf(presetTenancy) }
    var kind by rememberSaveable { mutableStateOf("OTHER") }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(7).toEpochDay()) }
    var menu by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val chosen = tenants.firstOrNull { it.tenancyId == tenancyId }
    val due = LocalDate.ofEpochDay(dueDay)
    val value = amount.toDoubleOrNull()

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("New bill", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tenant", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlineButton(chosen?.let { "Unit ${it.unitNumber}, ${it.name}" } ?: "Choose a tenant", { menu = true }, Modifier.fillMaxWidth())
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    tenants.forEach { t ->
                        DropdownMenuItem(text = { Text("Unit ${t.unitNumber}, ${t.name}") }, onClick = { tenancyId = t.tenancyId; menu = false })
                    }
                }
            }
            Text("Type", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RENT", "WATER", "OTHER").forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }, colors = havenChipColors())
                }
            }
            HavenField("What is it for?", description, { description = it; error = null }, hint = "For example: Rent, December 2026")
            HavenField("Amount (KES)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = null }, keyboard = KeyboardType.Decimal)
            OutlineButton("Due date: ${displayDate(due)}", { showPicker = true }, Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                "Send bill",
                onClick = {
                    vm.createBill(NewBill(tenancyId, kind, description.trim(), value ?: 0.0, due.toString()), { error = it }) { nav.popBackStack() }
                },
                enabled = chosen != null && description.trim().length >= 3 && (value ?: 0.0) > 0,
                loading = busy,
            )
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
