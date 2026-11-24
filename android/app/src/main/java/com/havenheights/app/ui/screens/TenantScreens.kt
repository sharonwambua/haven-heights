package com.havenheights.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havenheights.app.data.Reading
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors

/** The tenant's home: what they owe, their lease, latest news and who to call. */
@Composable
fun TenantDashboardScreen(vm: HavenViewModel, onOpenTab: (HomeTab) -> Unit, onOpenProfile: () -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val opener = rememberAgreementOpener(vm)
    val context = LocalContext.current
    val d = dashboard
    if (d == null) {
        EmptyState("Loading your home", "If this takes long, check your connection and tap refresh.")
        return
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // What do I owe?
        item {
            HavenCard(container = if (d.pendingTotal > 0) HavenColors.Burgundy else HavenColors.SageMist) {
                val onColor = if (d.pendingTotal > 0) HavenColors.Cream else HavenColors.SageDeep
                val soft = if (d.pendingTotal > 0) HavenColors.Nude else HavenColors.SageDeep
                Text("${d.estate.estateName}, unit ${d.tenant.unitNumber}", style = MaterialTheme.typography.bodyMedium, color = soft)
                Spacer(Modifier.height(10.dp))
                if (d.pendingTotal > 0) {
                    Text("Amount due", style = MaterialTheme.typography.bodyMedium, color = soft)
                    Text(kes(d.pendingTotal), style = MaterialTheme.typography.headlineLarge, color = onColor)
                    if (d.overdueTotal > 0) {
                        Spacer(Modifier.height(8.dp))
                        Pill("${kes(d.overdueTotal)} overdue", HavenColors.Cream, HavenColors.Burgundy)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpenTab(HomeTab.Bills) },
                        colors = ButtonDefaults.buttonColors(containerColor = HavenColors.Cream, contentColor = HavenColors.Burgundy),
                    ) { Text("See bills and how to pay") }
                } else {
                    Text("You're all paid up", style = MaterialTheme.typography.headlineSmall, color = onColor)
                    Text("Nothing is due right now.", style = MaterialTheme.typography.bodyMedium, color = soft)
                }
            }
        }

        if (d.awaitingTotal > 0) {
            item {
                HavenCard(onClick = { onOpenTab(HomeTab.Bills) }, container = HavenColors.Sage) {
                    Text("${kes(d.awaitingTotal)} waiting for approval", style = MaterialTheme.typography.titleMedium, color = HavenColors.Cocoa)
                    Text("Your landlord will confirm your payment. You'll be notified.", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Cocoa)
                }
            }
        }

        if (d.pendingBills.isNotEmpty()) {
            item { SectionTitle("Bills") }
            items(d.pendingBills.take(3), key = { "b${it.id}" }) { BillCard(it, showTenant = false, onClick = { onOpenTab(HomeTab.Bills) }) }
        }

        item {
            HavenCard(onClick = { onOpenTab(HomeTab.Usage) }, container = HavenColors.SageMist) {
                Text("Water this month", style = MaterialTheme.typography.bodyMedium, color = HavenColors.SageDeep)
                Text(
                    if (d.waterMonthUnits > 0) "${units(d.waterMonthUnits)} units, ${kes(d.waterMonthCost)}" else "No reading yet this month",
                    style = MaterialTheme.typography.titleLarge, color = HavenColors.SageDeep,
                )
                Text("Latest meter reading ${units(d.tenant.latestReading)}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.SageDeep)
            }
        }

        item { LeaseCard(d.tenant, opener) }

        item { SectionTitle("Latest notices") }
        if (d.latestNotices.isEmpty()) item { Text("No notices yet.", color = HavenColors.Brown) }
        items(d.latestNotices.take(2), key = { "n${it.id}" }) { n ->
            val (container, content) = categoryColors(n.category)
            HavenCard(onClick = { onOpenTab(HomeTab.Notices) }) {
                Pill(categoryLabel(n.category), container, content)
                Spacer(Modifier.height(6.dp))
                Text(n.title, style = MaterialTheme.typography.titleMedium)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(friendlyDate(n.createdAt), style = MaterialTheme.typography.labelMedium, color = HavenColors.Brown, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            HavenCard(onClick = { onOpenTab(HomeTab.Requests) }) {
                Text("Repair requests", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (d.openRequests) { 0 -> "None open. Tap to report an issue."; 1 -> "1 request is being handled."; else -> "${d.openRequests} requests are being handled." },
                    style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown,
                )
            }
        }

        item {
            HavenCard {
                Text("Need to reach your landlord?", style = MaterialTheme.typography.titleMedium)
                Text("${d.estate.landlordName}, ${d.estate.landlordPhone}", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SageButton("Call", { dial(context, d.estate.landlordPhone) })
                    OutlineButton("My profile", onOpenProfile)
                }
            }
        }
    }
}

@Composable
fun UsageScreen(vm: HavenViewModel) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    val u = usage
    var photoFor by remember { mutableStateOf<Reading?>(null) }
    if (u == null) {
        EmptyState("No readings yet", "Your landlord records your water meter here. Check back after the next reading.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HavenCard(container = HavenColors.Burgundy) {
                Text("Unit ${u.unitNumber}, this month", style = MaterialTheme.typography.bodyMedium, color = HavenColors.Nude)
                Spacer(Modifier.height(4.dp))
                Text(kes(u.monthCost), style = MaterialTheme.typography.headlineLarge, color = HavenColors.Cream)
                Text(
                    "${units(u.monthUnits)} units at ${kes(u.ratePerUnit)} a unit",
                    style = MaterialTheme.typography.bodyMedium, color = HavenColors.Nude,
                )
            }
        }
        item {
            HavenCard(container = HavenColors.SageMist) {
                Text("Latest meter reading", style = MaterialTheme.typography.bodyMedium, color = HavenColors.SageDeep)
                Text(units(u.latestReading), style = MaterialTheme.typography.headlineMedium, color = HavenColors.SageDeep)
            }
        }
        item { SectionTitle("Past readings") }
        if (u.readings.isEmpty()) {
            item { Text("No readings yet.", color = HavenColors.Brown) }
        }
        items(u.readings, key = { it.id }) { ReadingRow(it, onPhoto = { photoFor = it }) }
    }
    photoFor?.let { PhotoDialog(vm, it) { photoFor = null } }
}
