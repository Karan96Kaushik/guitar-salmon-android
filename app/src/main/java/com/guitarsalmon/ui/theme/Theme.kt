package com.guitarsalmon.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Salmon accent over a deep teal, so the chroma bars and the "correct" state stay
// readable against the background in both themes.
private val Salmon = Color(0xFFF4825F)
private val SalmonLight = Color(0xFFFFB295)
private val DeepTeal = Color(0xFF14504A)
private val Teal = Color(0xFF2E8B7F)
private val MintLight = Color(0xFFA8E0D5)

/** Colour used for a correctly played chord. */
val CorrectGreen = Color(0xFF2FBF71)

private val DarkColors = darkColorScheme(
    primary = SalmonLight,
    onPrimary = Color(0xFF4A1505),
    primaryContainer = Color(0xFF6B2810),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = MintLight,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = DeepTeal,
    onSecondaryContainer = MintLight,
    background = Color(0xFF101014),
    onBackground = Color(0xFFE5E2DF),
    surface = Color(0xFF16161B),
    onSurface = Color(0xFFE5E2DF),
    surfaceVariant = Color(0xFF2A2A31),
    onSurfaceVariant = Color(0xFFC9C5C2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Salmon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B1005),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = MintLight,
    onSecondaryContainer = Color(0xFF00201A),
    background = Color(0xFFFCF8F6),
    onBackground = Color(0xFF1C1B1A),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEFE3DE),
    onSurfaceVariant = Color(0xFF51443F),
)

/**
 * Typography tweaked for one specific job: the live chord name has to be legible
 * from arm's length while the user is holding a guitar.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontSize = 96.sp,
            lineHeight = 100.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

/** Style for the big chord readout. */
val ChordDisplayStyle = TextStyle(
    fontSize = 88.sp,
    lineHeight = 92.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
fun GuitarSalmonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
