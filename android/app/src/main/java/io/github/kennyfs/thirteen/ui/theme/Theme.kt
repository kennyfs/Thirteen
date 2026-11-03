package io.github.kennyfs.thirteen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kennyfs.thirteen.ColorStyle
import io.github.kennyfs.thirteen.DarkMode
import io.github.kennyfs.thirteen.TextSize

/** The brand's green felt, as on the store page. */
val FELT = Color(0xFF1F6F54)

private val BrandLight = lightColorScheme(
  primary = FELT, onPrimary = Color.White, primaryContainer = Color(0xFFAEEFD0), onPrimaryContainer = Color(0xFF002115),
  secondary = Color(0xFF4D6357), onSecondary = Color.White, secondaryContainer = Color(0xFFCFE9D9), onSecondaryContainer = Color(0xFF0A1F15),
  tertiary = Color(0xFF3D6373), onTertiary = Color.White, tertiaryContainer = Color(0xFFC1E8FB), onTertiaryContainer = Color(0xFF001F29),
  error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
  background = Color(0xFFF5FBF6), onBackground = Color(0xFF171D1A), surface = Color(0xFFF5FBF6), onSurface = Color(0xFF171D1A),
  surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF404943), outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F0), surfaceContainer = Color(0xFFE9EFEA),
  surfaceContainerHigh = Color(0xFFE4EAE5), surfaceContainerHighest = Color(0xFFDEE4DF), surfaceDim = Color(0xFFD6DBD7),
  surfaceBright = Color(0xFFF5FBF6), inverseSurface = Color(0xFF2C322E), inverseOnSurface = Color(0xFFECF2ED), inversePrimary = Color(0xFF8CD5B4),
)

private val BrandDark = darkColorScheme(
  primary = Color(0xFF8CD5B4), onPrimary = Color(0xFF003827), primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFAEEFD0),
  secondary = Color(0xFFB4CCBE), onSecondary = Color(0xFF1F352A), secondaryContainer = Color(0xFF354B40), onSecondaryContainer = Color(0xFFCFE9D9),
  tertiary = Color(0xFFA5CCDF), onTertiary = Color(0xFF073543), tertiaryContainer = Color(0xFF244C5B), onTertiaryContainer = Color(0xFFC1E8FB),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
  background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4DF), surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4DF),
  surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C2), outline = Color(0xFF8A938C), outlineVariant = Color(0xFF404943),
  surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A), surfaceContainer = Color(0xFF1B211E),
  surfaceContainerHigh = Color(0xFF252B28), surfaceContainerHighest = Color(0xFF303633), surfaceDim = Color(0xFF0F1512),
  surfaceBright = Color(0xFF353B37), inverseSurface = Color(0xFFDEE4DF), inverseOnSurface = Color(0xFF2C322E), inversePrimary = FELT,
)

/**
 * The colors of the table, where the cards lie: the felt, a row's slot on it, the text on it, and the highlight of
 * selected cards and the row they can go to. Good and bad are readable on the felt.
 */
@Immutable
data class TableColors(
  val felt: Color,
  val slot: Color,
  val target: Color,
  val onFelt: Color,
  val onFeltDim: Color,
  val highlight: Color,
  val good: Color,
  val bad: Color,
  /** The main action on the table (Confirm). */
  val action: Color,
  val onAction: Color,
)

/** Text colors for good and bad outcomes on the theme's surfaces. */
@Immutable
data class Outcome(val good: Color, val bad: Color)

val LocalTable = staticCompositionLocalOf { tableFor(ColorStyle.TABLE, BrandLight, false) }
val LocalOutcome = staticCompositionLocalOf { Outcome(Color(0xFF2E7D32), Color(0xFFC62828)) }

private val BRASS = Color(0xFFF0C058)

