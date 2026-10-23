package com.koolikanaku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.koolikanaku.ui.KooliKanakuApp

/**
 * Thin entry point. The real app wiring (navigation, ViewModel) is Abhinav's
 * AppNavGraph/MainViewModel; until those land, this hosts Aadi's screens directly
 * so they can be reviewed end to end.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KooliKanakuApp()
        }
    }
}
