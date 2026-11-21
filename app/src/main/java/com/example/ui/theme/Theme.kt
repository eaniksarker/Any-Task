package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppThemeMode
import com.example.domain.model.TaskPriority

@Immutable
data class LiquidGlassPalette(
    val isDark: Boolean,
    val canvasBase: Color,
    val canvasSecondary: Color,
    val glassSurfaceTop: Color,
    val glassSurfaceBottom: Color,
    val glassSurfaceElevated: Color,
    val rimStart: Color,
    val rimEnd: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val divider: Color
)

private val DarkLiquidPalette = LiquidGlassPalette(
    isDark = true,
    canvasBase = DarkCanvasBase,
    canvasSecondary = DarkCanvasSecondary,
    glassSurfaceTop = Color(0x26FFFFFF),
    glassSurfaceBottom = Color(0x0EFFFFFF),
    glassSurfaceElevated = Color(0x33FFFFFF),
    rimStart = DarkGlassRimStart,
    rimEnd = DarkGlassRimEnd,
    orbPrimary = Color(0x5538BDF8),
    orbSecondary = Color(0x4D6366F1),
    orbTertiary = Color(0x3DA855F7),
    divider = Color(0x24FFFFFF)
)

private val LightLiquidPalette = LiquidGlassPalette(
    isDark = false,
    canvasBase = LightCanvasBase,
    canvasSecondary = LightCanvasSecondary,
    glassSurfaceTop = Color(0xDBFFFFFF),
    glassSurfaceBottom = Color(0x9EFFFFFF),
    glassSurfaceElevated = Color(0xF0FFFFFF),
    rimStart = LightGlassRimStart,
    rimEnd = LightGlassRimEnd,
    orbPrimary = Color(0x5538BDF8),
    orbSecondary = Color(0x45818CF8),
    orbTertiary = Color(0x38C084FC),
    divider = Color(0x1E0F172A)
)

val LocalLiquidGlassPalette = staticCompositionLocalOf { DarkLiquidPalette }

private val AnyTaskDarkColorScheme = darkColorScheme(
    primary = CyanFrost,
    onPrimary = Color(0xFF041E2B),
    primaryContainer = Color(0x3338BDF8),
    onPrimaryContainer = DarkTextPrimary,
    secondary = ElectricIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0x336366F1),
    onSecondaryContainer = DarkTextPrimary,
    tertiary = VioletMist,
    background = DarkCanvasBase,
    onBackground = DarkTextPrimary,
    surface = DarkCanvasSecondary,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0x22FFFFFF),
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0x40FFFFFF),
    error = PriorityHighRed
)

private val AnyTaskLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = Color(0xFF9333EA),
    background = LightCanvasBase,
    onBackground = LightTextPrimary,
    surface = LightCanvasSecondary,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0x99FFFFFF),
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0x330F172A),
    error = PriorityHighRed
)

private val AnyTaskShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun TaskPriority.accentColor(): Color = when (this) {
    TaskPriority.HIGH -> PriorityHighRed
    TaskPriority.MEDIUM -> PriorityMediumAmber
    TaskPriority.LOW -> PriorityLowGreen
}

fun TaskPriority.glassTintColor(isDark: Boolean): Color = when (this) {
    TaskPriority.HIGH -> if (isDark) Color(0x2BEF4444) else Color(0x1FEF4444)
    TaskPriority.MEDIUM -> if (isDark) Color(0x2BEAB308) else Color(0x22EAB308)
    TaskPriority.LOW -> if (isDark) Color(0x2B22C55E) else Color(0x1F22C55E)
}

@Composable
fun AnyTaskTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) AnyTaskDarkColorScheme else AnyTaskLightColorScheme
    val liquidPalette = if (darkTheme) DarkLiquidPalette else LightLiquidPalette

    CompositionLocalProvider(LocalLiquidGlassPalette provides liquidPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AnyTaskTypography,
            shapes = AnyTaskShapes,
            content = content
        )
    }
}
