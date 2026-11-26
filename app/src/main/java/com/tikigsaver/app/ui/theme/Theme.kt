package com.tikigsaver.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF16A34A)
val EmeraldBright = Color(0xFF10B981)
val EmeraldDark = Color(0xFF15803D)
val SlateDark = Color(0xFF0F172A)
val SlateMuted = Color(0xFF64748B)
val SurfaceBg = Color(0xFFF8FAFC)
val CardBorder = Color(0xFFE2E8F0)

private val AppColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    secondary = EmeraldBright,
    onSecondary = Color.White,
    background = SurfaceBg,
    onBackground = SlateDark,
    surface = Color.White,
    onSurface = SlateDark,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SlateMuted,
    outline = CardBorder
)

@Composable
fun TikIGSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
