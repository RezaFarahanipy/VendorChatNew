package com.example.vendorchat.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pal {
    val Indigo = Color(0xFF4F46E5)
    val Violet = Color(0xFF7C3AED)
    val Teal = Color(0xFF14B8A6)
    val Danger = Color(0xFFE5484D)
    val Ok = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Brand: Brush = Brush.horizontalGradient(listOf(Indigo, Violet))
    val BrandDiag: Brush = Brush.linearGradient(listOf(Indigo, Violet))
    val TealGrad: Brush = Brush.linearGradient(listOf(Teal, Color(0xFF0EA5E9)))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF5F6FB), onBackground = Color(0xFF14152B),
    surface = Color.White, onSurface = Color(0xFF14152B),
    surfaceVariant = Color(0xFFEEF0F8), onSurfaceVariant = Color(0xFF5B5F7A),
    outline = Color(0xFFD5D8E8), error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B93FF), onPrimary = Color(0xFF0E0F2A),
    primaryContainer = Color(0xFF2B2E6B), onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0E1020), onBackground = Color(0xFFE8E9F5),
    surface = Color(0xFF171A2E), onSurface = Color(0xFFE8E9F5),
    surfaceVariant = Color(0xFF22263E), onSurfaceVariant = Color(0xFFA3A7C4),
    outline = Color(0xFF353A5A), error = Color(0xFFFF6B70)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val base = Typography()
private val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
