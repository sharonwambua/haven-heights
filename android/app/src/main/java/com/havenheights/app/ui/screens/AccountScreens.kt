package com.havenheights.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors

@Composable
fun ChangePasswordScreen(vm: HavenViewModel, nav: NavController) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = confirm.isNotEmpty() && confirm != new

    Scaffold(containerColor = HavenColors.Cream, topBar = { HavenTopBar("Change password", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "If your landlord gave you a temporary password, pick your own now.",
                style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown,
            )
            HavenField("Current password", current, { current = it; error = null }, password = true)
            HavenField("New password", new, { new = it; error = null }, password = true, hint = "At least 6 characters")
            HavenField("Repeat new password", confirm, { confirm = it }, password = true,
                error = if (mismatch) "The passwords don't match" else null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                "Save password",
                onClick = { vm.changePassword(current, new, { error = it }) { nav.popBackStack() } },
                enabled = current.isNotEmpty() && new.length >= 6 && new == confirm,
                loading = busy,
            )
        }
    }
}
