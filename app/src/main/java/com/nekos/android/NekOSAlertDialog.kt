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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
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
    shape: Shape = RoundedCornerShape(18.dp),
    dialogWidth: androidx.compose.ui.unit.Dp? = null,
    containerColor: Color = Color(0xFFFFFBFE),
    titleContentColor: Color = Color(0xFF1D1B20),
    textContentColor: Color = Color(0xFF5E574E),
    topFrameColorArgb: Long = DEFAULT_LAUNCHER_UI_COLOR,
    topFrameOffsetY: androidx.compose.ui.unit.Dp = (-6).dp,
    topFrameHorizontalExpansion: androidx.compose.ui.unit.Dp = 0.dp,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null
) {
    val motifBrush = rememberDialogMotifBrush()
    val motifTint = ColorFilter.tint(darkenMotifColor(containerColor), BlendMode.SrcIn)

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
                    .widthIn(max = 360.dp)
                    .then(dialogWidth?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .heightIn(max = availableDialogHeight * 0.80f - 12.dp),
                    shape = shape,
                    color = containerColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                motifBrush?.let {
                                    drawRect(brush = it, alpha = 0.18f, colorFilter = motifTint)
                                }
                            }
                            .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                                    title()
                                }
                            }
                            if (text != null) {
                                SettingsBrushSeparator()
                            }
                        }
                        if (text != null) {
                            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
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
                TopFrame(
                    colorArgb = topFrameColorArgb,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = topFrameOffsetY)
                        .graphicsLayer {
                            val expansionPx = topFrameHorizontalExpansion.toPx()
                            scaleX = if (size.width > 0f) (size.width + expansionPx * 2f) / size.width else 1f
                        }
                )
            }
        }
    }
}
