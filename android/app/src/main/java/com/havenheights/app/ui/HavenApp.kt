package com.havenheights.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.havenheights.app.ui.screens.*
import com.havenheights.app.ui.theme.HavenColors

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ADD_TENANT = "tenant/new"
    const val TENANT = "tenant/{id}"
    const val READING = "reading/{id}"
    const val NEW_REQUEST = "request/new"
    const val NEW_NOTICE = "notice/new"
    const val PASSWORD = "account/password"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val NOTICES = "notices"
    const val NEW_BILL = "bill/new?tenancy={tenancy}"
    const val LEASE = "lease/{id}"
    fun newBill(tenancy: Int = -1) = "bill/new?tenancy=$tenancy"
    fun lease(id: Int) = "lease/$id"
    fun tenant(id: Int) = "tenant/$id"
    fun reading(id: Int) = "reading/$id"
}

@Composable
fun HavenApp(vm: HavenViewModel) {
    val nav = rememberNavController()
    val user by vm.user.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val start = remember { if (vm.user.value == null) Routes.LOGIN else Routes.HOME }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    // Android 13+ needs permission before notices and repair updates can be shown.
    LaunchedEffect(user) {
        if (user != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Move between login and home when the session starts or ends.
    LaunchedEffect(user) {
        val route = nav.currentDestination?.route
        if (user == null && route != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        } else if (user != null && route == Routes.LOGIN) {
            nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize().background(HavenColors.Cream)) {
        NavHost(nav, startDestination = start) {
            composable(Routes.LOGIN) { LoginScreen(vm) }
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.ADD_TENANT) { AddTenantScreen(vm, nav) }
            composable(Routes.TENANT, listOf(navArgument("id") { type = NavType.IntType })) {
                TenantDetailScreen(vm, it.arguments!!.getInt("id"), nav)
            }
            composable(Routes.READING, listOf(navArgument("id") { type = NavType.IntType })) {
                RecordReadingScreen(vm, it.arguments!!.getInt("id"), nav)
            }
            composable(Routes.NEW_REQUEST) { NewRequestScreen(vm, nav) }
            composable(Routes.NEW_NOTICE) { NewNoticeScreen(vm, nav) }
            composable(Routes.PASSWORD) { ChangePasswordScreen(vm, nav) }
            composable(Routes.PROFILE) { ProfileScreen(vm, nav) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(vm, nav) }
            composable(Routes.NOTICES) { NoticesRouteScreen(vm, nav) }
            composable(Routes.NEW_BILL, listOf(navArgument("tenancy") { type = NavType.IntType; defaultValue = -1 })) {
                NewBillScreen(vm, nav, it.arguments?.getInt("tenancy") ?: -1)
            }
            composable(Routes.LEASE, listOf(navArgument("id") { type = NavType.IntType })) {
                EditLeaseScreen(vm, it.arguments!!.getInt("id"), nav)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }
}
