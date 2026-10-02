package com.vynyl.record

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vynyl.record.ui.VynylApp
import com.vynyl.record.ui.components.Onboarding
import com.vynyl.record.ui.components.Splash
import com.vynyl.record.ui.components.needsOnboarding
import com.vynyl.record.ui.theme.VynylTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VynylTheme {
                var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                var welcome by rememberSaveable { mutableStateOf(needsOnboarding(this@MainActivity)) }
                Box {
                    VynylApp()
                    if (welcome) Onboarding { welcome = false }
                    if (splash) Splash { splash = false }
                }
            }
        }
    }
}
