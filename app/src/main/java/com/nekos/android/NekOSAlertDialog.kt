package com.nekos.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NekOSAlertDialog(
    onDismissRequest: () -> Unit,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color(0xFFFFFBFE),
    titleContentColor: Color = Color(0xFF1D1B20),
    textContentColor: Color = Color(0xFF49454F),
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.Center
        ) {
            val availableDialogHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 14.dp)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 32.dp)
                        .heightIn(max = availableDialogHeight * 0.88f - 16.dp),
                    shape = shape,
                    color = containerColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 38.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                                    title()
                                }
                            }
                        }
                        if (text != null) {
                            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    text()
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) dismissButton()
                            confirmButton()
                        }
                    }
                }
                TopFrame(Modifier.align(Alignment.TopCenter).padding(horizontal = 10.dp))
            }
        }
    }
}
