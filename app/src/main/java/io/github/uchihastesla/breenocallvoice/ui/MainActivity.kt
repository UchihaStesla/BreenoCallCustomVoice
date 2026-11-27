package io.github.uchihastesla.breenocallvoice.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.uchihastesla.breenocallvoice.config.XposedServiceManager
import io.github.uchihastesla.breenocallvoice.ui.screen.HomeScreen
import io.github.uchihastesla.breenocallvoice.ui.theme.AppTheme
import io.github.uchihastesla.breenocallvoice.ui.theme.isThemeDark

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by XposedServiceManager.state.collectAsState()
            val darkTheme = isThemeDark(uiState.themeMode)

            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }

            AppTheme(
                uiStyle = uiState.uiStyle,
                themeMode = uiState.themeMode,
            ) {
                HomeScreen(uiState = uiState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        XposedServiceManager.refresh()
    }
}
