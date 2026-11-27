package io.github.uchihastesla.breenocallvoice.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.uchihastesla.breenocallvoice.config.ServiceUiState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatusCard(
    uiState: ServiceUiState,
    breenoVersionText: String,
    onRequestScope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        uiState.isActivated && uiState.isTargetInScope -> MiuixTheme.colorScheme.primary
        uiState.isActivated -> MiuixTheme.colorScheme.primaryVariant
        else -> MiuixTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        uiState.isActivated && uiState.isTargetInScope -> MiuixTheme.colorScheme.onPrimary
        uiState.isActivated -> MiuixTheme.colorScheme.onPrimary
        else -> MiuixTheme.colorScheme.onErrorContainer
    }
    val title = when {
        uiState.isActivated && uiState.isTargetInScope -> "模块已激活"
        uiState.isActivated -> "模块已激活 · 待授权作用域"
        else -> "模块未激活"
    }
    val subtitle = when {
        uiState.isActivated && uiState.isTargetInScope -> {
            val fw = buildString {
                append(uiState.frameworkName.ifBlank { "LSPosed" })
                if (uiState.frameworkVersion.isNotBlank()) {
                    append(" ")
                    append(uiState.frameworkVersion)
                }
                if (uiState.apiVersion > 0) {
                    append(" · API ")
                    append(uiState.apiVersion)
                }
            }
            "$fw\n目标应用：$breenoVersionText"
        }
        uiState.isActivated -> {
            "尚未将小布助手（com.heytap.speechassist）加入作用域\n目标应用：$breenoVersionText"
        }
        else -> {
            "请在 LSPosed 管理器中启用本模块并勾选「小布助手」，然后强行停止小布助手后重试\n目标应用：$breenoVersionText"
        }
    }
    val icon = when {
        uiState.isActivated && uiState.isTargetInScope -> MiuixIcons.Mic
        uiState.isActivated -> MiuixIcons.Settings
        else -> MiuixIcons.Info
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = contentColor.copy(alpha = 0.88f),
                )
            }
        }

        if (uiState.isActivated && !uiState.isTargetInScope) {
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(
                text = "一键请求小布助手作用域",
                onClick = onRequestScope,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = contentColor.copy(alpha = 0.18f),
                    textColor = contentColor,
                ),
            )
        }
    }
}
