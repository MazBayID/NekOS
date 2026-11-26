package com.nekos.android

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
internal val NekOSFontFamily = FontFamily(
    Font(
        R.font.space_grotesk,
        weight = FontWeight.W300,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.W700,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

/**
 * Shared Space Grotesk type scale for the complete NekOS interface.
 * The established sizes and hierarchy are preserved while every Material
 * typography role uses the bundled variable font.
 */
internal val NekOSTypography = Typography(
    displayLarge = TextStyle(fontFamily = NekOSFontFamily, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    displayMedium = TextStyle(fontFamily = NekOSFontFamily, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = NekOSFontFamily, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = NekOSFontFamily, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = NekOSFontFamily, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = NekOSFontFamily, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = NekOSFontFamily, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = NekOSFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = NekOSFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = NekOSFontFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = NekOSFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = NekOSFontFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = NekOSFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = NekOSFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = NekOSFontFamily, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
)
