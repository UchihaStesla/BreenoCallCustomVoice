package com.custom.breenocallvoice.ui.screen

import androidx.compose.runtime.Composable
import com.custom.breenocallvoice.config.ServiceUiState
import com.custom.breenocallvoice.config.UiStyle
import com.custom.breenocallvoice.ui.screen.material3.Material3HomeScreen
import com.custom.breenocallvoice.ui.screen.miuix.MiuixHomeScreen

@Composable
fun HomeScreen(uiState: ServiceUiState) {
    when (uiState.uiStyle) {
        UiStyle.MIUIX -> MiuixHomeScreen(uiState = uiState)
        UiStyle.MATERIAL3 -> Material3HomeScreen(uiState = uiState)
    }
}
