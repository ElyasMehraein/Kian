package com.ely.kian.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class KianColors(
    val canvas: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val panel: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color
)

val LocalKianColors = staticCompositionLocalOf<KianColors> {
    error("No KianColors provided")
}

private val LightKianColors = KianColors(
    canvas = Color(0xFFFFFFFF),
    ink = Color(0xFF111827),
    muted = Color(0xFF64748B),
    line = Color(0xFFE5E7EB),
    panel = Color(0xFFF8FAFC),
    accent = Color(0xFF2563EB),
    accentSoft = Color(0xFFDBEAFE),
    danger = Color(0xFFB91C1C)
)

private val DarkKianColors = KianColors(
    canvas = Color(0xFF111827),
    ink = Color(0xFFF9FAFB),
    muted = Color(0xFF94A3B8),
    line = Color(0xFF374151),
    panel = Color(0xFF1F2937),
    accent = Color(0xFF3B82F6),
    accentSoft = Color(0xFF1E3A8A),
    danger = Color(0xFFEF4444)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkKianColors.accent,
    secondary = DarkKianColors.ink,
    tertiary = DarkKianColors.accentSoft,
    background = DarkKianColors.canvas,
    surface = DarkKianColors.panel,
    onPrimary = Color.White,
    onSecondary = DarkKianColors.canvas,
    onTertiary = DarkKianColors.ink,
    onBackground = DarkKianColors.ink,
    onSurface = DarkKianColors.ink,
    outline = DarkKianColors.line
)

private val LightColorScheme = lightColorScheme(
    primary = LightKianColors.accent,
    secondary = LightKianColors.ink,
    tertiary = LightKianColors.accentSoft,
    background = LightKianColors.canvas,
    surface = LightKianColors.panel,
    onPrimary = Color.White,
    onSecondary = LightKianColors.canvas,
    onTertiary = LightKianColors.ink,
    onBackground = LightKianColors.ink,
    onSurface = LightKianColors.ink,
    outline = LightKianColors.line
)

object KianTheme {
    val colors: KianColors
        @Composable
        get() = LocalKianColors.current
}

@Composable
fun KianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled by default for UI fidelity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val kianColors = if (darkTheme) DarkKianColors else LightKianColors
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalKianColors provides kianColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
