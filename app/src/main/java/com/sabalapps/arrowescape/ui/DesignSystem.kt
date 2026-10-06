package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shared visual language of the whole game: the numbers every screen measures
 * itself against, and the pieces they are all built from.
 *
 * ## The look
 *
 * Arrow Escape is a puzzle game with five painted worlds, and its menus should
 * feel like part of one of them rather than like a settings app. So the language
 * is *glass over artwork*: the world's picture sits behind everything (see
 * `MenuBackdrop`), type that rides straight on it is white with a soft shadow, and
 * everything that holds information is a rounded translucent surface — a white
 * card in the light theme, a deep navy one in the dark — with a bright edge, a
 * faint top highlight and a broad soft shadow. Gradients are restrained and used
 * for exactly three jobs: the primary call to action, a badge, and a progress bar.
 *
 * Glass is *simulated* — translucent fill, gradient, border, shadow. There is no
 * runtime blur anywhere in the app, because it costs more than everything else on
 * screen put together and buys nothing a translucent surface does not.
 *
 * ## The scale
 *
 * Small on purpose: a handful of radii, spacings and elevations a person can hold
 * in their head. A token for every measurement would only be the same
 * inconsistency with longer names. Touch targets are never below
 * [AppIcon.touch], whatever the visible shape is.
 */

/** Widest a menu card is allowed to get, so a tablet gets cards and not banners. */
val MAX_CONTENT_WIDTH = 460.dp

/**
 * The game's palette, beyond the Material scheme.
 *
 * Indigo/electric blue is the brand, teal its partner, warm gold the reward colour
 * (stars, the Daily, a Perfect Escape), coral the one warm note that is not an
 * error, purple the night worlds. These are *fixed* colours — they do not flip in
 * the dark theme — because they belong to the game's objects (a gold tile is gold
 * in both themes), not to the page they sit on.
 */
object GamePalette {
    val ElectricBlue = Color(0xFF3E7BFA)
    val BlueTop = Color(0xFF4F8CFF)
    val BlueBottom = Color(0xFF2B3FD3)
    val Indigo = Color(0xFF3F51D5)

    val Navy = Color(0xFF0A0E2B)
    val NavyLift = Color(0xFF1C2563)

    val Teal = Color(0xFF14C4B4)
    val TealDeep = Color(0xFF089A98)
    val Cyan = Color(0xFF5BE3F0)

    val Gold = Color(0xFFFFC53D)
    val GoldLight = Color(0xFFFFE28A)
    val GoldDeep = Color(0xFFF59E1B)
    val Cream = Color(0xFFFFF6DC)
    val Brown = Color(0xFF3A2400)

    val Coral = Color(0xFFFF7A66)
    val CoralDeep = Color(0xFFE2463C)

    val Purple = Color(0xFF9A86F0)
    val PurpleDeep = Color(0xFF5D4BD0)

    val Green = Color(0xFF28B873)
    val GreenDeep = Color(0xFF16894F)
}

/** The gradients the palette is used as. All vertical, light at the top. */
object GameBrush {
    val Blue = Brush.verticalGradient(listOf(GamePalette.BlueTop, GamePalette.BlueBottom))
    val Teal = Brush.verticalGradient(listOf(Color(0xFF3FE0CF), GamePalette.TealDeep))
    val Gold = Brush.verticalGradient(listOf(GamePalette.GoldLight, GamePalette.GoldDeep))
    val Orange = Brush.verticalGradient(listOf(Color(0xFFFFB347), Color(0xFFF2711C)))
    val Coral = Brush.verticalGradient(listOf(GamePalette.Coral, GamePalette.CoralDeep))
    val Purple = Brush.verticalGradient(listOf(GamePalette.Purple, GamePalette.PurpleDeep))
    val Green = Brush.verticalGradient(listOf(Color(0xFF45D68F), GamePalette.GreenDeep))
    val Navy = Brush.verticalGradient(
        listOf(GamePalette.NavyLift.copy(alpha = 0.80f), GamePalette.Navy.copy(alpha = 0.78f))
    )
}