private fun tableFor(style: ColorStyle, scheme: ColorScheme, dark: Boolean): TableColors = when (style) {
  ColorStyle.TABLE ->
    if (!dark) TableColors(FELT, Color(0xFF27795C), Color(0xFF2E8665), Color(0xFFEEF7F2), Color(0xFFB9D9C8), BRASS, Color(0xFFA8E6B0), Color(0xFFFFB4AB), Color(0xFFEEF7F2), FELT)
    else TableColors(Color(0xFF164F3D), Color(0xFF1D5C47), Color(0xFF256A52), Color(0xFFE3F0E9), Color(0xFFA3C4B3), Color(0xFFE8B954), Color(0xFFA8E6B0), Color(0xFFFFB4AB), Color(0xFFE3F0E9), Color(0xFF164F3D))
  ColorStyle.MINIMAL -> TableColors(
    scheme.surface, scheme.surfaceContainer, scheme.primaryContainer, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary,
    if (dark) Color(0xFF81C784) else Color(0xFF2E7D32), if (dark) Color(0xFFE57373) else Color(0xFFC62828), scheme.primary, scheme.onPrimary,
  )
  ColorStyle.WALLPAPER -> {
    val felt = if (dark) scheme.primaryContainer else scheme.primary
    val on = if (dark) scheme.onPrimaryContainer else scheme.onPrimary
    TableColors(
      felt, on.copy(alpha = 0.10f).compositeOver(felt), on.copy(alpha = 0.18f).compositeOver(felt), on, on.copy(alpha = 0.75f).compositeOver(felt),
      BRASS, Color(0xFFA8E6B0), Color(0xFFFFB4AB), on, felt,
    )
  }
}

/**
 * Material's sizes, a little firmer: body text 16sp, nothing under 12sp. The device's font size and the app's own text
 * size setting make it larger; the cards, not the words, are what should be big.
 */
private val AppTypography = Typography().let { t ->
  fun TextStyle.at(size: Int, line: Int, weight: FontWeight? = null) = copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight ?: fontWeight)
  t.copy(
    displaySmall = t.displaySmall.at(34, 42),
    headlineLarge = t.headlineLarge.at(30, 38, FontWeight.Bold),
    headlineMedium = t.headlineMedium.at(26, 34, FontWeight.Bold),
    headlineSmall = t.headlineSmall.at(22, 30, FontWeight.Bold),
    titleLarge = t.titleLarge.at(20, 26, FontWeight.Bold),
    titleMedium = t.titleMedium.at(16, 22, FontWeight.Medium),
    titleSmall = t.titleSmall.at(14, 20, FontWeight.Medium),
    bodyLarge = t.bodyLarge.at(16, 23),
    bodyMedium = t.bodyMedium.at(14, 20),
    bodySmall = t.bodySmall.at(13, 18),
    labelLarge = t.labelLarge.at(14, 20, FontWeight.Medium),
    labelMedium = t.labelMedium.at(13, 18, FontWeight.Medium),
    labelSmall = t.labelSmall.at(12, 16, FontWeight.Medium),
  )
}

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

/** Whether the app is drawn dark: the cards are dark then too. */
val LocalDark = staticCompositionLocalOf { false }

fun isDark(mode: DarkMode, system: Boolean) = when (mode) {
  DarkMode.SYSTEM -> system
  DarkMode.LIGHT -> false
  DarkMode.DARK -> true
}

@Composable
fun ThirteenTheme(style: ColorStyle, darkMode: DarkMode, textSize: TextSize, content: @Composable () -> Unit) {
  val dark = isDark(darkMode, isSystemInDarkTheme())
  val context = LocalContext.current
  val dynamic = style != ColorStyle.TABLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val scheme = when {
    dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> BrandDark
    else -> BrandLight
  }
  val outcome = if (dark) Outcome(Color(0xFF81C784), Color(0xFFEF9A9A)) else Outcome(Color(0xFF2E7D32), Color(0xFFC62828))
  val density = LocalDensity.current
  CompositionLocalProvider(
    LocalTable provides tableFor(style, scheme, dark),
    LocalOutcome provides outcome,
    LocalDark provides dark,
    LocalDensity provides Density(density.density, density.fontScale * textSize.scale),
  ) {
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
  }
}
