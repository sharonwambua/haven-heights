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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.data.*
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors

// ---------------------------------------------------------------- maintenance requests

private fun statusRank(s: String) = when (s) { "OPEN" -> 0; "IN_PROGRESS" -> 1; else -> 2 }
private fun urgencyRank(u: String) = when (u) { "HIGH" -> 0; "MEDIUM" -> 1; else -> 2 }

@Composable
fun RequestsScreen(vm: HavenViewModel, isLandlord: Boolean, onNew: () -> Unit) {
    val requests by vm.requests.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<MaintenanceRequest?>(null) }

    val shown = requests
        .filter { filter == "ALL" || it.status == filter }
        .sortedWith(compareBy<MaintenanceRequest>({ statusRank(it.status) }, { urgencyRank(it.urgency) }).thenByDescending { it.createdAt })

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL" to "All", "OPEN" to "Open", "IN_PROGRESS" to "In progress", "RESOLVED" to "Resolved").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) }, colors = havenChipColors())
            }
        }
        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                EmptyState(
                    if (requests.isEmpty()) "No repair requests" else "Nothing in this view",
                    if (isLandlord) "Requests from your tenants will show up here."
                    else "Something broken at home? Report it and your landlord will see it straight away.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { r ->
                        HavenCard(onClick = if (isLandlord) ({ editing = r }) else null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UrgencyPill(r.urgency)
                                StatusPill(r.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            if (isLandlord) {
                                Text("Unit ${r.unitNumber}, ${r.tenantName}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(r.description, style = MaterialTheme.typography.bodyMedium)
                            r.landlordNote?.let { note ->
                                Spacer(Modifier.height(10.dp))
                                Column(Modifier.fillMaxWidth().background(HavenColors.SageMist, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                    Text(if (isLandlord) "Your note" else "Update from your landlord", style = MaterialTheme.typography.labelMedium, color = HavenColors.SageDeep)
                                    Text(note, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Cocoa)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Reported ${friendlyDate(r.createdAt)}", style = MaterialTheme.typography.labelMedium, color = HavenColors.Brown)
                        }
                    }
                }
            }
            if (!isLandlord) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = HavenColors.Burgundy,
                    contentColor = HavenColors.Cream,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Report an issue") },
                )
            }
        }
    }

    editing?.let { req ->
        var status by remember(req.id) { mutableStateOf(req.status) }
        var note by remember(req.id) { mutableStateOf(req.landlordNote.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = HavenColors.Paper,
            title = { Text("Update request") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(req.title, style = MaterialTheme.typography.titleMedium)
                        Text("Unit ${req.unitNumber}, ${req.tenantName}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("OPEN", "IN_PROGRESS", "RESOLVED").forEach { s ->
                            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(statusLabel(s)) }, colors = havenChipColors())
                        }
                    }
                    HavenField("Note for the tenant", note, { note = it }, singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.updateRequest(req.id, status, note); editing = null }) {
                    Text("Save update", color = HavenColors.Burgundy)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NewRequestScreen(vm: HavenViewModel, nav: NavController) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var urgency by rememberSaveable { mutableStateOf("MEDIUM") }
    var error by remember { mutableStateOf<String?>(null) }
    val hints = mapOf(
        "LOW" to "Can wait a week or more.",
        "MEDIUM" to "Needs fixing in the next few days.",
        "HIGH" to "A leak, no power, or a security risk.",
    )

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Report an issue", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HavenField("What's wrong?", title, { title = it; error = null }, hint = "For example: Kitchen tap is leaking")
            HavenField("Details", description, { description = it; error = null }, singleLine = false, minLines = 4,
                hint = "Where it is and when it started")
            Text("How urgent is it?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOW", "MEDIUM", "HIGH").forEach { u ->
                    FilterChip(selected = urgency == u, onClick = { urgency = u }, label = { Text(urgencyLabel(u)) }, colors = havenChipColors())
                }
            }
            Text(hints.getValue(urgency), style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                "Send request",
                onClick = { vm.createRequest(NewRequest(title.trim(), description.trim(), urgency), { error = it }) { nav.popBackStack() } },
                enabled = title.trim().length >= 3 && description.trim().length >= 3,
                loading = busy,
            )
        }
    }
}

// ---------------------------------------------------------------- announcements

@Composable
fun NoticesScreen(vm: HavenViewModel, isLandlord: Boolean, onNew: () -> Unit) {
    val notices by vm.announcements.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Announcement?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (notices.isEmpty()) {
            EmptyState(
                "No notices yet",
                if (isLandlord) "Post water outages, repair schedules or garbage days and every tenant sees them here."
                else "Updates from your landlord will appear here.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notices, key = { it.id }) { n ->
                    val (container, content) = categoryColors(n.category)
                    HavenCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(categoryLabel(n.category), container, content)
                            Spacer(Modifier.weight(1f))
                            if (isLandlord) {
                                IconButton(onClick = { toDelete = n }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete notice", tint = HavenColors.Brown)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(n.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(n.body, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(friendlyDate(n.createdAt), style = MaterialTheme.typography.labelMedium, color = HavenColors.Brown)
                    }
                }
            }
        }
        if (isLandlord) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = HavenColors.Burgundy,
                contentColor = HavenColors.Cream,
                icon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                text = { Text("Post notice") },
            )
        }
    }

    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = HavenColors.Paper,
            title = { Text("Delete this notice?") },
            text = { Text("\"${n.title}\" will disappear from every tenant's feed.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAnnouncement(n.id); toDelete = null }) { Text("Delete notice", color = HavenColors.Burgundy) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NewNoticeScreen(vm: HavenViewModel, nav: NavController) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("GENERAL") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Post notice", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Category", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryLabel(c)) }, colors = havenChipColors())
                }
            }
            HavenField("Title", title, { title = it; error = null }, hint = "For example: Water off on Friday")
            HavenField("Message", body, { body = it; error = null }, singleLine = false, minLines = 5,
                hint = "Include the date, time and what tenants should do")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                "Post notice",
                onClick = { vm.postAnnouncement(NewAnnouncement(title.trim(), body.trim(), category), { error = it }) { nav.popBackStack() } },
                enabled = title.trim().length >= 3 && body.trim().length >= 3,
                loading = busy,
            )
        }
    }
}
