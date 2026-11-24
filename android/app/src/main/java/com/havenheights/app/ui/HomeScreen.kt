package com.havenheights.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.ui.screens.*
import com.havenheights.app.ui.theme.HavenColors

enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Overview("Overview", Icons.Filled.Dashboard),
    Tenants("Tenants", Icons.Filled.People),
    Meters("Meters", Icons.Filled.WaterDrop),
    Bills("Bills", Icons.Filled.Receipt),
    Requests("Requests", Icons.Filled.Build),
    Notices("Notices", Icons.Filled.Campaign),
    Usage("Water", Icons.Filled.WaterDrop),
}

// Landlord notices open from the menu and the overview, to keep the bottom bar at five tabs.
private val landlordTabs = listOf(HomeTab.Overview, HomeTab.Tenants, HomeTab.Meters, HomeTab.Bills, HomeTab.Requests)
private val tenantTabs = listOf(HomeTab.Home, HomeTab.Bills, HomeTab.Usage, HomeTab.Requests, HomeTab.Notices)

@Composable
fun HomeScreen(vm: HavenViewModel, nav: NavController) {
    val user by vm.user.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val u = user ?: return
    val tabs = if (u.isLandlord) landlordTabs else tenantTabs
    var index by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs[index.coerceIn(tabs.indices)]
    val unread = inbox?.unread ?: 0

    LaunchedEffect(u.role) { vm.refreshAll() }

    Scaffold(
        containerColor = HavenColors.Cream,
        topBar = {
            HavenTopBar(
                title = if (tab == HomeTab.Overview || tab == HomeTab.Home) "Hello, ${u.name.substringBefore(' ')}" else tab.label,
                actions = {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = HavenColors.Brown)
                    IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
                        BadgedBox(badge = {
                            if (unread > 0) Badge(containerColor = HavenColors.Burgundy, contentColor = HavenColors.Cream) { Text(if (unread > 9) "9+" else "$unread") }
                        }) { Icon(Icons.Filled.Notifications, "Notifications") }
                    }
                    IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("My profile") }, onClick = { menu = false; nav.navigate(Routes.PROFILE) })
                            if (u.isLandlord) DropdownMenuItem(text = { Text("Notices") }, onClick = { menu = false; nav.navigate(Routes.NOTICES) })
                            DropdownMenuItem(text = { Text("Change password") }, onClick = { menu = false; nav.navigate(Routes.PASSWORD) })
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; vm.logout() })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = HavenColors.Paper) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == index,
                        onClick = { index = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HavenColors.Burgundy,
                            selectedTextColor = HavenColors.Burgundy,
                            indicatorColor = HavenColors.Nude,
                            unselectedIconColor = HavenColors.Brown,
                            unselectedTextColor = HavenColors.Brown,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (pending.isNotEmpty()) {
                val waiting = pending.count { it.error == null }
                Text(
                    if (waiting > 0) "$waiting item${if (waiting == 1) "" else "s"} saved on this phone, sending when you're back online."
                    else "Some saved items need your attention. Open the Meters tab.",
                    style = MaterialTheme.typography.labelMedium,
                    color = HavenColors.SageDeep,
                    modifier = Modifier.fillMaxWidth().background(HavenColors.SageMist).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (tab) {
                    HomeTab.Home -> TenantDashboardScreen(
                        vm,
                        onOpenTab = { target -> index = tabs.indexOf(target).coerceAtLeast(0) },
                        onOpenProfile = { nav.navigate(Routes.PROFILE) },
                    )
                    HomeTab.Overview -> OverviewScreen(
                        vm,
                        onOpenTab = { target -> index = tabs.indexOf(target).coerceAtLeast(0) },
                        onAddTenant = { nav.navigate(Routes.ADD_TENANT) },
                        onOpenNotices = { nav.navigate(Routes.NOTICES) },
                    )
                    HomeTab.Tenants -> TenantsScreen(vm, onOpen = { nav.navigate(Routes.tenant(it)) }, onAdd = { nav.navigate(Routes.ADD_TENANT) })
                    HomeTab.Meters -> MetersScreen(vm, onRecord = { nav.navigate(Routes.reading(it)) })
                    HomeTab.Bills -> BillsScreen(vm, u.isLandlord, onNew = { nav.navigate(Routes.newBill()) })
                    HomeTab.Requests -> RequestsScreen(vm, u.isLandlord, onNew = { nav.navigate(Routes.NEW_REQUEST) })
                    HomeTab.Notices -> NoticesScreen(vm, u.isLandlord, onNew = { nav.navigate(Routes.NEW_NOTICE) })
                    HomeTab.Usage -> UsageScreen(vm)
                }
            }
        }
    }
}
