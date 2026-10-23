package com.familytracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familytracker.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme =
                    if (dark)
                        darkColorScheme(primary = Color(0xFF85D5C5), secondary = Color(0xFFC1D5BD))
                    else
                        lightColorScheme(
                            primary = Color(0xFF247B72),
                            secondary = Color(0xFF52634F),
                            background = Color(0xFFF5F8F4),
                        )
            ) {
                if ((application as TrackerApp).graph == null)
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(stringResource(R.string.config_missing))
                        }
                    }
                else TrackerScreen(viewModel())
            }
        }
    }
}
