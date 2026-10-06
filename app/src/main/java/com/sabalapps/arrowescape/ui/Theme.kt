package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import com.sabalapps.arrowescape.R
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.settings.ThemeOption

/**
 * Indigo primary, teal secondary, warm gold tertiary.
 *
 * The gold is the newest of the three and the only one with a story worth
 * writing down. `tertiary` used to be undefined in both schemes, so anything
 * reaching for it silently got Material's default muted brown — which is how the
 * Phase 5 hint glow ended up unreadable against a blue tile. It is now a real
 * colour chosen for the job it actually has: the warm accent the Daily Challenge
 * is identified by, over five pieces of world artwork that run from bright sky
 * to near-black space. Gold is the one warm note that is neither the arrow
 * tile's own indigo nor the red a blocked tap owns, and it survives being laid
 * over all five.
 *
 * The container pairs carry the colour; `tertiary` itself is dark enough in the
 * light scheme to take white text (5.7:1) and light enough in the dark scheme to
 * be read against the surface (9.8:1). Nothing here moves the Indigo/Teal
 * foundation.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51D5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF00A99D),
    background = Color(0xFFF3F5FF),
    onBackground = Color(0xFF12142B),
    secondaryContainer = Color(0xFFD3F3EF),
    onSecondaryContainer = Color(0xFF08322E),
    tertiary = Color(0xFF8B5E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF2A1800),
    surface = Color.White,
    onSurface = Color(0xFF12142B),
    surfaceVariant = Color(0xFFE6E8F5),
    onSurfaceVariant = Color(0xFF454860),
    outline = Color(0xFF9296B0),
    error = Color(0xFFD7263D),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C1FF),
    onPrimary = Color(0xFF15205E),
    primaryContainer = Color(0xFF2B3690),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF52DED0),
    background = Color(0xFF0E1020),
    onBackground = Color(0xFFE4E6F5),
    // Opaque dark teal. This was `0xFF12474170` — nine hex digits, so the
    // leading FF was truncated away and what survived was 0x12474170: a slate
    // blue at 7% alpha. Every surface using it, the endless/daily tier badge
    // included, was effectively unpainted, which went unnoticed while the thing
    // behind it was a flat gradient and would not have survived world artwork.
    secondaryContainer = Color(0xFF124741),
    onSecondaryContainer = Color(0xFFBDF2EB),
    tertiary = Color(0xFFF2C063),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF5E4000),
    onTertiaryContainer = Color(0xFFFFDEA8),
    surface = Color(0xFF1A1D33),
    onSurface = Color(0xFFE4E6F5),
    surfaceVariant = Color(0xFF2A2E48),
    onSurfaceVariant = Color(0xFFC3C6DC),
    outline = Color(0xFF7E82A0),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF3A0710)
)

/**
 * The type hierarchy, in one place.
 *
 * Material 3's scale already has the right *sizes*, and inventing a second set
 * would only guarantee that two screens disagree. What was missing was weight:
 * every screen was reaching for `MaterialTheme.typography.x` and then adding its
 * own `fontWeight = FontWeight.Bold`, which is how "bold" ended up meaning six
 * different things. So this keeps Material's sizes and fixes the weight per
 * role, and screens stop overriding it.
 *
 * Six rungs, which is all a puzzle game needs. The weights lean heavy — a game's
 * type is read at a glance, over artwork, at arm's length — and the handful of
 * styles Material's scale does not name live in `AppText` (DesignSystem.kt):
 *
 * | Role | Style | Where |
 * |---|---|---|
 * | Brand title | `displaySmall` | "Arrow Escape" on Home, once |
 * | Screen title | `headlineMedium` / `titleLarge` | Stats, Level Select, the result heading |
 * | Key number | `headlineMedium` | the figure a Stats card is about |
 * | Card title | `titleMedium` | the name of a card or a settings row |
 * | Body | `bodyMedium` / `bodySmall` | sentences, subtitles, captions |
 * | Label | `labelLarge` / `labelSmall` | buttons, and the tracked uppercase section headings |
 *
 * Body styles keep Material's regular weight deliberately: making everything
 * semibold is the fastest way to make nothing look important. Anything needing a
 * weight outside this table sets it locally and says why — the game's HUD does,
 * because it is fitting a heading, a badge and three hearts onto one 320dp line.
 */
private val Base = Typography()

/**
 * The game's one typeface: Nunito (SIL OFL 1.1), bundled as four static Latin
 * subsets in `res/font`. Rounded terminals and soft proportions give the menus a
 * game's personality while the digits stay plain and unambiguous. One family does
 * both jobs: ExtraBold/Bold carry titles, CTAs and reward moments; Regular carries
 * body copy. Static instances rather than the variable font because variation
 * axes need API 26 and minSdk is 23. Weights not listed (Medium) resolve to the
 * nearest listed one.
 */
val GameFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

private fun TextStyle.game() = copy(fontFamily = GameFont)

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.game(),
    displayMedium = Base.displayMedium.game(),
    // The one place the brand name appears.
    displaySmall = Base.displaySmall.copy(
        fontFamily = GameFont,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = Base.headlineLarge.game(),
    // Screen titles, and the numbers a Stats card is built around.
    headlineMedium = Base.headlineMedium.copy(fontFamily = GameFont, fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = GameFont, fontWeight = FontWeight.ExtraBold),
    // Card titles and the level number on a tile.
    titleLarge = Base.titleLarge.copy(fontFamily = GameFont, fontWeight = FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.copy(fontFamily = GameFont, fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontFamily = GameFont, fontWeight = FontWeight.Bold),
    bodyLarge = Base.bodyLarge.game(),
    bodyMedium = Base.bodyMedium.game(),
    bodySmall = Base.bodySmall.game(),
    // Buttons. Bold: this is a game, and a button label should be read at a glance.
    labelLarge = Base.labelLarge.copy(fontFamily = GameFont, fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontFamily = GameFont, fontWeight = FontWeight.SemiBold),
    // Section headings and chips. Light tracking: a rounded face reads as
    // corporate when it is spaced out.
    labelSmall = Base.labelSmall.copy(fontFamily = GameFont, fontWeight = FontWeight.ExtraBold)
)

@Composable
fun ArrowEscapeTheme(
    theme: ThemeOption = ThemeOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (theme) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.LIGHT -> false
        ThemeOption.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = {
            // Text(fontSize = …) with no style inherits LocalTextStyle, so make
            // the family the default for every Text inside the theme.
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = GameFont)
            ) { content() }
        }
    )
}
