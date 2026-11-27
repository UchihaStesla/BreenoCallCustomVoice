package io.github.uchihastesla.breenocallvoice.ui.screen

import androidx.compose.runtime.Composable
import io.github.uchihastesla.breenocallvoice.config.ServiceUiState
import io.github.uchihastesla.breenocallvoice.config.UiStyle
import io.github.uchihastesla.breenocallvoice.ui.screen.material3.Material3HomeScreen
import io.github.uchihastesla.breenocallvoice.ui.screen.miuix.MiuixHomeScreen

@Composable
fun HomeScreen(uiState: ServiceUiState) {
    when (uiState.uiStyle) {
        UiStyle.MIUIX -> MiuixHomeScreen(uiState = uiState)
        UiStyle.MATERIAL3 -> Material3HomeScreen(uiState = uiState)
    }
}