/**
 * One corner-radius family. Each step is a *role*, not a size: two things with
 * the same job round the same amount on every screen.
 */
object AppRadius {
    /** The big feature surfaces: Continue, the result panel. */
    val hero = 28.dp

    /** Cards and panels — anything holding a group of things. */
    val card = 24.dp

    /** A square that holds one glyph on a card. */
    val tile = 20.dp

    /** A level tile: small enough that this reads as a soft squircle. */
    val levelTile = 16.dp

    /** Buttons and anything button-shaped. */
    val control = 20.dp

    /** Small rectangular badges. Pills use a fully rounded shape instead. */
    val chip = 12.dp

    /** The floating result sheet. */
    val dialog = 30.dp
}

/** The spacing scale. Vertical rhythm on a menu is 8 / 12 / 20. */
object AppSpace {
    val screenH = 18.dp
    val screenV = 14.dp

    /** Inside a card, on every edge. */
    val card = 16.dp

    /** Between two cards in the same group. */
    val betweenCards = 12.dp

    /** Between one group and the next. */
    val betweenSections = 20.dp

    /** Between a label and the thing it labels. */
    val tight = 8.dp

    /** Between two lines of the same block. */
    val hair = 4.dp
}

/** Icon and control sizes. Few, so icons from different screens cannot disagree. */
object AppIcon {
    val small = 16.dp
    val medium = 20.dp
    val badge = 26.dp

    /** The rounded square a glyph sits in on a card. */
    val badgeBox = 52.dp

    /** The minimum anything tappable may be. Never reduce this for layout. */
    val touch = 48.dp

    /** The visible disc of a round control. Its touch target is still [touch]. */
    val control = 42.dp
}

/** Heights of the things you press. */
object AppSize {
    val cta = 58.dp
    val secondary = 50.dp
    val navButton = 54.dp
}

/** Elevation, by how much a surface wants to be noticed. Shadows are broad and soft. */
object AppElevation {
    val card = 6.dp
    val raised = 10.dp
    val hero = 16.dp
}

/** Border weights. A border is a hairline unless it means something. */
object AppStroke {
    val hairline = 1.dp
    val emphasis = 1.5.dp

    /** The ring around the current level. */
    val ring = 2.5.dp
}

/** How strongly each treatment reads, pulled out so the same idea is the same number. */
object AppAlpha {
    /** A quiet edge on a plain card. */
    const val OUTLINE = 0.10f

    /** An edge that carries an accent and is meant to be seen. */
    const val ACCENT_OUTLINE = 0.45f

    /** Secondary body copy. */
    const val MUTED = 0.70f

    /** Captions and units — present, but never competing with a number. */
    const val FAINT = 0.56f

    /** A glyph badge's tinted backing. */
    const val BADGE = 0.14f

    /** Light glass over artwork, light theme. */
    const val GLASS_LIGHT = 0.94f

    /** Light glass over artwork, dark theme. */
    const val GLASS_LIGHT_DARK = 0.90f
}

/** True when the active colour scheme is the dark one. */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Type that sits straight on artwork carries this, so it holds up on every world. */
val OnArtShadow = Shadow(
    color = Color(0x99000000),
    offset = Offset(0f, 3f),
    blurRadius = 10f
)

/**
 * The few text styles that Material's scale does not already name. Everything
 * else on a menu uses `MaterialTheme.typography`, whose weights `Theme.kt` sets.
 */
object AppText {
    /** The brand title on Home. */
    val brand = TextStyle(
        fontFamily = GameFont,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp
    )

    /** A screen's title in its top bar. */
    val screenTitle = TextStyle(
        fontFamily = GameFont,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.ExtraBold
    )

    /** The headline of a result: "Level Complete!". */
    val resultTitle = TextStyle(
        fontFamily = GameFont,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold
    )

    /** The name on a card. */
    val cardTitle = TextStyle(
        fontFamily = GameFont,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold
    )

    /** The one big figure on a stat card. */
    val number = TextStyle(
        fontFamily = GameFont,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold
    )

    /** The label on a main call to action. */
    val cta = TextStyle(
        fontFamily = GameFont,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.3.sp
    )

