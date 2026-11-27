package com.bigoke.metings.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.metings.R
import com.bigoke.metings.data.EntityType

val Ink = Color(0xFF14213D)
val InkDeep = Color(0xFF0B1226)
val Ocean = Color(0xFF1E4A7A)
val Teal = Color(0xFF0E8A7E)
val Mint = Color(0xFF3DD9C1)
val Violet = Color(0xFF6C63FF)
val Amber = Color(0xFFF4B740)
val RecordRed = Color(0xFFE5484D)

/** Signature Notule gradient: night ink → ocean → teal. */
val BrandGradient = Brush.linearGradient(listOf(InkDeep, Ocean, Teal))
val RecordGradient = Brush.linearGradient(listOf(Color(0xFFFF6B6B), RecordRed, Color(0xFFC81E5B)))
val AiGradient = Brush.linearGradient(listOf(Violet, Color(0xFF4F8CFF), Mint))

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9F7),
    onPrimaryContainer = Ink,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F3EE),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFB7791F),
    tertiaryContainer = Color(0xFFFCEFD6),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFEDF0F6),
    onSurfaceVariant = Color(0xFF5B6275),
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFE9ECF3),
    outline = Color(0xFFD3D8E2),
    outlineVariant = Color(0xFFE6E9F0),
    error = RecordRed,
    errorContainer = Color(0xFFFDE4E4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C2FF),
    onPrimary = Color(0xFF0B1226),
    primaryContainer = Color(0xFF1E2C4F),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Mint,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0E3F39),
    onSecondaryContainer = Color(0xFFC9F5EC),
    tertiary = Amber,
    tertiaryContainer = Color(0xFF3D2E0D),
    background = Color(0xFF0A0D14),
    onBackground = Color(0xFFE8EBF2),
    surface = Color(0xFF121722),
    onSurface = Color(0xFFE8EBF2),
    surfaceVariant = Color(0xFF1B2232),
    onSurfaceVariant = Color(0xFF9AA3B7),
    surfaceContainerLow = Color(0xFF0F131C),
    surfaceContainer = Color(0xFF151B27),
    surfaceContainerHigh = Color(0xFF1B2232),
    outline = Color(0xFF2E3648),
    outlineVariant = Color(0xFF222938),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3D1518),
)

private fun TextStyle.j(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) =
    copy(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.j(FontWeight.ExtraBold, 34, 40, -0.8),
    headlineLarge = base.headlineLarge.j(FontWeight.ExtraBold, 30, 36, -0.6),
    headlineMedium = base.headlineMedium.j(FontWeight.Bold, 26, 32, -0.5),
    headlineSmall = base.headlineSmall.j(FontWeight.Bold, 22, 28, -0.3),
    titleLarge = base.titleLarge.j(FontWeight.Bold, 20, 26, -0.2),
    titleMedium = base.titleMedium.j(FontWeight.SemiBold, 16, 22),
    titleSmall = base.titleSmall.j(FontWeight.SemiBold, 14, 20),
    bodyLarge = base.bodyLarge.j(FontWeight.Normal, 16, 26),
    bodyMedium = base.bodyMedium.j(FontWeight.Normal, 14, 21),
    bodySmall = base.bodySmall.j(FontWeight.Normal, 12, 17),
    labelLarge = base.labelLarge.j(FontWeight.SemiBold, 14, 20),
    labelMedium = base.labelMedium.j(FontWeight.SemiBold, 12, 16, 0.2),
    labelSmall = base.labelSmall.j(FontWeight.Medium, 11, 15, 0.3),
)

/** Numbers that line up (timers, timestamps). */
val MonoNumbers = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MetingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private val speakerColors = listOf(
    Color(0xFF4F8CFF), Color(0xFF14B8A6), Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF84CC16), Color(0xFFF59E0B), Color(0xFF06B6D4), Color(0xFFA855F7), Color(0xFF10B981),
)

fun speakerColor(index: Int): Color = speakerColors[((index % speakerColors.size) + speakerColors.size) % speakerColors.size]

fun entityColor(type: EntityType): Color = when (type) {
    EntityType.AMOUNT -> Color(0xFF16A34A)
    EntityType.NUMBER, EntityType.PERCENT, EntityType.MEASUREMENT -> Color(0xFF0284C7)
    EntityType.DATE, EntityType.TIME -> Color(0xFFD97706)
    EntityType.PERSON -> Color(0xFF7C3AED)
    EntityType.PLACE, EntityType.ADDRESS -> Color(0xFFDB2777)
    EntityType.PHONE, EntityType.EMAIL -> Color(0xFF0891B2)
    EntityType.COLOUR -> Color(0xFFEA580C)
    EntityType.SHAPE -> Color(0xFF65A30D)
    EntityType.TASK -> Color(0xFFE5484D)
}
