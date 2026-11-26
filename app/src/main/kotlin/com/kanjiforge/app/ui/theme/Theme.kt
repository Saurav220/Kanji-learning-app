package com.kanjiforge.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm Japanese Editorial (Washi / Sumi ink + restrained Shu vermilion)
private val CharcoalDarkColors = darkColorScheme(
    primary = Color(0xFFD65A42),
    onPrimary = Color(0xFFFFF8F5),
    primaryContainer = Color(0xFF3A201B),
    onPrimaryContainer = Color(0xFFF5C5B8),
    secondary = Color(0xFF84A689),
    onSecondary = Color(0xFF122616),
    secondaryContainer = Color(0xFF1F2E22),
    onSecondaryContainer = Color(0xFFC8E0CC),
    tertiary = Color(0xFFCFA15A),
    onTertiary = Color(0xFF332308),
    tertiaryContainer = Color(0xFF332715),
    onTertiaryContainer = Color(0xFFF0D5A8),
    background = Color(0xFF141312),
    onBackground = Color(0xFFF0EBE1),
    surface = Color(0xFF1C1A18),
    onSurface = Color(0xFFF0EBE1),
    surfaceVariant = Color(0xFF25221F),
    onSurfaceVariant = Color(0xFFA8A095),
    surfaceContainerLowest = Color(0xFF100F0E),
    surfaceContainerLow = Color(0xFF181614),
    surfaceContainer = Color(0xFF1C1A18),
    surfaceContainerHigh = Color(0xFF25221F),
    surfaceContainerHighest = Color(0xFF2E2A26),
    outline = Color(0xFF6B635B),
    outlineVariant = Color(0xFF2C2824),
    error = Color(0xFFE07A70),
    onError = Color(0xFF3F0D09),
    errorContainer = Color(0xFF471F1B),
    onErrorContainer = Color(0xFFFCD6D2),
    inverseSurface = Color(0xFFF0EBE1),
    inverseOnSurface = Color(0xFF181614),
    inversePrimary = Color(0xFFB33E28),
)

private val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFFD65A42),
    onPrimary = Color(0xFFFFF8F5),
    primaryContainer = Color(0xFF331C17),
    onPrimaryContainer = Color(0xFFF5C5B8),
    secondary = Color(0xFF84A689),
    onSecondary = Color(0xFF122616),
    secondaryContainer = Color(0xFF1B281E),
    onSecondaryContainer = Color(0xFFC8E0CC),
    tertiary = Color(0xFFCFA15A),
    onTertiary = Color(0xFF332308),
    tertiaryContainer = Color(0xFF2E2312),
    onTertiaryContainer = Color(0xFFF0D5A8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF0EBE1),
    surface = Color(0xFF0D0C0B),
    onSurface = Color(0xFFF0EBE1),
    surfaceVariant = Color(0xFF161412),
    onSurfaceVariant = Color(0xFFA8A095),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080706),
    surfaceContainer = Color(0xFF0D0C0B),
    surfaceContainerHigh = Color(0xFF161412),
    surfaceContainerHighest = Color(0xFF1F1C19),
    outline = Color(0xFF5C554E),
    outlineVariant = Color(0xFF221F1C),
    error = Color(0xFFE07A70),
    onError = Color(0xFF3F0D09),
    errorContainer = Color(0xFF421B17),
    onErrorContainer = Color(0xFFFCD6D2),
    inverseSurface = Color(0xFFF0EBE1),
    inverseOnSurface = Color(0xFF121110),
    inversePrimary = Color(0xFFB33E28),
)

private val StudyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 60.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Medium,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
    ),
)

private val StudyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Immutable
data class KanjiForgeColors(
    val cardBorder: Color = Color(0xFF2B2723),
    val topBarContainer: Color = Color(0xFF141312),
    val topBarContent: Color = Color(0xFFF0EBE1),
    val dockContainer: Color = Color(0xFF121110),
    val dockBorder: Color = Color(0xFF262320),
    val successContainer: Color = Color(0xFF1E2E22),
    val onSuccessContainer: Color = Color(0xFFC8E0CC),
    val ratingAgain: Color = Color(0xFFE07A70),
    val ratingHard: Color = Color(0xFFCFA15A),
    val ratingGood: Color = Color(0xFF84A689),
    val ratingEasy: Color = Color(0xFFB7B0A4),
    val canvasGuideStroke: Color = Color(0x55D8D0C5),
    val canvasActiveStroke: Color = Color(0xFFD65A42),
    val drawingInk: Color = Color(0xFFF0EBE1),
    val canvasGrid: Color = Color(0x38968D82),
    val progressTrack: Color = Color(0xFF2A2622),
    val inactiveDayFill: Color = Color(0xFF211E1B),
    val activeDayFill: Color = Color(0xFF3A201B),
    val onActiveDayFill: Color = Color(0xFFF5C5B8),
    val readingOnContainer: Color = Color(0xFF1F2E22),
    val onReadingOn: Color = Color(0xFFB7D6BC),
    val readingKunContainer: Color = Color(0xFF312515),
    val onReadingKun: Color = Color(0xFFE8C68D),
    val readingNameContainer: Color = Color(0xFF272421),
    val onReadingName: Color = Color(0xFFC7BFB3),
    val modeCardsAccent: Color = Color(0xFFD65A42),
    val modeQuizAccent: Color = Color(0xFFCFA15A),
    val modeWritingAccent: Color = Color(0xFF84A689),
    val intervalBuckets: List<Color> = listOf(
        Color(0xFFD65A42),
        Color(0xFFCFA15A),
        Color(0xFF9EB384),
        Color(0xFF84A689),
        Color(0xFF6B8E78),
    ),
) {
    fun jlptColor(level: Int): Color = when (level) {
        5 -> Color(0xFF84A689)
        4 -> Color(0xFF9EB384)
        3 -> Color(0xFFCFA15A)
        2 -> Color(0xFFD98555)
        1 -> Color(0xFFD65A42)
        else -> modeCardsAccent
    }
}

private val CharcoalKanjiForgeColors = KanjiForgeColors()

private val AmoledKanjiForgeColors = KanjiForgeColors(
    cardBorder = Color(0xFF1F1C19),
    topBarContainer = Color(0xFF000000),
    dockContainer = Color(0xFF050505),
    dockBorder = Color(0xFF1B1917),
    successContainer = Color(0xFF162419),
    progressTrack = Color(0xFF1B1917),
    inactiveDayFill = Color(0xFF141210),
)

val LocalKanjiForgeColors = staticCompositionLocalOf { CharcoalKanjiForgeColors }

object KanjiForgeTheme {
    val colors: KanjiForgeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKanjiForgeColors.current
}

@Composable
fun KanjiForgeTheme(
    amoledDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (amoledDark) AmoledDarkColors else CharcoalDarkColors
    val forgeColors = if (amoledDark) AmoledKanjiForgeColors else CharcoalKanjiForgeColors
    CompositionLocalProvider(LocalKanjiForgeColors provides forgeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudyTypography,
            shapes = StudyShapes,
            content = content,
        )
    }
}
