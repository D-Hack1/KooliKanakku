package com.koolikanaku

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koolikanaku.ai.initDetector
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.AppViewModel
import com.koolikanaku.ui.navigation.AppNavGraph
import com.koolikanaku.ui.theme.KooliKanakuTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initDetector(this)
        setContent {
            val vm: AppViewModel = viewModel()
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(Unit) { vm.changeDarkTheme(systemDark) }

            val baseContext = LocalContext.current
            val localizedContext = remember(vm.language) {
                val locale = if (vm.language == AppLanguage.ML) Locale("ml") else Locale.ENGLISH
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                val localizedResources = baseContext.createConfigurationContext(config).resources
                // Wraps the real Activity context (not a floating configuration context) so Activity
                // lookups used by rememberLauncherForActivityResult etc. still resolve correctly.
                object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedResources
                }
            }

            KooliKanakuTheme(darkTheme = vm.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalContext provides localizedContext) {
                        AppNavGraph(vm = vm)
                    }
                }
            }
        }
    }
}
