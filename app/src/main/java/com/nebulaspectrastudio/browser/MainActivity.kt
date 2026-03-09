package com.nebulaspectrastudio.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.nebulaspectrastudio.browser.ui.BrowserApp
import com.nebulaspectrastudio.browser.ui.theme.BrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startUrl = intent?.data?.toString()
        setContent {
            BrowserTheme {
                BrowserApp(startUrl = startUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // handled via recomposition
    }
}
