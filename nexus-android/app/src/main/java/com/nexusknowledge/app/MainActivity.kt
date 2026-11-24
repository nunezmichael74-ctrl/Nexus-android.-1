package com.nexusknowledge.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nexusknowledge.app.data.NoteStore
import com.nexusknowledge.app.ui.Night
import com.nexusknowledge.app.ui.NexusApp
import com.nexusknowledge.app.ui.NexusTheme
import com.nexusknowledge.app.ui.Paper
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val store by lazy { NoteStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bg = (if (dark) Night else Paper).toArgb()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!dark) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent { NexusTheme { NexusApp(store) } }
    }
}