    /** The label on a secondary button. */
    val button = TextStyle(
        fontFamily = GameFont,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    )

    /** A tracked uppercase section heading. */
    val section = TextStyle(
        fontFamily = GameFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp
    )

    /** Small supporting copy. */
    val caption = TextStyle(
        fontFamily = GameFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/** White, shadowed: the style for type that has no card behind it. */
fun TextStyle.onArt(): TextStyle = copy(color = Color.White, shadow = OnArtShadow)

// -------------------------------------------------------------------------
// Surfaces
// -------------------------------------------------------------------------

private val ShadowTint = Color(0xFF060A24)

/**
 * A broad, soft, navy-tinted shadow. Material's default shadow is a harsh grey
 * that reads as a Material card; tinting it with the game's own shadow colour is
 * most of why a card here looks like it is floating in the scene instead.
 * (The colours apply from API 28; earlier versions fall back to the platform's.)
 */
fun Modifier.softShadow(elevation: Dp, shape: Shape): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = ShadowTint.copy(alpha = 0.50f),
    spotColor = ShadowTint.copy(alpha = 0.70f)
)

/**
 * Makes a surface pressable the way a game button is: it dips to
 * [pressedScale] in ~90ms and springs back, instead of flashing a ripple.
 *
 * The scale is a `graphicsLayer` read inside its block, so a press never
 * recomposes anything. It is applied *first* in the chain on purpose: it then
 * scales the shadow, the fill and the border as one object.
 */
@Composable
fun Modifier.gameClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedScale: Float = 0.96f,
    onClickLabel: String? = null
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 150),
        label = "press"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

/** What kind of surface a [GameGlassCard] is. */
enum class GlassTone {
    /** A white card in the light theme, a deep surface in the dark. The default. */
    Light,

    /** Deep translucent navy: the HUD, the result panel, nav pills. White content. */
    Dark,

    /** Warm cream-gold: the Daily Challenge. Dark brown content in both themes. */
    Gold
}

/** The fill, edge and text colours of a [GlassTone]. */
@Immutable
class GlassStyle(
    val fill: Brush,
    val border: Color,
    val content: Color,
    val muted: Color,
    val faint: Color
)

@Composable
fun glassStyle(tone: GlassTone): GlassStyle {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    return when (tone) {
        GlassTone.Light -> {
            val alpha = if (dark) AppAlpha.GLASS_LIGHT_DARK else AppAlpha.GLASS_LIGHT
            val top = lerp(scheme.surface, Color.White, if (dark) 0.07f else 0.55f)
            GlassStyle(
                fill = Brush.verticalGradient(
                    listOf(top.copy(alpha = alpha), scheme.surface.copy(alpha = alpha))
                ),
                border = Color.White.copy(alpha = if (dark) 0.14f else 0.85f),
                content = scheme.onSurface,
                muted = scheme.onSurface.copy(alpha = AppAlpha.MUTED),
                faint = scheme.onSurface.copy(alpha = AppAlpha.FAINT)
            )
        }

        GlassTone.Dark -> GlassStyle(
            fill = GameBrush.Navy,
            border = Color.White.copy(alpha = 0.18f),
            content = Color.White,
            muted = Color.White.copy(alpha = 0.78f),
            faint = Color.White.copy(alpha = 0.58f)
        )

        GlassTone.Gold -> GlassStyle(
            fill = Brush.linearGradient(listOf(GamePalette.Cream, Color(0xFFFFE19A))),
            border = GamePalette.GoldDeep.copy(alpha = 0.60f),
            content = GamePalette.Brown,
            muted = GamePalette.Brown.copy(alpha = 0.74f),
            faint = GamePalette.Brown.copy(alpha = 0.58f)
        )
    }
}

/**
 * The one card shell. Every panel on every menu is this, so "a card" means one
 * thing: a translucent gradient surface, a bright 1dp edge, and a broad shadow
 * proportional to how much it wants to be pressed.
 *
 * @param onClick makes the whole card the tap target rather than putting a button
 *   inside it, which is what turns a row into a game card.
 * @param contentDescription the card's single merged label. Required when
 *   [onClick] is set, because a card the player can press must announce what
 *   pressing it does.
 */
