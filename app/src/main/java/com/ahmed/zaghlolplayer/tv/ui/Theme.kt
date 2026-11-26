package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

object ZaghlolColors {
    val Bg = Color(0xFF0B0E1A)
    val Panel = Color(0xFF12152A)
    val Panel2 = Color(0xFF10132A)
    val Panel3 = Color(0xFF1A1E38)
    val Border = Color(0xFF232744)
    val Text = Color(0xFFE8EAF2)
    val Muted = Color(0xFF9AA3C0)
    val Dim = Color(0xFF6F77A0)
    val Accent = Color(0xFF8B5CF6)
    val Accent2 = Color(0xFF4F46E5)
    val AccentSoft = Color(0x2E8B5CF6)
    val Live = Color(0xFFEF4444)
    val Good = Color(0xFF22C55E)
    val Gold = Color(0xFFF59E0B)
}

private val ZaghlolScheme = darkColorScheme(
    primary = ZaghlolColors.Accent,
    secondary = ZaghlolColors.Accent2,
    background = ZaghlolColors.Bg,
    surface = ZaghlolColors.Panel,
    onPrimary = Color.White,
    onBackground = ZaghlolColors.Text,
    onSurface = ZaghlolColors.Text
)

/** App theme: dark + violet accent, Arabic RTL (matches Windows v2.0.3). */
@Composable
fun ZaghlolTvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = ZaghlolScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZaghlolColors.Bg)
            ) {
                content()
            }
        }
    }
}

/**
 * D-pad focus visuals for any clickable TV row/card: violet border + slight
 * scale-up when focused, so the focus is always obvious from the couch.
 * Returns a Modifier that is focusable and clickable.
 */
@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f, label = "tvScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) ZaghlolColors.Accent else Color.Transparent,
        label = "tvBorder"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }
        .clickable(enabled = enabled, onClick = onClick)
}

/** Same focus visuals for a focusable (not clickable) container. */
@Composable
fun Modifier.tvFocusable(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) ZaghlolColors.Accent else Color.Transparent,
        label = "tvBorder"
    )
    return this
        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }
        .focusable()
}
