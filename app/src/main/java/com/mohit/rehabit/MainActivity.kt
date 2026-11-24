package com.mohit.rehabit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.mohit.rehabit.reminders.Notifier
import com.mohit.rehabit.ui.RehabitAppUi
import com.mohit.rehabit.ui.theme.RehabitTheme

class MainActivity : ComponentActivity() {

    /** A screen to open, e.g. from a notification's Start button. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(Notifier.EXTRA_ROUTE)
        setContent {
            RehabitTheme {
                RehabitAppUi(
                    pendingRoute = pendingRoute.value,
                    onRouteHandled = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
}
