package com.nekos.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NekOSHomeClock(
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(CLOCK_FORMAT)) }
    var currentDate by remember {
        mutableStateOf(LocalDate.now().format(DATE_FORMAT).uppercase(Locale.getDefault()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            currentTime = now.format(CLOCK_FORMAT)
            currentDate = LocalDate.now().format(DATE_FORMAT).uppercase(Locale.getDefault())

            // Refresh on the minute boundary rather than waking every second.
            val waitUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(waitUntilNextMinute.coerceAtLeast(250L))
        }
    }

    val accent = Color(colorArgb)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = currentDate,
            color = accent.copy(alpha = 0.82f),
            fontFamily = NekOSFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.15.sp,
            maxLines = 1
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            AnimatedContent(
                targetState = currentTime,
                transitionSpec = {
                    fadeIn(animationSpec = tween(260)) togetherWith
                        fadeOut(animationSpec = tween(180))
                },
                label = "nekos-clock-time"
            ) { time ->
                Text(
                    text = time,
                    color = accent,
                    fontFamily = NekOSFontFamily,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.35).sp,
                    maxLines = 1
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.padding(start = 1.dp)
            ) {
                drawCircle(
                    color = accent.copy(alpha = 0.8f),
                    radius = 2.dp.toPx()
                )
            }
            Text(
                text = "NEKOS  /  LOCAL TIME",
                color = accent.copy(alpha = 0.64f),
                fontFamily = NekOSFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.05.sp,
                maxLines = 1
            )
        }
    }
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