@Composable
fun GameGlassCard(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.Light,
    shape: Shape = RoundedCornerShape(AppRadius.card),
    borderColor: Color? = null,
    elevation: Dp = AppElevation.card,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = glassStyle(tone)
    Column(
        modifier = modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.gameClickable(onClick = onClick, pressedScale = 0.975f)
                } else {
                    Modifier
                }
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                }
            )
            .softShadow(elevation, shape)
            .clip(shape)
            .background(style.fill)
            .border(AppStroke.hairline, borderColor ?: style.border, shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides style.content) {
            content()
        }
    }
}

/** The disc a round control sits on: dark glass, bright edge, soft shadow. */
@Composable
private fun GlassDisc(size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .softShadow(5.dp, CircleShape)
            .clip(CircleShape)
            .background(GameBrush.Navy)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * A round game button: a [AppIcon.control] disc of dark glass inside a full
 * [AppIcon.touch] target. The label goes on the node that can be activated, not on
 * the icon inside it.
 */
@Composable
fun GameIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    discSize: Dp = AppIcon.control,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(AppIcon.touch)
            .gameClickable(onClick = onClick, pressedScale = 0.92f)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        GlassDisc(size = discSize, content = content)
    }
}

/** The back arrow, in the glyph and tint every [GameIconButton] back uses. */
@Composable
fun BackGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier.size(AppIcon.medium)
    )
}

/**
 * A screen's title, with the back button that leaves it.
 *
 * Every menu that is not Home uses this, so "where am I and how do I get out" is in
 * the same place and the same size on each. The title is centred between the
 * back button and the optional [trailing] content, and rides on the artwork as
 * white shadowed type.
 */
@Composable
fun GameTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back to home",
    trailing: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .widthIn(max = MAX_CONTENT_WIDTH + 60.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        GameIconButton(
            onClick = onBack,
            label = backLabel,
            modifier = Modifier.align(Alignment.CenterStart)
        ) { BackGlyph() }
        Text(
            text = title,
            style = AppText.screenTitle.onArt(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 66.dp)
                .semantics { heading() }
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd), content = trailing)
    }
}

/**
 * The small tracked-uppercase label over a group of cards, riding on the artwork.
 * A `heading()` for TalkBack, which is how the screens are navigable by section.
 */
@Composable
fun GameSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    dot: Color? = null
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            )
            Spacer(Modifier.width(AppSpace.tight))
        }
        Text(
            text = text.uppercase(),
            style = AppText.section.copy(
                color = Color.White.copy(alpha = 0.92f),
                shadow = OnArtShadow
            )
        )
    }
}

// -------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------

/** The two fills a main call to action comes in. */
enum class ActionTone { Primary, Coral }

/**
 * The main action: a large, rounded, glowing gradient button. There is never more
 * than one of these in view — that is the whole of the button hierarchy.
 * [GamePrimaryButton] is what the player most likely wants, [GameSecondaryButton]
 * is the alternative, and a text action is navigation.
 *
 * Blue for going forward ([ActionTone.Primary]); coral only for Retry after a
 * loss ([ActionTone.Coral]) — warm and inviting, not an emergency red.
 */
