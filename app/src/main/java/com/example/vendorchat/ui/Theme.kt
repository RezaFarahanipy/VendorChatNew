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
    val Cyan = Color(0xFF0891B2)
    val Blue = Color(0xFF2563EB)
    val Violet = Color(0xFF6D28D9)
    val Danger = Color(0xFFE5484D)
    val Ok = Color(0xFF16A34A)
    val Amber = Color(0xFFF59E0B)

    /** گرادیان اصلی (هدر) */
    val Brand: Brush = Brush.horizontalGradient(listOf(Cyan, Blue, Violet))
    /** حباب کاربر و دکمه‌ها */
    val BrandDiag: Brush = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF6D28D9)))
    val Mint: Brush = Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0EA5E9)))

    private val avatarSets = listOf(
        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
        listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
        listOf(Color(0xFF14B8A6), Color(0xFF0EA5E9)),
        listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
        listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
        listOf(Color(0xFF22C55E), Color(0xFF0D9488))
    )

    /** رنگ ثابت برای هر وندور بر اساس نامش */
    fun avatar(name: String): Brush =
        Brush.linearGradient(avatarSets[Math.floorMod(name.hashCode(), avatarSets.size)])
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E9FF), onPrimaryContainer = Color(0xFF14226B),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF2F5FB), onBackground = Color(0xFF111827),
    surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9EDF7), onSurfaceVariant = Color(0xFF5B6478),
    outline = Color(0xFFD9DFEE), error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB0FF), onPrimary = Color(0xFF0B1235),
    primaryContainer = Color(0xFF26337A), onPrimaryContainer = Color(0xFFE1E7FF),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE7EAF6),
    surface = Color(0xFF141A2E), onSurface = Color(0xFFE7EAF6),
    surfaceVariant = Color(0xFF1F2742), onSurfaceVariant = Color(0xFF9EA7C2),
    outline = Color(0xFF2E3857), error = Color(0xFFFF6B70)
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
