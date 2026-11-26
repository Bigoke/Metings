package com.bigoke.metings.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bigoke.metings.data.EntityType

val Ink = Color(0xFF14213D)
val Teal = Color(0xFF0E8A7E)
val RecordRed = Color(0xFFD93A3A)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3F2),
    onPrimaryContainer = Ink,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEEE9),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFFD2D6DE),
    outlineVariant = Color(0xFFE4E7EC),
    error = RecordRed,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4C5EC),
    onPrimary = Color(0xFF0C1630),
    primaryContainer = Color(0xFF26355A),
    onPrimaryContainer = Color(0xFFDCE3F2),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0F4A42),
    onSecondaryContainer = Color(0xFFCDEEE9),
    tertiary = Color(0xFFE8B35A),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF232731),
    onSurfaceVariant = Color(0xFFA6ADBB),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF2A2F39),
    error = Color(0xFFFF6B6B),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val MonoNumbers = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)

@Composable
fun MetingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

private val speakerColors = listOf(
    Color(0xFF2F6FDE), Color(0xFF0E8A7E), Color(0xFFC2410C), Color(0xFF7C3AED), Color(0xFFBE185D),
    Color(0xFF4D7C0F), Color(0xFFB45309), Color(0xFF0369A1), Color(0xFF9333EA), Color(0xFF0F766E),
)

fun speakerColor(index: Int): Color = speakerColors[((index % speakerColors.size) + speakerColors.size) % speakerColors.size]

fun entityColor(type: EntityType): Color = when (type) {
    EntityType.AMOUNT -> Color(0xFF15803D)
    EntityType.NUMBER, EntityType.PERCENT, EntityType.MEASUREMENT -> Color(0xFF0369A1)
    EntityType.DATE, EntityType.TIME -> Color(0xFFB45309)
    EntityType.PERSON -> Color(0xFF7C3AED)
    EntityType.PLACE, EntityType.ADDRESS -> Color(0xFFBE185D)
    EntityType.PHONE, EntityType.EMAIL -> Color(0xFF0E7490)
    EntityType.COLOUR -> Color(0xFF9A3412)
    EntityType.SHAPE -> Color(0xFF4D7C0F)
    EntityType.TASK -> Color(0xFFDC2626)
}