@Composable
fun GamePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Primary,
    icon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppRadius.control)
    val brush = if (tone == ActionTone.Primary) GameBrush.Blue else GameBrush.Coral
    val glow = if (tone == ActionTone.Primary) GamePalette.ElectricBlue else GamePalette.Coral
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSize.cta)
            .gameClickable(onClick = onClick, pressedScale = 0.96f)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = glow.copy(alpha = 0.45f),
                spotColor = glow.copy(alpha = 0.85f)
            )
            .clip(shape)
            .background(brush)
            .border(AppStroke.emphasis, Color.White.copy(alpha = 0.42f), shape),
        contentAlignment = Alignment.Center
    ) {
        // The sheen: a lit upper half, which is what makes the fill read as a
        // raised, glossy button and not a flat rectangle.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.5f to Color.White.copy(alpha = 0.04f),
                        1f to Color.Transparent
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = AppText.cta.copy(color = Color.White, shadow = Shadow(
                    color = Color(0x55000000), offset = Offset(0f, 2f), blurRadius = 4f
                )),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The alternative action: translucent glass with a bright edge, clearly lower in
 * the hierarchy than [GamePrimaryButton]. Made for dark scrims and artwork (the
 * result screens, Home); pass [onArt] = false for the rare button on a light card.
 */
@Composable
fun GameSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArt: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppRadius.control)
    val scheme = MaterialTheme.colorScheme
    val content = if (onArt) Color.White else scheme.primary
    Box(
        modifier = modifier
            .heightIn(min = AppSize.secondary)
            .gameClickable(onClick = onClick, pressedScale = 0.96f)
            .clip(shape)
            .background(
                if (onArt) {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.10f))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(scheme.primary.copy(alpha = 0.10f), scheme.primary.copy(alpha = 0.06f))
                    )
                }
            )
            .border(
                AppStroke.emphasis,
                if (onArt) Color.White.copy(alpha = 0.50f) else scheme.primary.copy(alpha = 0.40f),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides content) {
                if (icon != null) {
                    icon()
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = label,
                    style = AppText.button.copy(color = content),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Badges, chips, bars
// -------------------------------------------------------------------------

/**
 * The rounded square a glyph sits in on a card: a gradient, a bright edge, a lit
 * upper half. It exists so every icon on every menu is the same size in the same
 * kind of box, which is most of what "icon consistency" is.
 */
@Composable
fun GameBadge(
    brush: Brush,
    modifier: Modifier = Modifier,
    size: Dp = AppIcon.badgeBox,
    shape: Shape = RoundedCornerShape(AppRadius.tile - 4.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .softShadow(3.dp, shape)
            .clip(shape)
            .background(brush)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        0.55f to Color.Transparent
                    )
                )
        )
        content()
    }
}

/**
 * A small pill: a difficulty tier, a streak, a count. Fully rounded, because a
 * chip is a status and a card is a place.
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.8.sp,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Gold, a star and a number: how many stars the player holds. */
@Composable
fun StarPill(count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = "$count stars earned" }
            .softShadow(5.dp, shape)
            .clip(shape)
            .background(GameBrush.Gold)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.65f), shape)
            .padding(start = 10.dp, end = 13.dp)
            .heightIn(min = 34.dp)
    ) {
        StarGlyph(
            fill = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF0B8))),
            outline = GamePalette.Brown.copy(alpha = 0.35f),
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$count",
            style = AppText.button.copy(
                color = GamePalette.Brown,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}

/**
 * A rounded progress bar. [fraction] is the fill; it eases to a new value rather
 * than jumping, and on entry it grows in from empty — one short tween, which is
 * the whole of the "progress feels earned" effect.
 *
 * Decorative: the number it illustrates is always written beside it, so it
 * contributes no semantics of its own.
 *
 * @param animated false to draw the value as-is (reduced motion, or a bar the
 *   caller animates itself).
 */
@Composable
fun GameProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
    fill: Brush = Brush.horizontalGradient(listOf(GamePalette.Cyan, GamePalette.ElectricBlue)),
    trackColor: Color = Color.White.copy(alpha = 0.24f),
    animated: Boolean = true
) {
    val reducedMotion = rememberReducedMotion()
    val animate = animated && !reducedMotion
    var started by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(if (animate) 520 else 0, easing = FastOutSlowInEasing),
        label = "bar"
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
            .clearAndSetSemantics {}
    ) {
        if (shown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown)
                    .widthIn(min = height)
                    .clip(shape)
                    .background(fill)
            ) {
                // A bright line along the top edge: the fill catches the light.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height / 2)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.40f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

/** The hairline between two rows inside one card. */
@Composable
fun GameDivider(modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = 0.10f)) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppStroke.hairline)
            .background(color)
    )
}

/** A world's name in a pill with a dot in its accent. */
@Composable
fun WorldChip(name: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = AppText.caption.copy(color = Color.White, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
