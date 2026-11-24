package com.havenheights.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.havenheights.app.ui.HavenApp
import com.havenheights.app.ui.HavenViewModel
import com.havenheights.app.ui.theme.HavenTheme

class MainActivity : ComponentActivity() {
    private val vm: HavenViewModel by viewModels {
        viewModelFactory { initializer { HavenViewModel(application as HavenApplication) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HavenTheme { HavenApp(vm) } }
    }
}
