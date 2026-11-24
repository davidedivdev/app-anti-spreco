package com.davide.salvacibo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davide.salvacibo.domain.Urgency

// Palette "fresca": verde foglia, corallo, ambra, su un bianco caldo.
private val Leaf = Color(0xFF1E8A5F)
private val LeafLight = Color(0xFF7FDDB0)
private val Coral = Color(0xFFFF7A59)
private val Amber = Color(0xFFFFB547)

private val LightColors = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFDD),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDD3),
    onSecondaryContainer = Color(0xFF3B0A00),
    tertiary = Amber,
    onTertiary = Color(0xFF3D2600),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF17201B),
    surface = Color(0xFFF6F7F2),
    onSurface = Color(0xFF17201B),
    surfaceVariant = Color(0xFFE4E9E1),
    onSurfaceVariant = Color(0xFF5B665F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF2EB),
    surfaceContainerHigh = Color(0xFFE9EDE5),
    surfaceContainerHighest = Color(0xFFE2E7DE),
    outline = Color(0xFFBCC5BD),
    outlineVariant = Color(0xFFDDE3DA),
    error = Color(0xFFD93A3F),
)

private val DarkColors = darkColorScheme(
    primary = LeafLight,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0E5236),
    onPrimaryContainer = Color(0xFFCDEFDD),
    secondary = Color(0xFFFF9C80),
    onSecondary = Color(0xFF5F1600),
    secondaryContainer = Color(0xFF5A2516),
    onSecondaryContainer = Color(0xFFFFDDD3),
    tertiary = Amber,
    onTertiary = Color(0xFF3D2600),
    background = Color(0xFF0F1412),
    onBackground = Color(0xFFE3E9E4),
    surface = Color(0xFF0F1412),
    onSurface = Color(0xFFE3E9E4),
    surfaceVariant = Color(0xFF2A322D),
    onSurfaceVariant = Color(0xFFAEB9B1),
    surfaceContainerLowest = Color(0xFF0A0E0C),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B221E),
    surfaceContainerHigh = Color(0xFF232B26),
    surfaceContainerHighest = Color(0xFF2C3530),
    outline = Color(0xFF4B564F),
    outlineVariant = Color(0xFF323B36),
    error = Color(0xFFFF8A80),
)

/** Colori "semaforo" per l'urgenza: colore pieno + sfondo tenue. */
@Immutable
data class UrgencyColors(
    val expired: Pair<Color, Color>,
    val today: Pair<Color, Color>,
    val soon: Pair<Color, Color>,
    val ok: Pair<Color, Color>,
) {
    fun strong(u: Urgency): Color = of(u).first
    fun soft(u: Urgency): Color = of(u).second
    private fun of(u: Urgency) = when (u) {
        Urgency.EXPIRED -> expired
        Urgency.TODAY -> today
        Urgency.SOON -> soon
        Urgency.OK -> ok
    }
}

private val LightUrgency = UrgencyColors(
    expired = Color(0xFFD93A3F) to Color(0xFFFDE1E0),
    today = Color(0xFFF2611D) to Color(0xFFFFE5D6),
    soon = Color(0xFFD99400) to Color(0xFFFFF1C9),
    ok = Color(0xFF1E8A5F) to Color(0xFFDDF4E7),
)

private val DarkUrgency = UrgencyColors(
    expired = Color(0xFFFF7B7B) to Color(0xFF4A1C1D),
    today = Color(0xFFFF9C66) to Color(0xFF4A2614),
    soon = Color(0xFFFFCC4D) to Color(0xFF433411),
    ok = Color(0xFF7FDDB0) to Color(0xFF173A2A),
)

val LocalUrgencyColors = staticCompositionLocalOf { LightUrgency }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Stile per i numeri grandi del conto alla rovescia. */
val CountdownStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = (-0.5).sp)

@Composable
fun SalvaciboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalUrgencyColors provides if (darkTheme) DarkUrgency else LightUrgency,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
