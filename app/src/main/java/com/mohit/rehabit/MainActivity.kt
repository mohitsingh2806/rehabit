package com.mohit.rehabit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mohit.rehabit.ui.RehabitAppUi
import com.mohit.rehabit.ui.theme.RehabitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RehabitTheme { RehabitAppUi() }
        }
    }
}
