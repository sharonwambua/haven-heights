package com.havenheights.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havenheights.app.ui.*
import com.havenheights.app.ui.theme.HavenColors

@Composable
fun LoginScreen(vm: HavenViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(HavenColors.Cream).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(HavenColors.Burgundy)
                .padding(horizontal = 28.dp).padding(top = 72.dp, bottom = 36.dp),
        ) {
            EstateMark(Modifier.width(150.dp))
            Spacer(Modifier.height(28.dp))
            Text("Haven Heights", style = MaterialTheme.typography.headlineLarge, color = HavenColors.Cream)
            Spacer(Modifier.height(6.dp))
            Text(
                "Water readings, repairs and notices for your estate in Machakos.",
                style = MaterialTheme.typography.bodyLarge,
                color = HavenColors.Nude,
            )
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sign in", style = MaterialTheme.typography.titleLarge)
            HavenField("Phone number", phone, { phone = it; error = null }, keyboard = KeyboardType.Phone)
            HavenField("Password", password, { password = it; error = null }, password = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            PrimaryButton(
                "Sign in",
                onClick = { vm.login(phone, password) { error = it } },
                enabled = phone.isNotBlank() && password.isNotBlank(),
                loading = busy,
            )
            Text(
                "Tenants: use the phone number and password your landlord gave you.",
                style = MaterialTheme.typography.bodyMedium,
                color = HavenColors.Brown,
            )
        }
    }
}
