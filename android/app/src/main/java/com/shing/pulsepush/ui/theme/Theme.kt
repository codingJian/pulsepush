package com.shing.pulsepush.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF4DB6AC)
)

/** Level colors (PRD 4.4): info green / warning orange / critical red. */
object LevelColors {
    val Info = Color(0xFF2E7D32)
    val Warning = Color(0xFFEF6C00)
    val Critical = Color(0xFFC62828)
    val Unknown = Color(0xFF616161)

    fun forLevel(level: String): Color = when (level.lowercase()) {
        "info" -> Info
        "warning" -> Warning
        "critical" -> Critical
        else -> Unknown
    }
}

@Composable
fun PulsePushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
