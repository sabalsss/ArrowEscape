# Arrow Escape — Current State Report

**Audience:** an agent planning the remaining development.
**As of:** Discovery System Phases 4 + 5 + 6 (§4f — the reveal, the Discoveries album, and Home / Level
Select / Stats rebuilt around exploration). Phase 3 (§4e) draws a Campaign board as a shape; Phase 2 (§4d, §6)
turned the 30 Campaign layouts into discovery silhouettes. The test count is in §16 (re-measured at the end
of this work; generator stress numbers in §7 are from Phase 5). **Nothing in §4f has been seen on a device**
— it was compiled and unit-tested only; expect a visual polish pass. **§4g (final UI polish, four targeted
presentation fixes from a device review) supersedes §4f where they differ** — Home's order, the result's
backdrop, the gold level tile, the HUD's progress row. **§4i (engagement polish) adds the closing build-up,
the World Complete / Campaign Complete beat and the pip rows** — presentation only, no layout changed.
**§4k (the shape-discovery system) is the newest layer and supersedes older sections where they differ:** every mode is
now a *mystery shape* — the arrows are the clue, and finishing a board traces the outline of the puzzle's own occupied
cells before the reveal. A `shape/` package (`ShapeMask`, `GridContourTracer`, the 27-picture `MysteryShapes` catalogue,
`ShapePuzzleGenerator`), five Campaign silhouettes redrawn (`Levels.LAYOUT_VERSION` **4**), **Daily and Endless are
shape-first** (`DailyChallenge.GENERATOR_VERSION` **2**, `MysteryShapePuzzles.GENERATOR_VERSION` **1**), and the win flow
is one clock (`CompletionFlow`). **Nothing in §4k has been seen on a device** — it is compiled and JVM-tested only (737
tests).
**§4l (retention, onboarding & launch experience) is the newest layer** — a daily local reminder, the Level 1/2 hand-guided
lessons, respectful Rate/Share prompts and the animated loading screen. It touches no puzzle rule, layout, generator,
discovery artwork or world background. **Nothing in §4l has been seen on a device** — compiled and JVM-tested only (914
tests); §4l ends with the list of things to look at first.
**Not a roadmap.** This describes what exists, what half-exists, and what does not.

---

## 1. The game in one paragraph

A grid holds arrows, each pointing up/down/left/right. Tap an arrow and it flies off the board —
but only if **no other arrow stands anywhere on the straight line between it and the board edge**
in the direction it points. Tapping a blocked arrow costs one of three lives. Clear every arrow to
win. That is the entire rule set; there are no power-ups, timers, scores or combos.

The interesting consequence, which the whole codebase leans on: **removing an arrow can never
create a blocker.** The set of escapable arrows is therefore monotone, so greedy "remove everything
currently free, repeat" is an *exact* solvability test in O(n²) with no search. This is why the
generator needs no solver and why production can afford to run solvability checks.

---

## 2. Build & environment

| | |
|---|---|
| Package | `com.sabalapps.arrowescape` |
| Language | Kotlin 2.4.20, 100% Jetpack Compose, no XML layouts |
| minSdk / targetSdk / compileSdk | **23** / 37 / 37 |
| AGP | 9.2.1, Compose BOM 2026.09.00 |
| Architecture | single Activity, single ViewModel, no DI framework, no navigation library |
| Dependencies | core-ktx, activity-compose, compose ui/foundation/material3/material-icons-core, lifecycle-runtime-compose, lifecycle-viewmodel-compose, junit — plus three added in §4l: **`androidx.core:core-splashscreen` 1.2.0** (the system splash, one theme on every API), **`androidx.work:work-runtime-ktx` 2.11.2** (the daily reminder), **`com.google.android.play:review` 2.0.2** (the in-app review flow). No Room, DataStore, Hilt, Retrofit, Firebase, coroutine-test, Robolectric, mockk. |
| Orientation | portrait-locked; `configChanges` keeps the Activity alive through size/fold events |
| JDK for builds | `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (no system Java on this machine) |
| Source size | 17,655 lines main / 10,533 lines test (Kotlin, measured after §4f) |

**minSdk 23 is load-bearing.** `java.time` is API 26+, so the date layer is hand-rolled
(§7). Core library desugaring is **not** enabled. Turning it on would allow `LocalDate` but is
not currently needed.

Build/test commands:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest                        # 911 fast tests + 3 gated skips, ~25s
./gradlew testDebugUnitTest -Darrowescape.stress=1 # all 914, 5,000 boards in the stress tests
./gradlew assembleDebug
```
`-Darrowescape.stress` is forwarded into the test JVM by `app/build.gradle.kts` `testOptions`.
Without it the stress tests skip themselves via `Assume`.

---

## 3. Architecture

```
MainActivity ──> ArrowEscapeApp (nav)  ──> HomeScreen / GameScreen / LevelSelectScreen / StatsScreen
       │                                         │
       │  builds 3 repositories + settings       │ all read one shared GameViewModel
       ▼                                         ▼
ProgressRepository   EndlessRepository   DailyRepository   SettingsRepository
       └──────────── ProgressStore (interface) ──────────┘        (Context/SharedPreferences)
                     ├─ InMemoryProgressStore (tests)
                     └─ SharedPrefsProgressStore(name)
```

Key properties:

- **`GameViewModel` is the only ViewModel** and is constructible with no Android `Context`
  (`ProgressRepository(InMemoryProgressStore())` etc. are the defaults), which is why
  ~140 of the tests can drive real gameplay on a plain JVM.
- **Game rules live in `game/` and are pure Kotlin.** `MoveValidator`, `GameState`,
  `BoardAnalysis`, `HintEngine`, `PuzzleGenerator`, `PuzzleMetrics`, `GameDate`,
  `TutorialState` — none import Android or Compose.
- **Navigation is a `rememberSaveable` list of a 7-value enum** (`HOME, GAME, LEVEL_SELECT,
  STATS, SETTINGS, DAILY, DISCOVERIES`), saved as ordinals — so new destinations are only ever
  *appended* (`ui.ScreenNavigationTest` is the tripwire). The stack is never more than three deep. No
  navigation library and no args; the one "deep link" is the daily reminder, which only chooses the stack the app
  *starts* with (§4l). Transitions are chosen per move (§4c). **The loading screen is not a `Screen`** — it is above the
  whole app (`ui/AppRoot.kt`) and nothing can navigate back to it.
- **One screen renders all four game modes.** `GameMode` is a sealed interface; `GameScreen`
  branches on it only for the HUD heading, the difficulty badge and the result card.
- **Events** (`GameEvent`) are a `MutableSharedFlow` with `DROP_OLDEST`, consumed by a composable
  that owns `SoundPlayer`/`Haptics`, keeping the ViewModel free of Android types.

---

## 4. Screens — ALL FULLY IMPLEMENTED

> **§4f (the discovery experience) supersedes §4c where they differ** — Home, Level Select, the Campaign
> result and the Campaign card in Stats were all reworked around discoveries. Everything below is still
> accurate for Daily, Endless, Settings and the shared design system.
>
> **§4c supersedes the visual descriptions in §4 and §4b.** The behaviour described here (order of
> Home's destinations, locked-tile rules, result-card contexts, accessibility labels) is unchanged;
> the *presentation* was rebuilt in the Phase 7 redesign. `SettingsSheet` and `ResultDialog` no
> longer exist — they are `SettingsScreen` and `ResultScreen`.

### HomeScreen (`ui/HomeScreen.kt`) — restyled in Phase 6G, see §4b
Scrollable. Ordered by likely intent: **Continue** (filled, full width) → **Daily Challenge card**
→ **Modes card** (Campaign, Endless) → quiet row (Stats, Settings).
Daily card shows the title, `✓ Completed today` / `Play today's puzzle`, the friendly date
("Sunday, 4 October"), and a 🔥 streak count. It uses `secondaryContainer` when undone and steps
back to plain `surface` once done.

### GameScreen (`ui/GameScreen.kt`, 1,100 lines — the largest file)
HUD card: mode heading, difficulty badge, "N arrows left", 3 hearts, restart, settings, gradient
progress bar. Board below. Footer line + Hint pill.
Two responsive breakpoints, decided independently:
- `TIGHT_LAYOUT_BELOW` (width) — swaps roomy 18/12dp padding for 8/6dp so a 6-column board keeps
  48dp tap targets at 320dp.
- `SHORT_LAYOUT_BELOW = 640.dp` (height) — closes vertical gaps so a 9-row board fits.

### LevelSelectScreen (`ui/LevelSelectScreen.kt`) — restyled in Phase 6G, see §4b
4-column `LazyVerticalGrid`, auto-scrolls to the player's current level. Locked tiles are inert —
not merely rejected on tap, genuinely not clickable. Shows "N of 30 cleared".

### StatsScreen (`ui/StatsScreen.kt`) — new in Phase 5, restyled in Phase 6G (§4b)
Four cards: Campaign (x/30), Endless (cleared, current tier, best run), Daily (days, current
streak, best streak), Overall (total). Deliberately no charts.

### SettingsSheet (`ui/SettingsSheet.kt`) — regrouped in Phase 6G (§4b)
`ModalBottomSheet`, fully expanded and scrollable. Sound toggle, Haptics toggle, Theme segmented
control (System/Light/Dark), **Help → Replay Tutorial**. Rendered from both Home and GameScreen.

### ResultDialog (`ui/ResultDialog.kt`) — restyled in Phase 6G; behaviour and timings unchanged
One card, five contexts. Hand-drawn Compose badge (spring disc + self-drawing tick/cross via
`PathMeasure`).

| Context | Heading | Actions |
|---|---|---|
| Campaign | "Level Complete" / "Out of Lives" | Next Level · Replay · Level Select |
| Campaign, last level | "All Levels Complete" | Replay Level · Level Select |
| Endless | (§4k) "SHAPE REVEALED!" | Next Puzzle · Replay · Home |
| **Daily** | (§4k) "TODAY'S SHAPE REVEALED!" / "TODAY'S SHAPE" | Done · Play Again, **+ streak panel** |
| **Tutorial** | "That is the whole rule" | Done · Replay |

---

## 4b. Visual language — FULLY IMPLEMENTED (Phase 6G)

`ui/DesignSystem.kt` is the menus' shared vocabulary, and `ui/Theme.kt` now carries a real type
hierarchy. Before this phase every screen chose its own radii, paddings, card elevations and button
shapes — all close, none identical, which is exactly what makes an app read as "some Compose
screens" rather than as one product.

**Tokens.** Deliberately small, so the scale fits in your head: four radii (`card` 24 · `tile` 20 ·
`control` 18 · `chip` 10, plus `dialog` 28), six spacings, three elevations, three icon sizes, three
border weights, and five named alphas. `MAX_CONTENT_WIDTH` (460dp) caps every menu card.

**Shared pieces.** `MenuBackground`, `AppCard`, `ScreenHeader`, `SectionLabel`, `StatusChip`,
`GlyphBadge`, `ProgressTrack`, `StatHero`, `StatRow`, and the three-rung button hierarchy
`PrimaryAction` / `SecondaryAction` / `TertiaryAction` — the last three lifted out of `ResultDialog`,
which was the only place they existed.

**Typography.** Material 3's *sizes* were already right; what was missing was weight, so every
screen reached for a style and then added its own `fontWeight = Bold`. `AppTypography` keeps
Material's scale and fixes the weight per role, and the screens stopped overriding it. Six rungs:
brand title (`displaySmall`) · screen title and key number (`headlineMedium`) · card title
(`titleMedium`) · body (`bodyMedium`/`bodySmall`) · button (`labelLarge`) · tracked uppercase
section heading (`labelSmall`). Body stays regular weight on purpose — making everything semibold is
the fastest way to make nothing look important. `GameScreen` sets its own weights and tracking
locally and is unaffected; it is fitting a heading, a badge and three hearts onto one 320dp line.

**Button hierarchy**, one rule, applied everywhere: never more than one filled button in view. Home
descends Continue (filled gradient) → Daily (gold container) → the two mode cards (surface +
hairline) → Stats/Settings (text). The result card descends Next Level → Replay → Level Select.

**World consistency in the menus** is exactly one colour: `MenuBackground` warms the top of Home and
the level grid with the accent of the world the current campaign level is played in, read from
`GameWorlds`/`WorldStyle` like the game screen reads it. Two static gradients, nothing animated — a
menu's job is to be read. The alpha is low (0.07 / 0.11) because three of the five accents are warm
and a warm cast over a near-white menu reads as a dirty screen rather than as a world.

### What each screen gained

**Home** — a hero **Continue** card: gradient fill, play glyph, "Level 15 · Sunset Canyon" (the
world name is the part that changes, and it is what turns a bare level number into a place), and an
unlabelled progress track. The **Daily** card moved from teal to the gold it already owns everywhere
else, with the streak as a chip. **Campaign** and **Endless** became game cards — an `ArrowGlyph`
badge in the mode's accent (up for the campaign, which climbs; right for endless, which goes on),
title, one line of state, a chevron, and a progress track where the track has an end. Stats and
Settings stayed a quiet text row. Order is unchanged.

**Level Select** — grouped into the five **world sections**, each headed by the world's name, a dot
in its accent and a `cleared/total` count. The grouping asks `GameWorlds` which world a level is in
rather than restating the boundaries, so it cannot drift and a longer catalogue extends the last
section. Tiles gained a fourth state: **PERFECT** (three stars) is now a gold tile, so a Perfect
Escape is findable across thirty. Locked tiles sit flat with no shadow; unlocked, completed and
perfect are raised. One border by precedence — current ring beats perfect beats completed. The star
row is three glyphs rather than one string, which is what lets earned stars be gold and unearned a
ghost; the single-string version this replaced could only ever paint all three the same colour
despite its own KDoc saying otherwise. Header shows total stars earned. **Unlock logic, level order
and the locked-tiles-are-not-clickable rule are untouched.**

**Stats** — four cards that each lead with one headline figure and a caption, with the supporting
detail underneath at body size. Campaign leads with levels cleared and gained a **stars earned** row
(§3 of the phase brief named it); Endless with puzzles cleared; Daily with the current streak, which
is the number at stake tomorrow and the only one on the screen that can go down; Overall with the
total, in teal so it does not look like a fourth mode. Back moved into the header, where it is on
the level grid, instead of the bottom of a scroll. Still no charts — a count has no trend.

**Settings** — three labelled sections (Sound & feedback · Appearance · Help) with a hairline
between rows that belong together. Flat, no cards: four settings is not enough to need them, and
every ornament is height to scroll past at a large font scale. Still fully expanded and scrollable,
still 48dp minimum on every control, same four settings.

**ResultDialog** — unchanged in every behaviour and **every timing**: the badge springs, the
`STAR_REVEAL_MS` stagger, `PERFECT_DELAY_MS` and the Phase 6D celebration are all byte-identical.
What changed is the shell (shared radius, hairline edge, shared action buttons), the mode subtitle
becoming the same `StatusChip` the HUD badges a tier with — gold for a daily — and a `verticalScroll`
so a badge, a heading, three stars, a sentence and three buttons cannot clip at a large font scale.

### `PlayerStats` gained two values

`campaignStars` (the sum of `PlayerProgress.bestStars`) and `campaignStarsMax`. **Not new counters:**
the stars are already on disk and already authoritative, so this is a sum of what is there. No
persistence format changed and nothing new is range-checked.

### Accessibility

Every existing contentDescription is preserved verbatim — "Continue, start Level N", "Campaign, N of
30 levels cleared", "Level N, completed, 2 of 3 stars", "Replay tutorial, …". New merged nodes were
added for the world headings and the star chip; decorative additions (the title mark, the streak
chip, the progress tracks, the tile star rows) are all `clearAndSetSemantics {}`. `heading()` is on
every screen title and section label. No touch target was reduced.

### Known visual issues

| | |
|---|---|
| Level grid's opening scroll lands a few percent short, so the bottom edge of the previous world's last row can peek in above the heading | cosmetic, gone on first scroll; scrolling again after a frame does not help, so the cause is the grid's index→line mapping with mixed span sizes, not a stale measurement |
| Crystal Night (`#8E7BE8`) and Cosmic (`#7E6BE0`) section dots are near-identical | the accents are Phase 6A data that also tint the board and the menu wash; changing them is a 6A decision, not a polish one |
| Verified on one emulator, light theme, default font scale | dark theme and large-font layouts are argued from the tokens, not observed |

## 4c. Premium UI redesign — IMPLEMENTED (Phase 7, presentation only)

A complete rebuild of the presentation layer toward a game-UI look: world artwork behind every
screen, glass surfaces, a button hierarchy, and a gameplay HUD of small floating controls. **No
game rule, generator, save format, level, world mapping, timing or sound was touched.** The only
non-UI edits: `GameViewModel.hintUsedOnBoard` (a read-only getter over the flag the star rule already
reads) and `MainActivity` (system-bar icons are always light, because every screen sits on a
dark-scrimmed picture whichever theme is chosen).

**Not verified on a device in this phase** — written and compiled only. Expect a polish pass.

**Language.** White type with a soft shadow where it rides straight on artwork; everything that
holds information is a translucent rounded surface (`GameGlassCard`: `Light` = white card / deep
card in the dark theme, `Dark` = navy glass for HUD, nav pills and the result panel, `Gold` = the
Daily). Glass is *simulated* — gradient fill, 1dp edge, navy-tinted `softShadow` — with no runtime
blur. Gradients do three jobs only: the primary CTA, badges, progress bars. Fixed palette in
`GamePalette`/`GameBrush` (electric blue, teal, gold, coral, purple, green) — these belong to game
objects and do not flip in the dark theme.

**Tokens** (`DesignSystem.kt`): radii hero 28 · card 24 · tile 20 · levelTile 16 · control 20 · chip
12 · dialog 30; `AppSpace`, `AppIcon` (touch 48, round-control disc 42), `AppSize` (cta 58),
`AppElevation`, `AppStroke`, `AppAlpha`, and `AppText` for the few styles Material's scale lacks.

**Shared pieces:** `GameGlassCard` · `GamePrimaryButton` (blue; `ActionTone.Coral` only for Retry) ·
`GameSecondaryButton` · `GameIconButton` · `GameTopBar` · `GameSectionHeader` · `GameProgressBar` ·
`GameBadge` · `StatusChip` · `StarPill` · `WorldChip` · `GameDivider` · `Modifier.gameClickable`
(90ms dip to 0.96, no ripple) · `Modifier.softShadow`. Glyphs in `GameIcons.kt` are drawn on
Canvas (core Material icons lack chart/speaker/book/infinity/flame): bar chart, speaker, vibrate,
sun, moon, system-theme, book, infinity, mountain, grid, flame, no-entry, star.

**Artwork in menus.** `MenuBackdrop(world)` = the world's WebP, a navy scrim (per-world strength,
+ in the dark theme) and an accent glow. Menus decode a **down-sampled** copy (`WorldArt.kt`:
`BitmapFactory.inSampleSize`, off the main thread, ~20MB byte-bounded `LruCache`) — five full
1440×2560 decodes behind a level grid would be ~74MB. Only Home runs the existing ambience
(`ambient = true`; drift layers + motes reused unchanged, nothing when reduced motion is on); every
other menu is static.

**Screens.**
- *Home* — brand mark of four arrow discs, "Arrow Escape", then Continue (deep-blue hero card with the
  world thumbnail fading in from the right, big Play disc, level · world, progress bar) → Daily (gold
  ticket: calendar badge with a green check that springs in once done, date, streak chip) → "Game
  Modes" (Campaign / Endless glass cards with gradient badges) → **Stats / Settings pinned to the
  bottom** as dark glass buttons. Content is centred in the room above them and scrolls if it must.
- *Level Select* — header (back, title, gold star pill), "N of 30 cleared" + bar, then a
  `LazyColumn` of **world chapter bands**, each with its own artwork (drained of colour and dimmed
  for a world not yet reached), heading, `cleared/total`, and six tiles to a row. Tiles: gold (perfect),
  blue (cleared, earned/ghost stars), lit white with an electric ring that breathes slowly (current,
  untried; steady under reduced motion), light with a world-coloured edge (open), dark glass with a
  padlock (locked — still `semantics { disabled() }`, no click). A tile's touch target is its whole
  grid cell and a cell is never under 48dp (screen/band padding give way below 360dp, not the tiles).
  The opening scroll is now exact (one item per world), which also fixes the old "lands a few percent
  short" nit.
- *Stats* — "Statistics" over the world; 2×2 glass cards (stacked below 340dp), each a badge, a
  38sp figure and supporting lines: Campaign (n / 30, stars), Endless (cleared, current tier, best
  run), Daily (current streak, best streak, ✓ Completed Today), Overall (total). The reference's
  "Highest tier" is **not shown** — the game does not track it.
- *Settings* — now a **screen** (`Screen.SETTINGS`), from Home and from the HUD; back returns to
  whichever it came from. Same four settings. Each switch row is one `toggleable` whole-row target;
  theme is three icon choices that spring when selected; Replay Tutorial keeps its old label.
- *Daily summary* — `Screen.DAILY`: Home's Daily card opens it **only once today is done**
  (undone → straight into the board as before). Calendar + check badge, "Completed Today", date,
  current/best streak, Play Again (→ `startDaily()`, stack reset to Home→Game) and Home. A restored
  back stack from an earlier day bounces to Home rather than describing the new day as done.
- *Result* (`ResultScreen`, replaces `ResultDialog`) — a **full-screen layer inside `GameScreen`**,
  not a dialog window: deep scrim over the live world, entrance = fade + 36dp rise + 0.94→1 scale
  over 300ms (120ms under reduced motion). The board layer leaves the accessibility tree while it is
  up, taps are swallowed, and back behaves as before (Home where there is no grid, nothing
  otherwise). Win: glowing headline, three big stars (middle larger and raised, outer two tilted,
  staggered spring-in — `STAR_REVEAL_MS` logic carried over), a swallow-tailed gold **PERFECT ESCAPE
  ribbon** for three stars, "New best"/"Best ★★★ kept" pill, a dark glass summary panel, one blue
  Next Level, Replay + Level Select/Home quieter beneath. Loss: darker, three dimmed hearts,
  same panel, coral Retry, Level Select + Home (Campaign) or Home. A **Daily win** uses the same
  completion layout as the daily summary.
  *Panel rows, and only what is reliable:* Stars earned (Campaign win) · Arrows cleared (`total`, or
  `n / total` on a loss) · Blocked taps (= lives lost; each blocked tap costs exactly one) · Hint used
  (**Campaign only** — a yes/no, because the game tracks a flag, not a count, and persists it only in
  Campaign). No moves, score or time: none is tracked.
- *Gameplay* — see below.

**Gameplay HUD** (`GameHud.kt`): back · level pill (name over world, or tier for Endless/Daily —
Daily in gold) · hearts pill · restart · settings, all dark glass, each round control a 48dp target
around a 42dp disc; a slim progress bar with "N left" beneath. The old full-width card is gone. At
**< 400dp** the hearts move from the top row to beside the progress bar (otherwise "Endless #51"
ellipsises at 320dp). TalkBack labels are carried over ("N of M arrows remaining", "N of 3 lives
remaining", "Restart level", "Settings"; Back is now "Back"). Footer: a light-glass instruction pill
with a gold bulb, and a violet **Hint** pill (logic untouched; disabled dims). Board: richer world
tint (`BOARD_TINT` 0.12), light-from-above sheen, bright rim fading to the world accent, inner
keyline, broad soft shadow, slightly more visible empty cells. Arrow tiles, VFX, ambience, celebration
timing and the in-game background are unchanged.
*(Since Discovery Phase 3 that board is what Endless and Daily draw; the Campaign draws a shape with no
board at all — §4e.)*

**Transitions** (`screenTransition`, 150–300ms): into a board = fade + zoom up from 0.94; out of
a board = fade + slight scale-out; between menus = horizontal slide in the direction of travel +
fade; reduced motion = a 120ms cross-fade. Microinteractions: press-dip on every card/button/tile,
the current tile's slow breath, the daily check's spring, theme-choice spring, progress bars grow in
(and ease between values), stars/ribbon/hearts spring on the result.

**Design compromises:** level tiles are 16dp-radius (not 18–22) because at ~46dp a bigger radius
turns them into circles; Settings loses the reference's decorative cog; the Hint button has no
badge (nothing to badge); status-bar icons are always light, even in the light theme.

## 4d. Campaign discoveries — catalogue (Phase 1) and silhouette puzzles (Phase 2)

Product direction: the Campaign becomes SOLVE → REVEAL → DISCOVER → COLLECT → EXPLORE — each level
hides one animal/object/treasure. **Phase 1 was data only: no screen, grid cell, animation, artwork or
`PlayerStats` field read the catalogue.** (Phases 4–6, §4f, are what finally read it.)

**Phase 2 changed the puzzle content, and only that:** the 30 Campaign layouts in `Levels.ALL` are now
the *silhouettes* of their discoveries — see §6. Presentation was untouched in Phase 2 (hiding the empty
cells and drawing the silhouette was Phase 3 — §4e). Phase 2 also renamed Crystal's art key
`crystal_crystal` → **`crystal_cluster`** (nothing consumed it); `CampaignDiscoveriesTest` now pins all
30 keys by name.

`ui/world/CampaignDiscovery.kt` (pure Kotlin, beside `GameWorlds`):
- `CampaignDiscovery(levelId, name, world, type, artKey)` and `DiscoveryType`
  (`NATURE, ANIMAL, OBJECT, TREASURE, SPACE` — a content category, read by nothing).
- `CampaignDiscoveries` — the one catalogue: 30 entries in level order, six per world.
  `all`, `forLevel(id)` (null for anything not a Campaign level, incl. `ENDLESS_LEVEL_ID`),
  `forWorld`, `totalCount(world)`, `isCollected`, `collectedCount(progress)` /
  `collectedCount(world, progress)`, `nextUndiscovered(progress)`.
- **`world` is derived** from `GameWorlds.forCampaignLevel` when the entry is built, so the
  discovery ranges cannot drift from the world ranges.
- `Level.name` is still "Level N"; discovery data is not on `Level` or `ArrowTile`.
- **Collected = `PlayerProgress.completedLevels` contains the level.** No new store, no second set
  of ids. `collectedCount` counts against the catalogue, so a stray id cannot inflate it. A later
  phase that must remember "first reveal already shown" owns that as separate state.
- `artKey` is `<world prefix>_<subject>` (`sky_`, `forest_`, `canyon_`, `crystal_`, `cosmic_`),
  e.g. `sky_cloud`, `canyon_treasure_chest`. Like level ids, keys must not change once shipped.
  `ui.discovery.DiscoveryArtRegistry` is the one thing that resolves a key to a drawing (§4f).

| Levels | World | Discoveries |
|---|---|---|
| 1–6 | Sky Garden | Cloud · Flower · Kite · Bird · Heart · Star |
| 7–12 | Forest | Leaf · Mushroom · Tree · Butterfly · Fox · Owl |
| 13–18 | Sunset Canyon | Sun · Cactus · Mountain · Canyon Arch · Eagle · Treasure Chest |
| 19–24 | Crystal Night | Gem · Crescent Moon · Crystal · Snowflake · Magic Star · Crown |
| 25–30 | Cosmic | Comet · Rocket · Planet · UFO · Satellite · Galaxy |

Guarded by `ui.world.CampaignDiscoveriesTest` (16): it also fails if `Levels.ALL` and the catalogue
ever disagree on which levels exist.

## 4e. Campaign shape presentation — IMPLEMENTED (Discovery Phase 3, presentation only; **since §4k every mode is drawn this way**)

A Campaign puzzle now reads as *a recognisable shape made of arrow pieces floating on the world*, not
as a rectangular board holding tiles. **Rendering only:** no layout, discovery, rule, save format,
`Levels.LAYOUT_VERSION` (still 2), world mapping or Endless/Daily behaviour changed, and the
discovery name is still not shown during play (the HUD says "Level N / World"; the reveal is §4f).
**Not verified on a device** — compiled, plus the pure `ui.*` JVM tests.

**One presentation per mode** (`ui/BoardPresentation.kt`, pure). `boardPresentationFor(mode)`:
`Campaign`, `Tutorial`, `Endless` **and** `Daily` → `Shape` (Endless/Daily were `Grid` until §4k). The replayed tutorial is a shape
because it plays Level 1's Cloud, the same board the first run shows as a real Campaign level.
`GameScreen` decides once and `Board` dispatches to `GridBoard` or `ShapeBoard`; nothing below asks
"which mode". Both place the same `ArrowCell`s through one `BoardPieces`, so taps, animations, VFX,
hint, semantics and sound are literally the same code.

**`GridBoard`** is the old board, unchanged: rounded translucent surface, sheen, keyline, `EmptyGrid`
slots, `GridPiece` (the original tile).

**`ShapeBoard`** draws no surface, no border, no keyline and **no slot for an empty cell** — an empty
cell is not composed at all, so it is invisible *and* inert (no invisible tap target exists). Margins,
gaps and meaningful holes (Owl eyes, Crown jewels, Rocket porthole, Chest gap) are all just nothing.
The only non-arrow thing drawn is the halo below.

**Occupied-bounds centring and scaling.** `OccupiedBounds.of(level.arrows)` is the tightest
rows × columns box around the level's *full* arrow list (never what is left, so the shape does not
re-centre or re-scale as it is dismantled; remaining arrows never reflow). The stage is everything
between the HUD and the footer; `ShapeFit.cellSize` picks the largest *square* cell that fits the
bounds in it, capped at `SHAPE_MAX_CELL = 60dp` (so an 8-arrow, 3-wide board is not blown up and every
level's pieces land at roughly 50–60dp on a phone), and the bounds box is centred in the stage. An
arrow sits `(row − minRow, col − minCol)` cells from the box's corner; its id, row, column and
TalkBack label ("Up pointing arrow, row 3, column 5") still use the level's own coordinates. Level 1
is trimmed from 4 rows to its 2 occupied ones; no empty row *inside* the bounds is ever cropped.
`TIGHT_`/`ROOMY_SHAPE_MARGIN` (4/10dp) keep air above and below the shape. Width stays the binding
limit for six-column shapes, so a 320dp phone still gets ≥ 48dp cells (asserted).

**Halo** (`ui/CampaignShape.kt`, `CampaignShapeHalo`). One `Canvas`: two concentric *elliptical* radial
gradients (a navy-deepened pool of the world accent under a plain accent glow, centre alphas 0.24 /
0.30) sized from the formation's own width/height plus 0.9 cell, held inside the stage and reaching
nothing at their rim — no edge, no rectangle, no blur, no per-frame allocation. It is **not** an
outline of the discovery and does not follow the cells. Its strength eases to 45% as the last arrow
leaves (`snap()` under reduced motion); otherwise static. Decorative — no semantics.

**Pieces.** `ShapePiece` = the same rounded-square tile, packed tighter (3.5dp inset, 14dp corner,
8dp glyph inset — the touch target is still the whole cell), finished as a physical piece: opaque
electric-blue → indigo body (`ShapePieceTop/Bottom`, from `GamePalette`, identical on every world and
in both themes), a top sheen and a small inner rim that fades white → a hint of the world accent
(`arrowPieceFinish`), a navy `softShadow`, and a white glyph. **Grid pieces are unchanged**: Endless
and Daily keep the theme-primary tile (which is light periwinkle with a dark glyph in the dark theme).
Hint teal border, blocked red flash/border, blocker pulse, impact ring, sparks, awaken halo, press
scale, wind-up, escape trail and launch sparks are untouched and apply to both styles.

**VFX coordinates.** `ArrowCell` takes `BoardMetrics` (cell size, board size in px, board origin).
For a shape the "board" an arrow flies off — flight length, `exitFraction`, the edge-accent position —
is the occupied-bounds box, drawn nowhere, so the accent flashes where the arrow itself crosses that
line. No escape rule changed; `ArrowVfx.kt` is untouched. `BoardCelebration` gained `framed`
(false for a shape): the completion glow is then a plain radial wash with no rounded-rect frame and no
edge stroke, which would otherwise have drawn the very rectangle this phase removes. The burst is the
same and the timings are unchanged.

**Tests.** `ui.BoardPresentationTest` (9): mode → presentation, occupied bounds tight for all 30
levels and Level 1 trimmed, cells square / within the room / capped, the 48dp floor at 320dp, the
tallest shape height-limited when short.

---

## 4f. The discovery experience — IMPLEMENTED (Discovery Phases 4 + 5 + 6)

**SOLVE → REVEAL → DISCOVER → COLLECT → EXPLORE.** Campaign is now the *discovery / exploration* mode:
clearing a level reveals what it hid, the album collects it, and Home and Level Select say how much is
left to find. **Endless and Daily have no discoveries and are untouched**, as are `MoveValidator`,
`GameState`, the generators, `StarRating`, the Campaign layouts, `Levels.LAYOUT_VERSION` (still 2) and the
discovery mapping. No new persisted state exists: a discovery is *collected* when its level is in
`PlayerProgress.completedLevels`, and nothing records that a reveal was "seen".
**Not verified on a device** — compiled and unit-tested only.

### One artwork system (`ui/discovery/`)

The 30 illustrations are **data** (SVG path strings in a 100 × 100 space) drawn by one renderer, so a
discovery has one identity wherever it appears — the reveal, the album, Level Select tiles, Home thumbnails.

| File | What it is |
|---|---|
| `DiscoveryArtSpec.kt` | pure: the model (`BodyLayer` = a silhouette filled across its union and edged once; `DetailLayer` = shine, markings, faces, sparkles), the `art { body(…) { +circle(…) }; details { face(…) } }` DSL and the primitives (`circle`, `ellipse`, `roundRect`, `poly`, `star`, `sparkle4`, `capsule`, `heart`) |
| `SkyDiscoveryArt.kt` … `CosmicDiscoveryArt.kt` | pure: the 30 drawings, six per world file |
| `DiscoveryArtRegistry.kt` | pure: **`artKey` → spec**, the only place that resolves one |
| `DiscoveryArtwork.kt` | Compose: `DiscoveryArtwork(artKey)` / `DiscoveryArtwork(discovery)` — parses each drawing once (shared cache), draws ring → ink → fill per body, then the details |
| `RevealSchedule.kt` | pure: the reveal's timeline (below) |
| `DiscoveryReveal.kt` | Compose: `DiscoveryGlow` (the two colours a world lights its discoveries with) and the halo + sparkle canvas |

**House style**, so thirty items read as one set: a two-tone vertical gradient fill, a deep-indigo ink edge and
a thin white "sticker" ring outside it (the ink holds on a light card, the ring on a dark scrim — checked on
dark, light and blue backdrops); chubby rounded shapes; one shared face (dot eyes, smile, blush) on anything
that has one; a twinkle or two. World colour is used only for the glow and sparkle accents, never the art.
Primitives wind clockwise — a body is several subpaths filled non-zero, and one wound the other way cuts a hole
where it overlaps its neighbour (that was a real bug in the first render). A translucent detail is never
`soften`ed (Skia would blend its fill and its softening stroke twice; the SVG sheet would not show it).

**How the 30 keys resolve.** `CampaignDiscovery.artKey` → `DiscoveryArtRegistry.specFor(key)`. `DiscoveryArtRegistryTest`
proves every catalogue key (and the thirty named in the brief) resolves, that the registry holds *exactly* the
catalogue's keys, that every path parses (`PathParser` runs on a plain JVM) and that nothing is drawn off the canvas.

**Review sheet (dev only).** `DiscoveryArtSheetWriterTest` writes `app/build/reports/discovery/discovery-art-{dark,light,blue}.svg`
— all 30 on three backdrops, drawn the way the app draws them. It asserts nothing about the art. Render one with
headless Chrome (`--screenshot`) to look at it; this is how the set was drawn and checked.

### The reveal

`GameViewModel.campaignDiscovery: StateFlow<DiscoveryResult?>` is published beside `campaignStars`, from the same
win, **after** `persistCampaign` has written the completion, the stars and the unlock — so the win is on disk the
instant the last arrow goes (`DiscoveryRevealFlowTest` kills the process mid-reveal and the clear survives) and
nothing the reveal does can lose it. `isFirstClear` is read *before* the write (`progress.progress.value.isCompleted`,
the same pattern as the previous best) and is true once per level; a replay says DISCOVERY FOUND. Null on a loss,
in Endless/Daily, for the replayed tutorial, and for a level that hides nothing.

`ui/world/DiscoveryState.kt` (pure) holds `DiscoveryResult` — the discovery, first-clear, the world's `x / 6`, the
global count, headline ("NEW DISCOVERY!" / "DISCOVERY FOUND"), collection title, progress label and one spoken
summary. Its counts always include the level just cleared: `DiscoveryResult.of` adds the level to the record
itself rather than trusting the caller to have committed first.

**Timeline** (`RevealSchedule.Standard`; launch = the last arrow starts to escape; ms):

| launch | beat |
|---|---|
| 0 | last arrow's own flight (370) |
| 380 | the result layer arrives; the scrim comes up over 420 |
| 450–900 | the art emerges: alpha, 0.90 → 1.00, and a glide up from the stage centre |
| 680–1060 | the world-coloured glow strengthens |
| **900** | **peak** — `LEVEL_COMPLETE` + `CELEBRATION` haptic, and the sparkle burst launches |
| 940 → 1180 | NEW DISCOVERY! → the name → the collection → the stars |
| 1400–1660 | the buttons fade in (pressable from 1500) |

About 1.6s. Reduced motion (`RevealSchedule.Reduced`) fades the art in, brings the words at once, throws no
sparkles, skips the scale and the glide, and has the buttons by ~480ms; sound and haptic still fire.
`RevealScheduleTest` pins the *shape* (orderings, peak in 900–1100, available in 1250–1700, total under 2s), not
the numbers.

**Who owns what.** `rememberWinCelebration(reveal = true)` only sequences what the layer cannot: it waits out the last
arrow's flight, lets the result layer in (`resultVisible`), and fires `onPeak` on the peak beat — **`onPeak` is
called there and nowhere else**, so the sound and haptic cannot fire twice and `GameFeedbackEffect` still plays
nothing for `LevelComplete`. No glow or burst is drawn over the board in this mode. The art, halo and sparkles live
inside the result layer (`DiscoveryResultContent`), driven by **one linear `Animatable` clock read only in draw
lambdas** (`graphicsLayer`, `Canvas`), so the sequence recomposes nothing per frame. The reveal flag is derived from
the board (`mode == Campaign` and the level is in the catalogue), not from the result having arrived, so it cannot
race the win. The glide runs between the stage centre (measured in `GameScreen`) and the art's slot, both on
untransformed anchors, and is zero if either is unknown.

**The result, in order of importance** (*discovery is what you found, stars are how well you solved it*):
headline → the art, large (≤ 240dp, sized from the room) → its name → "FOREST COLLECTION · 4 / 6 DISCOVERED" with six
pips and a gold WORLD COMPLETE / ALL DISCOVERIES FOUND note → the stars (the existing `BigStars` at 0.58 scale) →
PERFECT ESCAPE ribbon / New best note → **CONTINUE EXPLORING** (existing next-level behaviour) with Replay and
Level Select beneath. A Perfect Escape adds a gold halo and more sparkles (42 vs 26) — more of the same effect,
no second one; one star discovers exactly what three do. **Level 30** offers **VIEW DISCOVERIES** (→ the album)
instead of a button to a Level 31 that does not exist. The old summary panel (arrows / blocked taps / hint) is no
longer on a Campaign *win*; it is unchanged on a loss and in every other mode. The buttons swallow presses until
they have arrived, so a stray tap from the last arrow cannot skip the reveal. TalkBack gets one heading ("New
discovery. Butterfly. Forest collection, 4 of 6 discovered."), then the stars and the buttons; art, halo, pips and
sparkles are decoration.

### The album (`Screen.DISCOVERIES`, `ui/DiscoveriesScreen.kt`)

Home → Discoveries → back is Home; the last level's result → Discoveries replaces the game (back is Home); Level
Select's header also opens it (back returns to Level Select); a mystery in it can `startLevel` + push the game (back
returns to the album). Top bar "Discoveries"; a header card "14 / 30 FOUND" with a bar (gold, "ALL DISCOVERIES FOUND"
at 30/30 — the finished album is the reward; nothing to claim); then five world pages in the Level Select band style
(world artwork under a navy scrim, drained of colour until reached, a gold edge and COMPLETE chip when all six are
found). Six slots a world, **three across where the band is ≥ 300dp, two across below** — a 320dp phone gets bigger
cards, not smaller; rows equalised with `IntrinsicSize.Max`.

| Slot state | Looks | Says (TalkBack) | Tap |
|---|---|---|---|
| **Found** | pale sticker card, the real art on a pool of the world's colour, the name, best stars (small) | "Butterfly, discovered, Forest" | closer look: art 200dp, name, world chip, DISCOVERED, "Level 10", Close |
| **Mystery** (open, not cleared) | dashed card, "?", "Undiscovered", "Level 11" | "Undiscovered item, Level 11" | UNDISCOVERED / Level 11 / **Play Level** |
| **Locked** | dark card, padlock, "Locked" | "Locked discovery" | nothing — not clickable at all |

The closer look is re-resolved from the player's *current* record on every composition, so a saved selection that is
not a found discovery or an open level shows nothing; while it is up the album leaves the accessibility tree.

### Spoiler prevention — the load-bearing invariant

**Only `DiscoverySlot.Collected` carries a `CampaignDiscovery`.** `DiscoverySlot.Mystery` and `.Locked` hold a level
number and nothing else (`DiscoveryStateTest` checks by reflection that they have no `CampaignDiscovery` field), so a
screen handed one *cannot* draw its artwork, print its name or speak it — it is structural, not a convention a screen
has to remember. The spoken label lives on the slot, so there is exactly one place that decides what TalkBack may say
per state; a test renders every non-collected slot at every point in the Campaign (0..30 cleared) and fails if any
string it could show or say contains a catalogue name or art key. Level Select, the album and Home all build from
slots; `DiscoveryArtwork(discovery)` can only be called with an identity, and the only identities in hand are
`Collected`'s. The Home Continue card shows level and world and **never** the next discovery.

### Home, Level Select, Stats

- **Home.** Tagline "Solve. Reveal. Discover." Order as built in §4f (**Discoveries and Daily were swapped in §4g**):
  **Continue Exploring** (level · world · "14 / 30 discoveries" +
  bar; "Start Exploring" on a fresh install; the chevron went to give the title room — it wraps to two lines at 320dp)
  → **Discoveries** (single row: sparkle badge, "14 / 30 FOUND", a thin bar, and up to four small
  overlapping stickers of *only found* discoveries, newest on top — as many as fit, one "?" when none) → Daily
  (unchanged) → Game Modes:
  Campaign now reads "14 / 30 discovered" → Stats / Settings. "Latest" is the highest cleared levels, which is the
  order they were found in (the Campaign unlocks strictly in sequence); no timestamp or recency state was added.
- **Level Select.** Summary "14 of 30 discovered" (a second way into the album, ≥ 48dp). World headings are the shared
  `WorldDiscoveryHeader`: name, "4 / 6 DISCOVERED", and a gold COMPLETE chip when all six are found (the band gets a gold
  edge). Tiles are a little taller than wide (0.82). A **cleared** tile = number, the discovery's miniature, stars; a
  **mystery** (open, not cleared) = number and a "?" — the current one still has the lit ring; a **locked** tile is
  exactly what it was (padlock, not clickable). Spoken: "Level 10, Butterfly discovered, 2 of 3 stars" · "Level 11,
  undiscovered. This is your next mystery." · "Level 12, locked".
- **Stats.** The Campaign card leads with **"14 / 30 Discoveries Found"** and keeps "★ 42 Stars Earned".
  `PlayerStats.campaignDiscoveries` is `CampaignDiscoveries.collectedCount` (catalogue-based); nothing new is tracked.

### Shared pieces added

`DiscoveryComponents.kt` (`MysteryMark`, `dashedBorder`, `CompleteChip`, `WorldDiscoveryHeader`), `SparkleGlyph` in
`GameIcons.kt`, and `ResultScreen`'s `BigStars`/`PerfectRibbon`/`StarNote`/`SecondaryRow`/`ResultButton`/`ButtonGlyph`
made `internal` (with `scale`/`animate`/`delay` parameters) for reuse.

### Known visual risks (unverified on device)

| | |
|---|---|
| The reveal's art lands in the result layout's hero slot, well above the stage centre on a tall phone; the glide hides most of that but it is the first thing to look at | tune `MAX_ART`, the `room * 0.28f` factor, or the glide window |
| A 320×568 phone scrolls the result by roughly one button row | art shrinks with the room; stars block is a fixed 92dp so the buttons do not jump |
| Level Select tiles at 320dp hold a 13sp number, ~22dp of art and 9dp stars in ~56dp | the art is small there by necessity; the number is deliberately first |
| The comet, planet and galaxy are the least literal drawings | the set was reviewed as SVG, not in the app |
| Home now scrolls slightly on an ~800dp phone (one more card than before) | content is vertically centred and scrolls; the Discoveries card is one row on purpose |

---

## 4j. Pacing re-author — IMPLEMENTED (layouts: 8 of 30; `Levels.LAYOUT_VERSION` 3)

Follow-up to the §4i audit. **Silhouettes, arrow counts and board sizes are unchanged**; only the directions of eight
boards changed, via the dev-only `ShapeAuthoring` (test source set), so the other 22 layouts are byte-identical to
version 2. `CampaignRegressionTest`'s fingerprint and the version assertion were updated; an in-progress Campaign
board saved under version 2 is discarded on load (completed levels, stars and unlocks are keyed by id and untouched).

| Level | Role | Effort (before → after) | What changed |
|---|---|---|---|
| 5 Heart | Sky Garden **breather** | 28.5 → 23.0 (−4% vs L4) | 4 openers (was 3), shallower |
| 6 Star | Sky finale | 33.9 → 29.3 | re-authored so the finale is a rise (+28%) from the breather rather than a spike into Forest |
| 9 Tree | — | 42.5 → 42.0 | four single-arrow passes in a row → none; forced moves 30% → 17% |
| 11 Fox | Forest **breather** | 48.6 → 42.8 (−5%) | tail of three singles → two; forced 26% → 16% |
| 12 Owl | Forest finale | 51.8 → 48.9 | 5 trailing single-arrow passes → 1; forced 35% → 14% |
| 17 Eagle | Canyon **breather** | 74.6 → 67.9 (−3%) | 6 openers, forced 9% |
| 23 Magic Star | Crystal **breather** | 103.2 → 92.0 (−4%) | 7 openers (was 8), forced 7% |
| 29 Satellite | Cosmic **breather** | 138.8 → 126.7 (−6%) | removed a run of four singles; 7 openers (was 9), forced 6% |

The rhythm is now: each world climbs, **takes one breath on its fifth level, and ends on its hardest board**
(the finale then rises +14…+28% from the breather). Dips are small on purpose (3–6% in the effort metric — that metric
mostly measures size; the felt difference is more choice and fewer forced moves). Breathers open with at least as many
free arrows as the level before.

**Authoring additions** (`ShapeAuthoring`): `singleStreak(layers)`, `forcedShare(level)` (share of moves in fixed-seed
random clears with exactly one free arrow) and `passesPacing(m, forcedShare)` — no run of more than two single-arrow
passes, no tail of more than two, forced share ≤ 24% (boards of 12+ arrows). The pacing bar applies **only to the
re-authored levels**; several untouched ones would fail it (Level 22: a run of three singles, Level 4: tail of three).
The eight were chosen by a DP over all 30 levels with the other 22 held fixed, scoring the step into and out of every
changed level (breather step 0.90–0.98×, finale-after-breather 1.04–1.33×, else 1.02–1.30×). Seeds are in the
comments in `Level.kt`.

**Tests:** `game.CampaignPacingTest` (7: one planned dip per world on the fifth level and nothing else dips; breathers
open with ≥ the previous level's free arrows; a world ends on its hardest board; breathers sit ≥5% under the finale
that follows; re-authored levels pass both bars; finales are not forced shuffles), `ShapeAuthoringTest` (+3),
`LevelDifficultyReportTest` now names the five breather ids as the only permitted dips (floor 0.90×).

**Not done:** the reveal pause and the build-up strength (§4i) are judged by feel and still await a device pass.

---

## 4h. Typography identity — IMPLEMENTED (presentation only)

One family, **Nunito** (SIL OFL 1.1), as `GameFont` in `ui/Theme.kt`. Four static Latin-subset
instances in `res/font` (`nunito_regular/semibold/bold/extrabold.ttf`, ~37 KB each, ~148 KB total) —
static because variable axes need API 26 and minSdk is 23. Every `AppTypography` style and every
`AppText` style sets the family, and `ArrowEscapeTheme` also provides it as `LocalTextStyle`, so raw
`Text(fontSize = …)` calls inherit it. Display = ExtraBold/Bold; body = Regular. Medium resolves to
Regular/SemiBold. Uppercase tracking was reduced (section 1.4→0.8, reward titles 1.4→0.8, result
badge 1.8→1.0). Not device-tested: Nunito is ~5–8% wider than Roboto, so check 320dp and large font
scale on Continue Exploring, Sunset Canyon, Crystal Night, Treasure Chest, All Discoveries Found.

## 4g. Final UI polish — IMPLEMENTED (four targeted presentation fixes)

From a real-device review of §4f. **Presentation only**: no rule, generator, save format, layout,
`Levels.LAYOUT_VERSION`, `StarRating`, discovery mapping, Daily logic or `GameViewModel` line changed, and
nothing was added to persistence. **Not seen on a device** — compiled and JVM-tested.

**1. Result screens no longer sit over faded gameplay chrome.** The result layer's scrim is translucent so the
world shows through, which also showed the HUD, hearts, progress bar, footer and Hint pill through it. In
`GameScreen` the world (`WorldBackground`) is now a sibling that always stays, and the gameplay chrome (HUD +
board + footer) is an `AnimatedVisibility` with `EnterTransition.None` and a `fadeOut` over `resultEnterMs(...)` —
the *same* span the result's scrim fades in over (`ResultScreen` uses the same helper, so they cannot drift:
420ms for a reveal, 300ms otherwise, 120ms reduced motion). It then leaves composition, so it also leaves touch,
focus and the accessibility tree (`clearAndSetSemantics` covers the fade itself). Applies to every result: Campaign
discovery, Daily completion, Game Over, Endless complete, tutorial. When the result goes (Replay/Next) the chrome
returns at once with no entrance; the board's state is in the ViewModel and is untouched, and `stageCentre` (the
reveal's glide origin) keeps its last measured value. Pure policy in `ui/ResultPresentation.kt`:
`isResultShowing(status, celebrationFinished)`, `gameplayChromeVisible(resultShowing)`, `resultEnterMs(...)`.

**2. Home order** is now Continue Exploring → **Discoveries → Daily Challenge** → Game Modes → Stats/Settings.
`HomeScreen` walks `HomeContentOrder` (`HomeSection`, pure) so the order is pinned by `HomeOrderTest`. Cards are
untouched; spacing is a card gap between the three cards and a section gap before Game Modes (the same
vertical total as before).

**3. Level Select gold means three stars.** `tileStateFor(progress, levelId)` is the one rule:
`PERFECT` ⇔ cleared and `bestStars == 3`; one/two stars → `COMPLETED` (blue, the earned stars); a clear with no
star on record (pre-star saves) → `COMPLETED` (blue, tick), never gold. The state derivation was already data-correct
(the saved map is range-checked 1..3); the ambiguity was the *star row on the gold tile*, which was pale
white-on-gold and read as unearned. A perfect tile's three stars are now solid deep brown (the colour of its
number). Tile sizes, world sections, mystery "?" and locked tiles are unchanged; spoken labels unchanged.

**4. HUD progress row.** The bar and "N left" are one slim navy-glass capsule (`ProgressChip` in `GameHud.kt`,
same fill/hairline/shadow family as the level and lives pills), so the count belongs to the bar and has guaranteed
contrast over every world. 18dp tall; at < 400dp it shares the row with the 26dp compact lives pill, so it adds no
height there (about +2dp at ≥ 400dp). Same count logic and the same spoken label ("N of M arrows remaining");
no moves/timer/score/percentage.

**Tests added (14):** `ui.ResultPresentationTest` (5), `ui.LevelTileStateTest` (6, sweeps all 30 levels × 0..3 stars),
`ui.HomeOrderTest` (3).

**Look at on a device:** the result's fade-over (chrome should be gone by the time the scrim is up, with no flash
of bare world); the gold tiles' brown stars; the capsule at 320dp beside the lives pill and at ~400dp; Home at
~800dp (Daily is now third).

**Re-audit (the "Phase 7 final UI polish" brief asked for the same four fixes).** All four were already in the code
as described above and were re-read, not rebuilt. One hardening edit: `LevelTile`'s no-discovery fallback branch (a
cleared level that is not in the catalogue — unreachable today, all 30 levels have one) drew only a tick, so a gold
tile arriving there would have had no ★★★; it now draws `TileStars` when the level is rated, so gold always shows its
stars on every path. `ui.*` JVM tests: 301, 0 failures.

---

## 4i. Engagement polish — IMPLEMENTED (presentation only; no rule, layout, save or ViewModel change)

Goal: more satisfying, better paced, a healthy "one more puzzle" — **from the puzzle and discovery loop, not from
systems** (no currencies, timers, streak pressure, ads, chests, copy like "ONLY 3 LEFT!"). **Not seen on a device.**

- **Final-three-arrows build-up** (Campaign shape only; the replayed Tutorial does not). `ShapeAnticipation.stage(left)`
  (pure, `BoardPresentation.kt`) is 0 for 4+ arrows, 1/2/3 for 3/2/1 left, 0 at zero. `CampaignShapeHalo` takes an
  `anticipation` 0..1 state: step 1 lifts the pool of light, step 2 lifts the world-coloured glow, step 3 tightens and
  brightens a little (`POOL_LIFT .30`, `ACCENT_LIFT .40`, `FOCUS_LIFT .15`, `FOCUS_TIGHTEN .10`, all multipliers on the
  existing halo). Eases over 520ms, steps under reduced motion. It is a function of the *count* only — nothing marks an
  arrow, no text, no mechanic touched. At zero it drops and the halo settles with the existing 400ms ease: that is the
  "brief settle" between the last arrow leaving and the discovery.
- **Reveal pause.** The art now starts at layer +130 (was +70) and lands in 390ms (was 450): the empty-stage beat after
  the last flight goes from ~80ms to ~140ms while the **peak (900ms) and total length are unchanged**.
- **World Complete.** `DiscoveryResult` gained `completion` (`NONE/WORLD/CAMPAIGN`, true only on a *first* clear that
  finishes the set — a replay of a finished world keeps the old gold chip and does not repeat the milestone),
  `completionTitle` ("SKY GARDEN COMPLETE!" / "ALL DISCOVERIES FOUND"), `completionCount` ("6 / 6 DISCOVERED" / "30 / 30")
  and `completionSet` (the world's six discoveries, or each world's finale — Star, Owl, Treasure Chest, Crown, Galaxy).
  `DiscoveryResultContent.CompletionLine` takes the collection line's slot (same height, nothing below moves): gold title,
  count, then the set's stickers popping in one by one on world-coloured discs with a soft gold pool and one small gold
  burst (`CompletionFx`). `RevealSchedule` gained `completionMs 1000 / completionStaggerMs 70 / completionBurstMs 600`
  and `totalMsFor(pieces)`; the beat sits *behind* the ordinary sequence — buttons still arrive at 1020/1120. Reduced
  motion: pieces appear together, no scale, no burst. The completion is in the spoken summary ("… World complete." /
  "… All discoveries found."), not only in effects.
- **Campaign complete** on the album: the gold "ALL DISCOVERIES FOUND" header card now also shows the five world finales
  (`WorldFinaleRow`, built from `DiscoverySlot.Collected` only). No new system, nothing to claim.
- **Goal gradient.** `WorldPips` (DiscoveryComponents.kt) is the shared six-pip row: lit in the world colour, and when
  exactly one is missing that slot is a quiet world-coloured ring. Used by the result's collection line and now by
  `WorldDiscoveryHeader`, so Level Select and the album show "5 / 6 DISCOVERED" *and* five lit + one waiting. Static.
- **Eagle** (`canyon_eagle`): angled brows replaced with gently arched ones — it was the one stern face in the family.
  Art key unchanged; nothing else redrawn.
- **Audited, unchanged:** tap → escape timing (70ms wind-up + 300ms flight, sound/haptic on the event), blocked-tap
  feedback (55ms shake, 240ms blocker pulse, immediate life loss), newly-unblocked awaken (120+180ms), Continue Exploring
  and Retry (one tap, no dialog; Game Over shows immediately), Daily and Endless (untouched), mystery/locked slots (still
  structurally unable to leak), stars on Level Select tiles (earned vs ghost, gold = three).

**Difficulty / pacing audit (the findings that led to §4j below; written before the re-author).** Effort rose monotonically with
no cliff (steps +18–21% in Sky Garden, then +3–9%; world openings are the *smallest* steps: 24→25 +3%, 18→19 +5%).
Opening free arrows 3–9 (18–40% of the board), average free arrows during a random clear 2.1 (early) to 4.7 (Satellite),
so there is real choice throughout. Findings: (1) the curve is a smooth ramp with **no breather or per-world dip** — the
brief's preferred waves are absent, but nothing is broken, and rebuilding it means re-running the authoring DP and
re-baselining everything; (2) **Level 12 (Owl, Forest finale)** ends in five single-arrow passes (the authoring bar of
≤2–3 trailing singles only applied to boards of 16+ arrows) and has the highest forced-step share (35%) — the only
candidate for a future re-author; (3) Level 9 (Tree) has four consecutive single-arrow passes mid-clear; (4) Level 23's
8 openers on 28 arrows is the widest early spread. Discovery order: Forest/Canyon/Crystal read well; mild notes —
Sky's finale Star is its least characterful, and Cosmic front-loads Rocket (L26) while the penultimate Satellite (L29)
is the weakest. Ids are persisted, so none were reordered.

---

## 4k. Shape discovery system — IMPLEMENTED (not seen on a device)

**The idea.** The arrows are the clue ("what might this be?"). Solve them, and the **outline of the puzzle's own occupied
cells** traces itself where the arrows were, locks, and hands over to the reveal: the polished collectable (Campaign), or
the solved shape named (Daily / Endless). Same loop in all three modes; Campaign keeps the emotional collectable, Daily
adds freshness + streak, Endless stays fast.

### Shape core (`shape/`, pure Kotlin — no Android, no Compose)

- **`ShapeMask`** — which cells of a grid hold an arrow; directions thrown away. `ShapeMask.of(level)` / `of(arrows, rows,
  columns)` / `parse(listOf("##.", …))`. `cells`, `cellCount`, `bounds` (`MaskBounds`), `trimmed()`, `mirrored()`,
  `rotated(quarterTurns)`, `components`, `ascii()`. Immutable, equality by content. Always built from a level's **full**
  arrow list, so it never changes as arrows leave.
- **`GridContourTracer.trace(mask) → ShapeContour`** — outlines from cell edges (no bitmaps, no geometry library). Every
  occupied cell contributes a directed unit edge per side facing empty space; each edge is paired with its continuation and
  the loops are the cycles. `ShapeContour.loops` (`ShapeLoop`: corner `GridPoint`s, collinear points removed, `isHole`,
  `perimeter`, `area`), `.outer`, `.holes`, `.area` (= the mask's cell count, asserted for all 30 levels and all 27
  pictures). Outer loops run clockwise, holes counter-clockwise, so one even-odd fill leaves holes open. Coordinates are
  grid corners in the **level's own** coordinates — the same ones an arrow's `row`/`col` use — so a renderer subtracts the
  occupied-bounds origin exactly as the board does. **Pinch corners** (two cells touching only diagonally) are decided by
  topology, not a fixed turn: if the two filled cells are the same 4-connected piece the corner *joins* them (so a Fish's eye
  beside an outer notch stays a hole — an earlier fixed-right-turn rule merged it into the outline and traced no hole); if
  they are different pieces (a Bird's wing tip, the Satellite's antenna) they stay separate loops.
  `ShapeLoop.smoothed(2)` returns the loop with corners rounded (unit-step cut + Chaikin) — the polished silhouette for a
  reveal; the confirmation outline itself stays on the cell edges.

### The Mystery catalogue (`shape/MysteryShapes.kt`) — Daily and Endless only

`MysteryShapeTemplate(id, name, rows, allowMirror, allowedRotations)` → `variants` (`ShapeVariant`: template + flipped? +
quarter turns → `mask`). **27 hand-drawn pictures**, each one 4-connected piece of 15–32 cells in ≤ 6×8: Flag, Music Note,
Boot, Tulip, House, Lightning Bolt, Top Hat, Pear, Apple, Umbrella, Goblet, Sailboat, Key, Balloon, Candle, Camera, Bell,
Mug, Lamp, Hourglass, Anchor, Fish, Gift, Snowman, Turtle, Ghost, Shield. None is a Campaign discovery (a test compares
names and masks). Holes are deliberate and kept (Fish eye, Key ring, Camera lens, Balloon shine, Mug handle, Anchor ring,
Gift ribbon, Ghost's two eyes, Shield slot). `allowMirror` only where the flip is a different board (Fish, Key, Sailboat,
Flag, Note, Bolt, Boot, Umbrella, Apple, Pear, Balloon, Camera, Mug). **`allowedRotations` is `{0}` for every template**: an
upside-down House or sideways Balloon is not that object, and a quarter turn of a tall template breaks the 6-column limit —
the field exists for a template that genuinely reads either way up. Ids are permanent (Daily and saved Endless boards derive
from them): redrawing or reordering a shipped template requires bumping `MysteryShapePuzzles.GENERATOR_VERSION` **and**
`DailyChallenge.GENERATOR_VERSION`. Only one thing reads a name: `GameViewModel.shapeReveal` after a win.
Dev review sheets (see below) show all 27.

### `ShapePuzzleGenerator` — shape first, directions second

`generate(mask, seed, profile, name) → GeneratedShapeBoard(level, solution, metrics, attempts, onProfile)`. Reverse
construction on the mask's cells (placement order reversed = the witness), every legality decision through the real
**`MoveValidator.canEscape`**, steered placement (block what is free, point down lines that will fill, keep all four glyphs,
avoid stripes, place a cell that is running out of directions first, refuse a placement that strands another), and the
finished board is **always replayed through `BoardAnalysis.verifyOrder`**. The occupied cells never move
(`ShapeMask.of(result.level) == mask`, tested for every template × tier). Pure function of `(mask, seed, profile)`. Up to 96
attempts; the first that meets the profile ships, else the closest (`onProfile=false`; the shipped catalogue is tested to
be on-profile at every tier that may draw it); a one-cell/line mask or an impossible profile still ships a verified board,
and a construction that dead-ends everywhere falls back to the all-up board, so generation cannot throw. Cost: ~0.6–6 ms
average, ≤ 25 ms worst per board on the JVM.

**Difficulty is logic, not ugliness.** `ShapeTierProfile.of(tier)` holds, per tier: the template **size window** (Beginner
14–20 cells, Easy 16–24, Medium 18–26, Hard 23–30, Expert 25–32 — overlapping, so a Fish is the same clean Fish at several
tiers), the opening **free-arrow share** (0.34–0.62 → 0.12–0.26), minimum **peel depth** (3 → 7), distinct glyphs (3 → 4),
dominant-glyph share (0.55 → 0.40), run and single-arrow-streak caps, and the steering ranges. Measured effort climbs
40 → 49 → 60 → 78 → 91 tier by tier. `PuzzleGenerator`/`EndlessTier.arrows`/`QualityRules` are retained (abstract
rectangular boards; their tests and the gated stress tests still run) but **no mode uses them any more**.

### Selection

- **Endless** — `MysteryShapePuzzles.choose(seed, tier)` (pure) picks template + orientation; `generate(seed, tier)` builds it.
  **Repeat control** lives in `GameViewModel.nextEndlessPuzzle`: it keeps the last 3 served template ids in memory (not
  persisted; a resumed board seeds it) and redraws the *seed* up to 16 times until its template is not among them — a board
  is a pure function of its seed, so repeat control cannot live inside generation. Over 160 puzzles no picture repeats within
  four.
- **Daily** — `DailyChallenge.shapeFor(date)` deals `MysteryShapes.dailyPool` (cells ≥ 18) like a shuffled deck: days are
  cut into cycles as long as the pool, each cycle visits every picture once in an order shuffled by the cycle number, and a
  cycle never opens with the picture the last one closed with — so no repeat until the pool is exhausted, never two days in a
  row, and still nothing stored. Flip (when allowed) from the day's seed. `tierFor(date)` is **Hard for pictures ≥ 25 cells,
  else Medium** (the day's picture decides). `GENERATOR_VERSION` is **2**.

### The win flow (`ui/CompletionFlow.kt`, `ui/Celebration.kt`)

```
Playing ──▶ FinalEscape ──▶ ShapeConfirming ──▶ Revealed          Playing ──▶ Lost (no confirmation, card at once)
```
`CompletionPhase` is a **pure function** of `(GameStatus, ms since the last arrow launched, ShapeConfirmSchedule)` —
monotone, never re-enterable, not persisted. `rememberWinCelebration(status, kind, reducedMotion, onLock, onPeak)` owns **one
linear `Animatable` clock** and fires three cues exactly once per win from one effect keyed on `(won, kind, reducedMotion)`
(so a recomposition cannot restart it): **lock** (outline closes → `SHAPE_CONFIRM` sound + `SHAPE_LOCK` haptic),
**hand-off** (`resultVisible`), **peak** (win stinger + celebration haptic — `onPeak` is called here and nowhere else).
`CompletionKind` = `Discovery` (Campaign level that hides one), `Shape` (Daily/Endless), `Plain` (tutorial). The old board
glow/burst (`BoardCelebration`) was **removed** — the outline *is* the board's celebration; the reveal layers' sparkles are
the only particles.

**Timeline** (launch clock, ms; `ShapeConfirmSchedule`): Campaign — ghost fill 110→370, outline traces 200→660, fill 430→760,
**lock 660**, **hand-off 760**; then `RevealSchedule` (layer clock) art 60→440, **peak 440 (≈1200 overall)**, buttons from
940 (pressable 1040, ≈1960 overall, i.e. about 2 s from the last arrow). Daily/Endless — ghost 60→220, trace 110→410, lock 410,
hand-off 470; `ShapeRevealSchedule` art 30→330, peak 220 (≈690 overall), buttons 480 (pressable 580, ≈1150 overall). Reduced
motion — no trace: the finished outline fades in 100→240, lock 240, hand-off 300; reveals have no burst/scale/glide and the
buttons are ready ≈ 0.45 s after the last arrow.

### The outline (`ui/ShapeConfirm.kt`)

`ShapeConfirmOverlay` is a sibling of the pieces *inside the formation box* (same box, same cell size, same
`OccupiedBounds` origin), so the outline sits on the cells it was traced from — nothing re-centres or reflows. Geometry
(`ShapeOutlinePaths`: rounded loops, `PathMeasure` lengths, an even-odd fill, twinkle points) is built **once per
contour × cell size** (`remember`) and only *drawn* per frame, reading the clock in the draw phase only: faint ghost fill →
every loop traces at once (soft accent glow + accent + thin white core) → inside lights up → lock flash and a handful of tiny
twinkles. It is composed only when the board is **won**; it has no semantics and carries no name. It lives inside the gameplay
chrome, so it fades out with the board over the scrim's span while the reveal comes in (`resultEnterMs(kind, reduced)` — one
span for both). The footer/hint fade out while the outline plays.

### The reveals

- **Campaign** — unchanged result (`DiscoveryResultContent`: NEW DISCOVERY! / DISCOVERY FOUND, art, name, collection, stars,
  Perfect, WORLD COMPLETE / ALL DISCOVERIES FOUND, Continue Exploring). Only its start moved to the confirmation's hand-off
  (`leadInMs` = `ShapeConfirmSchedule.Discovery.handoffMs`, art 60 after) and a replay still gets the full confirmation.
- **Daily / Endless** (`ui/ShapeRevealContent.kt`) — the solved shape, **drawn from the puzzle's own contour** (smoothed, ink edge,
  white sticker ring, two-tone fill in the world's colours, gloss, two twinkles; never an emoji or stock icon), glowing from
  the stage centre it came from, then the **name**. Daily first clear: "TODAY'S SHAPE REVEALED!" + name + "Completed Today"
  + date + current/best streak + **Done** (primary) / Play Again. Daily replay of a finished day: "TODAY'S SHAPE" — not a reveal.
  Endless: "SHAPE REVEALED!" + name + "Puzzle N · Tier" + **Next Puzzle** / Replay / Home. Wording is pure
  (`ShapeRevealCopy`, tested). The buttons swallow presses until `interactiveMs`. The Daily summary page (`DailyScreen`, only
  reachable once today is done) shows today's shape and name in place of the calendar badge.
- A Daily/Endless win with no published name (cannot happen today) falls back to the plain card instead of inventing one.

### Presentation of the boards

`boardPresentationFor` now returns **`Shape` for every mode**: Daily and Endless are floating pieces on the world (no grid
surface, no empty-slot tiles) so the silhouette is actually visible — a grid slot for every cell would half-hide the picture
it was built to. The final-three-arrows halo build-up runs in every mode but the replayed tutorial; Daily lights the halo and
the outline with the gold-warmed world accent. `GridBoard`/`BoardPresentation.Grid` remain in the code but nothing selects them.

### Copy

Footer (Campaign, no tutorial caption): "What might it be? Solve the shape to find out."; **Level 1 before it is cleared**:
"Clear the arrows to reveal the hidden shape." (the one first-time line; the tutorial captions are unchanged and take the line
while they run); Daily: "What might today's shape be?"; Endless: "Mystery shape. Solve it to reveal it." Home: Daily card
"Today's mystery shape" (when not done); Endless card "Mystery shapes · #N".

### Sound and haptics

New `GameSound.SHAPE_CONFIRM` (`res/raw/sfx_shape_confirm.ogg`, 230 ms, two soft high plucks, gain 0.62 — the smallest sound in
the game; generated by `tools/sfx/gen_sfx.py` like the rest, original) and `HapticEffect.SHAPE_LOCK` (`GESTURE_END` on API 30+,
`KEYBOARD_TAP` before; lighter than the celebration). Both obey the sound/haptics toggles. Arrow escape sound/haptic, the
win stinger and the celebration haptic are unchanged and fire once.

### Spoiler protection (the load-bearing rule — keep it)

A board in play holds arrows and nothing else. **Daily/Endless:** the hidden name lives only in `GeneratedPuzzle.shape`
(`ShapeIdentity`), which `GameViewModel` keeps privately; `GameMode.Endless/Daily` hold seed + tier only, `Level.name` is
"Daily Challenge"/"Endless · tier", and the name is published on **`shapeReveal`** (`ShapeReveal(templateId, name)`) only
after the win is persisted — null in play, after a loss, in Campaign and the tutorial, and cleared the moment another board
opens. **Campaign:** unchanged structural rule (§4f invariant 12). Tests (`ui.ShapeModesTest`) scan everything the UI is
handed during play (mode, state, level name, hint, blocked, tutorial…) for all 27 template names and all 30 discovery
names/art keys, before, mid-board, after a hint and after a blocked tap; and prove the name is available after the win. The
outline overlay and the art canvases carry no semantics. After the reveal one polite live-region heading is announced
("Shape revealed: Fish…", "Today's shape revealed…", the Campaign "New discovery…").

### Persistence and migrations (nothing player-visible is wiped)

| Store | Change | What happens to an old record |
|---|---|---|
| Campaign `saved_game` | `Levels.LAYOUT_VERSION` 3 → **4** (five redrawn silhouettes) | in-progress board saved under 3 is discarded; `player_progress` (completions, stars, unlock) untouched |
| Endless `endless_game` | record **v2**: `2\|seed\|num\|TIER\|lives\|ids\|generatorVersion` (was v1, six fields) | a v1 record, a wrong field count or another generator version is discarded *and wiped*; `endless_progress` (v1, unchanged: puzzle number, total, streaks) untouched |
| Daily `daily_game` | `DailyChallenge.GENERATOR_VERSION` 1 → **2** (mixed into every seed) | a record whose seed no longer matches its date's is discarded by the existing cross-check; `daily_progress` (streak, best, total, last date) untouched |

Nothing about the reveal is persisted: no "seen" flag, no phase. A won board is simply a saved win. A relaunch mid-reveal
re-enters at no phase (the ViewModel was rebuilt; the win is already on disk).

### Dev tooling: silhouette review sheets (test source set only)

`shape.ShapeSheet` + `shape.ShapeSheetWriterTest` write `app/build/reports/shapes/{campaign-shapes-1..3, mystery-shapes-1..2,
contour-states}.svg` — each shape drawn as the pieces, the confirmation outline with its ghost, and the smoothed polished
silhouette (SVG approximations of the Compose drawing, same geometry). Render with headless Chrome (output path must be absolute):
`"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless --disable-gpu --hide-scrollbars --screenshot=/abs/out.png --window-size=W,H file:///abs/x.svg`
(W×H are in the SVG's `width`/`height`). Nothing here ships and nothing asserts on the art.

### Campaign silhouette audit (`Levels.LAYOUT_VERSION` 4)

All 30 silhouettes were rendered and judged on what a player would guess. Five read as the wrong thing and were redrawn, each
keeping its world's arrow band and the catalogue's non-decreasing counts:

| Level | Discovery | Was | Now |
|---|---|---|---|
| 10 | Butterfly | an X / bowtie (15 cells, 5×5) | two antennae over broad wings either side of a centre body (15, 4×5) |
| 17 | Eagle | a mushroom/cross (22) | wings swept up, a body, a fanned tail (22, 6×6) |
| 21 | Crystal | a "J" (24, 7×6) | a pointed shard with a small spike beside it on a rock base (24, 6×6) |
| 24 | Crown | a robot face — two "jewel" holes (28) | three points over a solid band with a rounded base (28) |
| 27 | Planet | a blob with two tabs (28, 7×6) | a round body with a wide ring across its middle (32, 8×6) |

Directions re-authored with the dev-only `ShapeAuthoring`: 60,000 constructions per level, filtered by the authoring bar, the
pacing bar and the **difficulty chain** with the other 25 layouts held fixed (steps 1.0–1.35× in effort; breathers L11/L17
keep their dip and opening-choice rules; the Crown, a finale, is its world's hardest board and not a forced shuffle). Seeds are
in `Level.kt`: L10 10015795, L17 17003574, L21 21023454, L24 24000919, L27 27002402. Cloud, Flower, Star, UFO, Satellite and
Galaxy were looked at and kept (coarse but plausible guesses; each is a re-author with chain risk). `CampaignRegressionTest`
pins the new SHA-256 (`f9283491…ead`), counts (L27 is 32) and sizes (L10 4×5, L21 6×6, L27 8×6).

### Known visual tuning points (judged from SVG renders, not on a device)

- **Butterfly (L10)** — the redrawn silhouette reads more like an "H with antennae" than a butterfly; it passed the chain
  and was left alone on purpose. First candidate to revisit (a 5-row, 16–17-cell version needs L11/L12 re-authored — counts
  are monotone).
- **Eagle (L17)** reads as a bird with raised wings / a "Y"; **Turtle, Snowman, Gift, Tulip, Apple** are the weakest mystery
  pictures (acceptable guesses). Ice Cream, Duck and Car were drawn and **dropped** as unrecognisable.
- Timings (lock 660 / hand-off 760 ms, art start, the 0.55 ghost-to-fill ramps) and the outline's three stroke weights
  (`0.34 / 0.13 / 0.05` of a cell) are guesses to be tuned on a phone; the `SHAPE_CONFIRM` gain (0.62) likewise.
- The polished Daily/Endless art is a smoothed silhouette, not an illustration: check the 5- and 6-column pictures at 320dp
  and that the glide from the stage centre reads as "the same shape".

### Tests added (shape-discovery pass)

`shape.ShapeContourTest` 21 · `shape.MysteryShapeCatalogueTest` 14 · `shape.ShapePuzzleGeneratorTest` 12 ·
`shape.CampaignShapeMaskTest` 6 · `shape.ShapeSheetWriterTest` 3 (dev) · `daily.DailyShapeTest` 9 ·
`progress.ShapeSaveCompatibilityTest` 8 · `ui.CompletionFlowTest` 19 · `ui.ShapeModesTest` 19 — and `RevealScheduleTest`,
`ResultPresentationTest`, `BoardPresentationTest`, `DailyChallengeTest`, `PersistenceIsolationTest` updated for the new
timings / kind-based `resultEnterMs` / all-shape presentation / shape-based Daily / the v2 Endless record.
**Verified: `compileDebugKotlin` OK; full JVM suite 737 tests, 0 failures, 3 skipped (the gated stress tests, which also pass
with `-Darrowescape.stress=1`).**

---

## 4l. Retention, onboarding & launch experience — IMPLEMENTED (not seen on a device)

Four systems, none of which touches a rule, a layout, a generator, the discovery artwork, difficulty, typography or a
world background. Everything optional stays optional: no ads, currency, reward-for-review, review gating, streak threat or
fake urgency, and nothing asks for a permission on the first frame.

```
Android system splash ─▶ SplashScreen (Compose) ─▶ ArrowEscapeApp ─▶ … a Campaign win ─▶ result ─▶ (settle) ─▶ nod ─▶ one prompt
  navy, no logo            arrows assemble, 0→100%     Home or Daily                                    "Great start!"  rate/share
                                                                                                                       or reminder
```

### A. The daily local reminder (`notification/`)

- **Scheduling.** WorkManager, **one unique piece of work** (`daily_mystery_reminder`) replaced on every schedule — never
  added to — so restarts, the Settings switch and the worker's own re-planning cannot leave two queued. It is a *chain of
  one-shot requests*, not a 24h periodic one: every run (`DailyReminderWorker`) computes the next from the *current* local
  clock and zone, and re-plans in a `finally`, so a changed time zone or date is corrected at the next run **or the next app
  start** (`ReminderController.syncOnStartup`, run by the loading screen). No exact-alarm permission, no foreground service.
- **Time.** One constant: `ReminderSchedule.HOUR = 18`, `MINUTE = 0` (6pm local). Evening on purpose — the Daily is ready from
  midnight, and a player who has played it by evening is not reminded at all. Next-trigger maths is calendar arithmetic
  ("tomorrow at 6pm on the wall clock"), tested across both New York DST changes.
- **When it is posted** (`ReminderPolicy.shouldPost`, pure): the switch is on **and** today's Daily is not completed **and**
  nothing was already posted today (`lastNotifiedDate`) **and** it is within −5…+240 minutes of 6pm local. A run held back
  by power-saving for hours is *dropped*, not sent at 11pm; tomorrow's is still on schedule.
- **Content.** Channel `daily_mystery` ("Daily Mystery", default importance), one id (`1001`, so a missed day never stacks
  two): "Today's mystery shape is ready ✨" / "Can you figure out what it is?". It never names the shape and the policy has no
  access to it.
- **Tap.** `PendingIntent` → `MainActivity` with `EXTRA_DESTINATION = "daily"` (`startup/StartupDestination.kt`, flags
  `NEW_TASK|CLEAR_TOP|SINGLE_TOP`; manifest `launchMode="singleTop"`). Closed app: the intent arrives in `onCreate` (only when
  `savedInstanceState == null`, so a recreated activity cannot replay it) and is resolved **once** into the stack the app
  starts with (`openingStack`) — Home → Daily board (or Home → the completed-today page if it is already done), never Home
  first. Running or backgrounded app: `onNewIntent` → `LaunchRouter.offer` → `ArrowEscapeApp` acts on `pendingDestination`
  once (acknowledged *before* acting) with `reset(HOME, GAME)`. Back from it goes Home. The extra is removed from the intent
  once read.
- **Permission** (`NotificationPermission`, `ui/RetentionUi.kt`). `POST_NOTIFICATIONS` (API 33+) is only ever requested at the
  player's request: the "Want a daily mystery?" card (below) or the Settings switch. "Allowed" means
  `NotificationManagerCompat.areNotificationsEnabled()` — the permission *and* the per-app system switch. Denied → nothing
  else happens (no second dialog); the switch is off and says so. The system dialog is only launched while Android will still
  show it (`permissionAsked` + `shouldShowRequestPermissionRationale`); after that the switch opens the app's system
  notification settings, and when the player returns with notifications on (`ON_RESUME`), the reminder they asked for is
  enabled.
- **Settings.** New section **Daily Mystery**: one *Daily Reminder* switch (off by default; subtitle states the time, or that
  notifications are blocked). Plus **Rate Arrow Escape** and **Share with a Friend** rows under Help. No settings page.
- **Persistence.** `ReminderPreferenceRepository` (`daily_reminder` in `arrow_escape_retention`):
  `1|enabled|introShown|lastNotifiedIso|permissionAsked`. `ReminderServices` is the process singleton wiring preference +
  `ReminderController` + `WorkManagerReminderScheduler`.

### B. Interactive onboarding (`tutorial/`, `ui/TutorialHand.kt`)

Teaches by playing; no tutorial screen. **Level 1** keeps its existing lesson (§11) and gains the hand; **Level 2** gets a new
lesson. Both reduce to `OnboardingGuide` (caption, hand target, glow target), resolved from the *real board* by `HintEngine`
and `EscapeAnalysis` — there is no second copy of the blocking rule, and the guide never plays a move.

| | Level 1 (`TutorialState`) | Level 2 (`DependencyLesson`) |
|---|---|---|
| Idea | an arrow with a clear path escapes | removing one arrow frees another |
| Step 1 | `TAP_FREE` "Tap an arrow with a clear path" — hand + glow on `HintEngine`'s arrow | `CLEAR_ONE` "Clear one path to free another." — hand + glow on the arrow that frees the most |
| Step 2 | `BLOCKED` (after a refused tap): caption, **hand stays**, no glow (the board pulses the blocker) · `CHAIN` after an escape: "Nice! It escaped ✨", no hand | `TAP_FREED` "That freed another arrow. Tap it!" — hand + glow move to the freed arrow (from `newlyFreed`) |
| Landed | first escape (`CHAIN`) | tapping any freed arrow → `UNDERSTOOD` "You've got it!" |
| Over | next escape / board end → `FINISHED` | next move / board end → `FINISHED` |
| Recorded | **at the first escape** (not at the end of the lesson) — key `tutorial_completed` (kept: existing saves still count) | **when the idea lands** — key `onboarding_level2_completed` |
| Shows when | first-ever Campaign Level 1 (`!flag`) **and Settings → Replay Tutorial** | first-ever Campaign Level 2 (`!flag && !completed(2)` — so a player who cleared Level 2 before this existed is not taught it) |

A removal that frees nothing teaches nothing, so Level 2 waits and the hand finds the next arrow worth taking. Restarting before
a lesson has worked starts it over; after it has worked it does not come back. The two flags are independent
(`TutorialFlagStore` gained `isDependencyLessonCompleted`/`set…`; `SettingsRepository` implements both).

**The hand** (`TutorialHand`) is Canvas only — a union-pathed pointing hand, a 2s loop (glide in → press → release → ripple →
fade), no Lottie/GIF/bitmap. It is placed from the board's own `BoardMetrics` (`(col − originCol + ½) · cell`), so it lands on
the real cell at any scale; it **mirrors** when the arrow is right of 58% of the board so it never leaves the screen; it has
**no pointer modifier and no semantics** (taps pass through; the caption says the same in words, as a polite live region).
Reduced motion: no loop — the hand rests on the arrow with a still ring.

**After the clear.** `OnboardingCoach`: the first clear of Level 1 → "Great start!", of Level 2 → "You're getting the hang of
it!" — a small pill near the top, *after* the reveal has settled (`PromptTiming`), for 2.6s. The discovery reveal is never
interrupted.

### C. Rate & Share, and the reminder invite (`retention/`)

**The app cannot know whether a Play review was submitted** (the review API reports only that its flow *finished*, and a tap on
the listing says nothing). So there is no `hasRated`. `RetentionPromptState` records what happened: `promptCount`,
`lastPromptAtMs`, `lastPromptCompletionCount`, `ratePromptShown`, **`rateActionTapped`** ("they pressed Rate" — not "they
rated"), `shareActionTapped`. Nothing ever says "thanks for rating".

`RetentionPromptPolicy.decide` (pure), in order:
1. only a **won Campaign level** asks (loss, Endless, Daily, replayed tutorial: never);
2. **one prompt per app session**, of any kind (`RetentionCoordinator`, per launch);
3. never on a clear that **finishes a world or the album** (their own celebration) — the milestone is simply owed to the next win;
4. the **reminder invite** first — once ≥2 levels are cleared and it has never been shown and the reminder is off;
5. **Rate/Share** at milestones — **1 and 2** (the onboarding pair: *no cooldown*), then **6, 12, 20, 30** with a **7-day
   cooldown** since the last prompt. The highest milestone reached and not yet asked is used, so skipping past some asks once.
   Stops for good once `rateActionTapped`; `shareActionTapped` removes the Share button from later prompts.

So Level 1 → eligible; Level 2 in the same session → suppressed (session cap), eligible next session. Across all 30 levels the
player is asked about rating at most 6 times, ever. Pure-policy tests include a "most permissive possible player" run.

**UI** (`ui/RetentionDialogs.kt`, wired in `GameScreen`): `CoachToast`, then one `RetentionPromptLayer` — a glass card over a
scrim that swallows touches (no semantics, so screen-reader focus is not trapped; back = Not Now; scrim taps do nothing).
"Enjoying Arrow Escape? — If you're having fun, you can support the game." → **Rate on Play Store** · **Share with a Friend**
(hidden after Share) · Not Now. Invite: "Want a daily mystery? — Get one reminder when today's puzzle is ready." → **Enable Daily
Reminder** · Not Now. No "do you like it?" branch, no reward, no gating. Timing: `PromptTiming.settleDelayMs` = the reveal's
`totalMs` + 1.1s, measured from when the result layer composes (already after the last arrow, the outline and the hand-off).

**Review** (`StoreActions`): Google's in-app review flow; if it cannot be requested/launched, or the "finished" callback
arrives within `ReviewOutcome.MIN_VISIBLE_MS = 700` of launch (Google's quota makes the card not appear; it is not on screen
that fast), the Play listing opens (`market://details?id=com.sabalapps.arrowescape`, else the https URL). A flow the player spent
longer than that in is left alone. **Share**: `ACTION_SEND` text/plain through `Intent.createChooser` — "I'm playing Arrow
Escape — solve mystery arrow shapes and reveal what's hidden!" + the listing URL; always the chooser.

**Persistence.** `RetentionPromptRepository` (`retention_prompts` in `arrow_escape_retention`):
`1|promptCount|lastAtMs|lastCompletionCount|shown|rateTapped|shareTapped`; a corrupt record reads as "never asked".

### D. The loading screen (`startup/`, `ui/SplashScreen.kt`, `ui/AppRoot.kt`)

- **System splash → Compose.** `Theme.ArrowEscape.Starting` (parent `Theme.SplashScreen`): background `@color/splash_background`
  (#0A0E2B = `GamePalette.Navy`), a **blank** icon (the logo is built by Compose; two logos would double up),
  `postSplashScreenTheme = Theme.ArrowEscape`, whose window background is the same navy in both themes (no white flash).
  `MainActivity` calls `installSplashScreen()`, holds it only until the first Compose composition (`firstFrameReady`, 700ms
  failsafe) and removes it **with no exit animation**.
- **Real initialization** (`AppStartup.steps`, run by `StartupViewModel` → `StartupCoordinator` off the main thread):
  `SETTINGS`(2, required) · `PROGRESS`(4, required: campaign/endless/daily repositories) · `RETENTION`(1) · `SCENERY`(2: decodes
  *only* Home's backdrop, `warmMenuBackdrop`) · `REMINDER`(2: channel + `syncOnStartup`). Not done: generating puzzles, tracing
  shapes, decoding other worlds, rendering discoveries.
- **Failure safety.** Every step has its own timeout run as an *independent job* (a timeout can stop *waiting* for a blocked
  `SharedPreferences` read, not interrupt it) and a throw/timeout *settles* the task as failed and moves on. `isReady` =
  required settled and (all settled or 1s of grace); `HARD_CAP_MS = 6s` hands over regardless.
- **Real progress vs smoothing** (`StartupProgress.target` vs `SplashProgress`). The target is the real weighted fraction, held
  under 97% until the game can open, 1 once it can. The shown value only eases towards it: monotone, never above the target, 100%
  only when exactly full, with a floor of ~0.9s on filling from empty so the assembly can be read on a phone where startup takes
  40ms. Instant-startup path ≈ 0.98s until the bar is full and the arrows assembled (simulated at 60fps) + 0.24s closing glow pulse = hand-off at ≈ **1.2s**, then a 0.26s fade ≈ **1.5s** in all; slower only if the work really is slower.
- **The animation** (`SplashChoreography` timing, pure; drawing in `SplashScreen`): four glossy blue arrow discs (up/right/down/
  left — the Home brand mark's discs) fly in from the corners, spinning, with a slight overshoot, 70ms apart, around a gold
  four-point star; four small teal sparkles twinkle in the gaps; "ARROW / ESCAPE" (current typography, stacked, font-scale
  capped so it cannot overflow) fades up; a teal progress bar with a percentage fills; a glow pulse closes it. Navy base →
  gradient + radial glow come up out of it. Assembled by 900ms. Under reduced motion nothing flies: the finished emblem fades in.
- **Accessibility.** One node, "Loading Arrow Escape"; the percentage and the arrows have no semantics (no 1%…2%… announcements).
- **Hand-off.** At full bar the game is composed *underneath* during the pulse (`onReady`), then the splash fades. The loading
  screen is not in the back stack. The launch destination is read **once** (`LaunchRouter.consume`) into the stack the game
  starts with.

### Persistence added by §4l

| Store | File | Key | Format |
|---|---|---|---|
| Level 1 lesson | `arrow_escape_settings` | `tutorial_completed` (existing) | boolean |
| Level 2 lesson | `arrow_escape_settings` | `onboarding_level2_completed` | boolean |
| Daily reminder | **`arrow_escape_retention`** (own file) | `daily_reminder` | `1\|enabled\|introShown\|lastNotifiedIso\|permissionAsked` |
| Rate / share record | `arrow_escape_retention` | `retention_prompts` | `1\|count\|lastAtMs\|lastCompletionCount\|shown\|rateTapped\|shareTapped` |

### Tests added (§4l) — 177 new, 914 total

`tutorial.DependencyLessonTest` 15 · `tutorial.OnboardingGuideTest` 11 (incl. the coach line) · `ui.OnboardingFlowTest` 20 (through the
real ViewModel; the guided tap is asked of the guide and judged by the game — the arrow leaves; restart/relaunch/replay/other
levels/migration) · `retention.RetentionPromptPolicyTest` 23 · `RetentionPromptRepositoryTest` 7 · `RetentionCoordinatorTest` 7 ·
`StoreActionsTest` 5 · `ui.PromptTimingTest` 3 · `notification.ReminderScheduleTest` 9 (DST, zones) · `ReminderPolicyTest` 7 ·
`ReminderPreferenceRepositoryTest` 4 · `ReminderControllerTest` 10 (a fake scheduler that models *unique* work) ·
`startup.StartupDestinationTest` 8 (intent contract + navigate-once) · `StartupProgressTest` 12 · `StartupCoordinatorTest` 9 (real
coroutines: throwing, hanging, blocking, cancelled steps) · `SplashProgressTest` 15 · `SplashChoreographyTest` 7. Updated for
the intentional behaviour changes: `TutorialStateTest` (+3), `TutorialFlowTest` (+2; the "abandoned half way" and "restart
mid-lesson" tests now describe *before the first escape*).

### Look at these first on a device

1. Fresh install: no white flash between the system splash and the loading screen; arrows fly in, bar fills, Home appears with its artwork already there.
2. Level 1: the hand appears on a free arrow, taps, ripples; it sits on the right cell (and mirrors for an arrow near the right edge); tapping it → "Nice! It escaped ✨" and the hand is gone.
3. Level 2: hand → first arrow → the freed arrow glows and the hand follows → "You've got it!"; replay Level 2: nothing.
4. After Level 1's reveal settles: "Great start!" then the Rate card (not during the reveal); Not Now / Rate / Share all dismiss it.
5. Rate on Play Store: does the in-app card show on a Play-signed build, and does the listing open when it does not?
6. Next session / Level 2 clear: the "Want a daily mystery?" card; Enable → system permission dialog on Android 13+; deny → no second dialog.
7. Settings → Daily Reminder: on/off, blocked state after revoking notifications, the return from system settings.
8. A reminder (use the debug clock or wait): tap with the app closed / backgrounded → Daily Challenge, Back → Home; solved Daily → no reminder.
9. Reduced motion (remove animations): static hand + ring, no flying arrows, no toast slide.
10. Largest font: the Settings rows, the prompt card and the loading screen's "ARROW / ESCAPE".

### Known limitations of §4l

- Android cannot tell the game whether a Play review was submitted; only "the player pressed Rate" is recorded.
- The in-app review card is shown or not at Google's discretion (and not at all on a debug/sideloaded build); the fallback is a timing heuristic (700ms), not knowledge.
- A reminder is dropped (not sent late) when the system holds it more than 4h past 6pm; OEM battery managers can still delay or kill WorkManager work.
- The reminder time is not user-configurable (one constant).
- "Session" for the prompt cap is one launch of the activity, not an idle-time window.
- The launcher icon is still the Android Studio template (§17).
- The new surfaces use hardcoded English like the rest of the UI (notification copy is in `strings.xml`).

---

## 5. Game modes — ALL FULLY IMPLEMENTED

`ui/GameMode.kt` — sealed interface, four cases.

| Mode | Board source | Persists to | Result card "next" |
|---|---|---|---|
| `Campaign` | `Levels.ALL[id]` | `ProgressRepository` | next level |
| `Endless(seed, puzzleNumber, tier, alreadyCleared)` | generator | `EndlessRepository` | next puzzle |
| `Daily(date, seed, tier, alreadyCleared)` | generator, date-derived seed | `DailyRepository` | **none — one per day** |
| `Tutorial` | Level 1's layout | **nothing at all** | none |

`GameMode.Tutorial` exists specifically so Replay Tutorial cannot touch campaign progress — a
player on Level 20 who rewatches the lesson keeps their selection, their in-progress save and
their completions. The *first* run of the tutorial is **not** this mode; it is genuine Campaign
Level 1 with captions over it, and clearing it unlocks Level 2.

Note the two `alreadyCleared` flags mean **different things**:
- `Endless.alreadyCleared` is a **guard** — `EndlessRepository.markCompleted()` always increments,
  so the flag is what stops a replay double-counting.
- `Daily.alreadyCleared` guards **nothing** — `DailyProgress.completing(date)` is idempotent per
  date. It is purely what the result card reads to say "that is today done" vs "you have already
  had today". It is set when the board is opened or replayed, never by winning.
  *(Getting this backwards was a real bug found during Phase 5 emulator testing.)*

---

## 6. Level system — FULLY IMPLEMENTED (layouts redrawn as discovery silhouettes in Discovery Phase 2)

`game/Level.kt`. 30 hand-authored levels as ASCII layouts:
```kotlin
level(1, ".....", ".^<.v", "^<>>v", ".....")   // ^ v < > place arrows; . or space is empty
```
Ids are 1-based catalogue positions and are **persisted**, so they must stay stable once shipped.
Arrow ids are assigned in reading order (top-left first) by `Levels.fromLayout`, and the generator
does the same — an id means the same thing in a save file, a debug log and a hand-written level.
`Level.name` is still "Level N"; the thematic name is `CampaignDiscovery.name`.

**The occupied cells are the picture; the directions are the puzzle.** Each level's occupied cells
draw its discovery (a Cloud, a Heart, a Rocket…), at most 6 columns × 9 rows, with empty cells as part
of the silhouette (a board is as tall and wide as its picture; only Level 1 has a margin row top and
bottom). The blueprints live in `src/test/.../game/CampaignShapes.kt` and
`CampaignShapesTest` pins every level's occupied cells to its blueprint, so a layout edit cannot
quietly redraw a level's picture. `build/reports/levels/campaign-shapes.md` (written by that test)
is the shape review table — level, world, discovery, rows × columns, arrows, silhouette, opening free
arrows, solvable — plus every silhouette drawn out; Phase 3 starts from it.

| Levels | World | Arrows | Opening free | Notes |
|---|---|---|---|---|
| 1–6 | Sky Garden | 8, 8, 10, 10, 11, 12 | 3–4 | Level 1 is the tutorial board; 5, 6 re-authored (§4j) |
| 7–12 | Forest | 12, 14, 14, 15, 15, 15 | 3–4 | 9, 11, 12 re-authored (§4j) |
| 13–18 | Sunset Canyon | 17, 17, 17, 20, 22, 22 | 4–6 | 17 re-authored (§4j) |
| 19–24 | Crystal Night | 22, 23, 24, 24, 28, 28 | 5–7 | 23 re-authored (§4j) |
| 25–30 | Cosmic | 28, 28, 32, 32, 34, 35 | 6–7 | Level 30 = Galaxy, 9×6; 27 redrawn (§4k, 32 arrows, 8×6); 29 re-authored (§4j) |

Arrow counts never decrease down the catalogue (`LevelCatalogueTest`), and per-world bands are
asserted. Level 6 (Star) is 12, one over the brief's "roughly 11", on purpose — its tall tip is what
stops it reading as the Heart (a vertically flipped Star *is* a Heart at this size).

**How they were made (development only).** Reverse construction, exactly as the generator, pointed at
a mask: `src/test/.../game/ShapeAuthoring.kt` lays arrows on the silhouette's cells one at a time in
reverse removal order, each given a direction whose escape path was clear at that moment (judged by
the real `MoveValidator`, not a copy), so a legal removal order exists by construction. Placement is
steered (block what is free, point down lines that will fill up, balance directions, avoid runs, refuse
a placement that would strand another cell), many constructions are measured, and a dynamic program
picks one board per level so the chain climbs the difficulty-report test's curve without a step
outside 1.0–1.35×. Every candidate is replayed through `BoardAnalysis.verifyOrder` before the helper
returns it. It is **not** a runtime component and not a second rules engine; the layouts in `Level.kt`
are static, and the catalogue tests re-prove them independently. Authoring bars (stricter than the
catalogue tests): 3–9 opening free arrows (never more than 40%/36%/33%/30%/27% by world; ≤ 3 on
Level 1), all four directions on boards of 10+, no direction over 42%, runs ≤ 3, peel depth growing
with size, no opener that blocks nobody, openers spread over at least 2–3 quadrants, no more than
2–3 trailing single-arrow passes.

**Difficulty compared with the old layouts.** The old Campaign was long single-file chains (Level 30:
35 arrows, depth 17, effort 242). The silhouette boards are deliberately wider-peeling: Level 30 is
depth 10 with 7 openers and effort 149. The *curve* is the same shape (monotone, no cliff, finale
hardest) but tops out lower on the scanning-effort scale; that is a consequence of the brief's
"several openers, peeling-apart, no one-arrow-at-a-time chains", not an accident.

**Level 1 and the tutorial.** Level 1 is the Cloud (8 arrows, 3 free at the start, depth 3).
`TutorialState` references no arrow ids — the spotlight is `HintEngine.hint()` — so nothing needed
re-pointing; `CampaignShapesTest` asserts the board still supplies the lesson (something free,
something blocked, the spotlighted arrow frees another, ≤ 3 openers, ≤ 4 passes).

**`Levels.LAYOUT_VERSION`** (currently **4** — §4k: five silhouettes redrawn; 3 = the §4j pacing re-author; 2 = the Phase 2 silhouettes, 1 = the original abstract layouts) is the version of the
layouts and **must be bumped whenever any layout changes** — `CampaignRegressionTest` pins the
catalogue by SHA-256 and its failure message says so. It exists for the save rule in §10.

Every level was built by reverse construction, and `LevelCatalogueTest` re-proves solvability on
every build with an independent DFS (`LevelSolver`, test-source-only) whose witness is then
replayed through the real `MoveValidator`.

`LevelDifficultyReportTest` writes a development-only difficulty report to
`app/build/reports/levels/` and asserts the curve has no cliffs. The metric is *scanning effort*
(arrows remaining ÷ arrows free, summed over a clear). Never shown to players.

---

## 7. Procedural generator — FULLY IMPLEMENTED, HEAVILY TESTED

> **Since §4k no mode calls `PuzzleGenerator`.** Endless and Daily are built by `ShapePuzzleGenerator` from a
> `MysteryShapes` picture (same reverse-construction idea, mask-first, `MoveValidator` for every decision, witness replayed
> through `BoardAnalysis.verifyOrder`). `PuzzleGenerator`, `EndlessTier.arrows` and `QualityRules` stay as the abstract
> rectangular generator — their tests and the gated stress tests still run — and `EndlessTier` is still the difficulty
> ladder (5 tiers, tier by puzzle number), now interpreted by `ShapeTierProfile`. The rest of this section describes the
> abstract generator.

### `endless/SeededRandom.kt`
SplitMix64 written out in full, deliberately **not** `kotlin.random.Random` (whose algorithm is an
implementation detail and could be re-specified under a saved seed). `derive(vararg Long)` folds
numbers into one seed.

### `endless/PuzzleGenerator.kt` (393 lines)
**Solvable by construction, never by search.** The board is built backwards: each arrow is laid on
a cell whose escape path is clear *at that moment*. Removing in reverse placement order therefore
replays exactly those states, so every removal is legal and the witness is free.

Placement is *steered*, not random — `chooseWeight` scores each legal candidate by: blocking
currently-free arrows (`BLOCK_BONUS = 7.0`, the biggest lever on opening free-arrow count), room
ahead (`ROOM_BONUS = 6.0`), thin-glyph balance, empty row/column spread, and a hard run penalty.
Weight is squared so good placements dominate while every legal one keeps nonzero probability.

Up to `MAX_ATTEMPTS = 24` constructions; the first that passes the tier's `QualityRules` ships.
On exhaustion the attempt closest to the band midpoint ships with `onTier = false`.
Generation is a pure function of `(seed, tier)` — which is what lets a board persist as one `Long`.

Generated boards carry `ENDLESS_LEVEL_ID = 0`, which no catalogue lookup matches. That is the
mechanism preventing a generated board from ever restoring as a campaign level.

### `endless/EndlessTier.kt`
Five tiers with board sizes, arrow bands and a 10-field `QualityRules` bar
(arrow count, max free ratio, min distinct directions, max dominant share, max same-direction run,
density range, min peel depth, min band coverage, 4 occupied quadrants, score band).
`Rejection` enum names which rule failed, for the stress report.
Tier by puzzle number: 1–5 Beginner, 6–15 Easy, 16–30 Medium, 31–50 Hard, 51+ Expert.

### `endless/PuzzleMetrics.kt`
Seven normalised components weighted into a 0–100 score (weights sum to 1, asserted by a test):
COUNT .24, AREA .12, BLOCKED .22, DEPTH .20, DENSITY .08, DIRECTIONS .06, SCAN .08
(SCAN is normalised Shannon entropy of the direction histogram.)

**Changed in Phase 5:** `depthComponent` was `(peelDepth-1)/(9-1)` clamped. Expert boards reach
depth 11, so 9/10/11 all scored an identical 1.0 on a fifth of the score. Now
`1 - exp(-(depth-1)/DEPTH_SCALE)` with `DEPTH_SCALE = 5.0` — zero at depth 1, strictly increasing
forever, asymptotic to 1, never saturating. Tier score bands were re-cut against the measured
distribution afterwards (Beginner 20–47, Easy 30–57, Medium 42–70, Hard 55–82, Expert 65–92).

### `endless/BoardAnalysis.kt`
`peel()` (layered removal passes; null = unsolvable), `isSolvable`, `solutionOrder`,
`verifyOrder` (replays a witness through the real rules), `longestSameDirectionRun`.
The file's doc comment contains the monotonicity argument the whole design rests on.

### Measured behaviour — 5,000 boards, before/after the Phase 5 metric change

| | before | after |
|---|---|---|
| rejection rate | 67.2% | **63.7%** |
| off-tier fallbacks | 0 | **0** |
| avg generation | 0.140 / 0.165 / 0.167 ms | 0.135 / 0.153 / 0.156 ms |
| worst single board | 1.84–1.93 ms | 1.94–2.59 ms |
| distinct layouts | 1000/1000 every tier | 1000/1000 every tier |

Per tier (after): Beginner 6–9 arrows, depth 2–5, score 30.9–46.9 avg 42.8 · Easy 10–14, 3–7,
44.4–57.0 avg 53.4 · Medium 15–22, 4–8, 56.4–70.0 avg 65.7 · Hard 22–29, **depth 5–11** (was
5–9), 68.5–81.8 avg 74.8 · Expert 28–36, 5–11, 75.0–89.6 avg 81.4.

---

## 8. Daily Challenge — FULLY IMPLEMENTED

> **Superseded by §4k where they differ.** `GENERATOR_VERSION` is now **2**; a daily is `MysteryShapePuzzles.generate(
> DailyChallenge.shapeFor(date), seedFor(date), tierFor(date))` — a hidden picture per day, dealt from a shuffled deck, Medium
> for pictures under 25 cells and Hard from 25 (the old 3:2 Medium/Hard draw and the "no second generator" paragraph below
> describe version 1). The seed derivation (`stableHash("daily" + iso + "v" + version)`), the streak rules and the date layer
> are unchanged.

`daily/DailyChallenge.kt`:
```
seed = SeededRandom.derive(stableHash("daily" + YYYY-MM-DD + "v" + GENERATOR_VERSION))
```
`stableHash` is FNV-1a 64-bit written out — `String.hashCode` would work but is 32 bits of a weak
mix, and this is the one number that must be identical on every device and every future runtime.

`GENERATOR_VERSION = 1` is mixed into every seed. Bumping it deliberately re-cuts every *future*
day's puzzle; an in-progress daily whose stored seed no longer matches its date is discarded by
`DailyRepository`'s seed cross-check.

**Tiers are Medium/Hard only, 3:2** (`TIERS` list, indexed by a second salted draw). Never Expert
— a 36-arrow board is the wrong thing to hand the lapsed player or the first-timer. Never
Beginner/Easy — no reason to return for a 20-second board.

**There is deliberately no second generator.** A daily is `PuzzleGenerator.generate(seed, tier)`,
so it inherits solvability-by-construction, the witness, the quality filter, the column cap and
the entire stress suite.

**Streak rules** (`DailyProgress.completing`, idempotent per date):
same day → no change at all · next calendar day → +1 · anything else (gap, first ever, clock moved
backwards) → 1. Losing a daily does **not** break the streak — the day's puzzle is still there to
finish.

---

## 9. Date abstraction — FULLY IMPLEMENTED

`time/GameDate.kt` — `(year, month, day)` with no clock, no zone, no time.
Standard proleptic-Gregorian days-from-civil algorithm (shifts the year so leap days land at the
end of a 400-year era, removing every special case). `epochDay`, `fromEpochDay`, `isDayAfter`,
`plusDays`, `dayOfWeek`, `iso` ("2026-10-04" — **part of the save format**), `friendly()`
("Sunday, 4 October"), and a strict `parse()` that round-trips to reject 31 February.

`time/DateProvider.kt` — `SystemDateProvider` (uses `java.util.Calendar`, API 23-safe) and
`FixedDateProvider` with `advance(days)`. **No production code anywhere calls the clock directly.**

**Documented, accepted limitation:** the app is offline, so the date is whatever the device says.
A player can move their clock to inflate their own private streak. The fix, if a leaderboard ever
makes it worth cheating for, is a server-signed date behind the same interface — nothing above it
changes. This is written up in `DateProvider`'s KDoc.

---

## 10. Persistence — FULLY IMPLEMENTED

Five independent stores. Format is hand-parsed pipe-delimited strings, not JSON, so **every field
is range-checked on the way back in** and anything failing a check is discarded rather than
guessed at.

| Store | SharedPreferences file | Keys | Format |
|---|---|---|---|
| Campaign | `arrow_escape_progress` | `player_progress`, `saved_game` | `2\|highest\|current\|csv-completed\|csv-id:stars` · `3\|levelId\|lives\|csv-ids\|blockedTaps\|hintUsed\|layoutVersion` |
| Endless | `arrow_escape_progress` | `endless_progress`, `endless_game` | `1\|num\|total\|streak\|best` · **`2\|seed\|num\|TIER\|lives\|csv-ids\|generatorVersion`** (§4k; v1 six-field records are discarded) |
| **Daily** | **`arrow_escape_daily`** (own file) | `daily_progress`, `daily_game` | `1\|lastDate\|streak\|best\|total` · `1\|date\|seed\|lives\|csv-ids` |
| Settings | `arrow_escape_settings` | `sound_enabled`, `haptics_enabled`, `theme`, `tutorial_completed`, `onboarding_level2_completed` (§4l) | native prefs types |
| **Retention** (§4l) | **`arrow_escape_retention`** (own file) | `daily_reminder`, `retention_prompts` | see §4l |

- Writes use `commit()`, not `apply()` — the board must be on disk before Android is free to kill
  the process, and these are a few dozen bytes.
- Each repository is a process singleton (`get(context)` + double-checked lock), because two
  instances over the same prefs would drift apart across a configuration change.
- `DailyRepository` is the most isolated: **its own preferences file**, and it holds no reference
  it could use to reach the others. A corrupt daily save cannot damage campaign, endless or
  settings — verified on-device by reading all three XML files.
- `DailyRepository.loadInProgress()` discards a save from an earlier date *and wipes it*, so
  yesterday's board can never stand in for today's.
- Sanity ceilings: `EndlessProgress.MAX_PUZZLE = 1_000_000`, `DailyProgress.MAX_COUNT = 1_000_000`.
- `DailyRepository.parseProgress` rejects internally inconsistent records (a streak with no date
  behind it, a total smaller than its own streak).

Process-death restoration works for all three modes: a generated board is rebuilt from
`(seed, tier)` or `(date)` and then filtered to the surviving ids; if any saved id is not on the
regenerated board the save is dropped rather than producing a board that never existed.

**Campaign layout versioning (Discovery Phase 2).** A saved Campaign board is only a list of surviving
arrow ids, and an id is a position in a layout. Redrawing all 30 layouts would have left every old
save *valid* — ids in range, level exists — and silently restored them onto different arrows. So the
in-progress record is now **version 3 and carries `Levels.LAYOUT_VERSION`**; `ProgressRepository`
reads version 3 only, and a record with any other version, field count or layout value is discarded
and wiped like any other corrupt save. Versions 1 and 2 predate the field, so they are layout 1 by
definition and are discarded too: **a player part-way through a level at upgrade time restarts that
level — nothing else.** `player_progress` (version 2) is not touched, so completed levels, best stars
and the unlock ceiling all survive (level ids did not change). Endless, Daily and Settings formats are
untouched. Any future layout change = bump `Levels.LAYOUT_VERSION`; no format change is needed again.

---

## 11. Tutorial & hints — FULLY IMPLEMENTED (Phase 5)

### `tutorial/TutorialState.kt`
Pure state machine, four steps, driven by what the player *did* — no timer, no Next button.

| Step | Caption | Emphasises? |
|---|---|---|
| `TAP_FREE` | "Tap an arrow with a clear path" | yes |
| `BLOCKED` | "Another arrow is blocking its path" | no — the board already pulses the blocker |
| `CHAIN` | "Nice! It escaped ✨" (§4l; the "free other arrows" idea moved to Level 2's lesson) | no |
| `FINISHED` | (empty) | no |

Transitions: TAP_FREE/BLOCKED + escape → CHAIN · TAP_FREE/BLOCKED + blocked → BLOCKED ·
CHAIN + anything → FINISHED · board ends → FINISHED from any step.
A run of blocked taps deliberately does not advance — the explanation stays until the player gets
one right — so what is bounded is the number of *successful* taps (≤2), not taps.

Shows on the first-ever Campaign Level 1 only (since Discovery Phase 2, the Cloud — see §6; the state
machine never named an arrow, so the redraw needed no change here). **Since §4l** `tutorial_completed` is written at the *first
escape* (`TutorialStep.isTaught`: `CHAIN` or later), not when the lesson ends — a player who tapped the guided arrow and backed
out has been taught — and `BLOCKED`/`TAP_FREE` also show the animated hand (`showsHand`). **Replay Tutorial does not clear the
flag**; it opens `GameMode.Tutorial` manually, and the hand shows there too. Level 2 has its own lesson, `DependencyLesson` (§4l).

`tutorial/TutorialFlagStore.kt` is a two-lesson interface (Level 1, Level 2) implemented by `SettingsRepository`,
which is what keeps `GameViewModel` constructible without a `Context`.

### `game/HintEngine.kt`
Picks the free arrow that unblocks the most others, lowest id breaking ties. Both halves matter:
usefulness means the hint teaches the rule (removals unblock things) rather than pointing at an
arrow already aimed off the edge; the id tie-break makes it a pure function of the board.
Every candidate goes through `MoveValidator`, so pointing at a blocked arrow is unreachable by
construction. Returns null for an empty board or (unreachably) a stuck one.

Hint UX: compact outlined pill with a hand-drawn `BulbGlyph`, beside the footer line rather than
in the already-crowded header. Teal `secondary` glow breathing 760ms each way between 0.35 and 1.0
alpha, plus border, elevation and a 6% scale. Auto-fades after `HINT_VISIBLE_MS = 4200`.
Cleared by any resolved tap, restart, mode change or level change. Nonce-guarded so a stale timer
cannot cancel a live hint. Free — no ads, no currency.

---

## 12. Animation & visual effects — FULLY IMPLEMENTED

All Compose `Animatable`/`animateFloatAsState`. No Lottie, no `MotionLayout`. The only animated
image asset anywhere is the world backdrop, and it is moved by a `graphicsLayer`, never redrawn
(§13c).

| Effect | Detail |
|---|---|
| Arrow launch | 70ms wind-up *against* travel, then 300ms `LinearOutSlowInEasing` flight off-board + fade in the last 45% |
| In-flight arrows | leave `GameState` but keep rendering via `EscapingArrow(flightId, tile)`; `flightId` is unique per launch so a late callback from a previous level cannot affect the current one |
| Blocked tap | 3× shake at ±13% cell width, 55ms per step, + red colour flash lerped into the tile gradient |
| Blocker pulse | the arrow that *did* the blocking pulses 240ms up / 480ms down with a red border and 11% scale — teaches the rule without ever marking which arrows are free |
| Hint/tutorial glow | teal border + elevation + 6% scale, looping breath; stands down while a shake runs so the two never fight over the same edge |
| Press | 0.92 scale, 90ms, `indication = null` |
| Lives | hearts scale 1.0 ↔ 0.8 over 220ms |
| Progress bar | 320ms `FastOutSlowInEasing`, primary→secondary horizontal gradient |
| Result badge | spring disc (`DampingRatioMediumBouncy`) then a tick/cross that strokes itself on via `PathMeasure.getSegment` |
| Screen transition | `AnimatedContent`, 180ms fade in / 140ms fade out |
| Arrow glyph | `ArrowGlyph.kt` — authored once pointing UP in a unit square, points rotated per direction so all four are pixel-identical; drop shadow stays offset downward in screen space |

**Reduced motion** (`ui/ReducedMotion.kt`): reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0`,
wrapped in `runCatching` (some OEM builds throw). Only *looping decorative* motion checks it —
the hint glow, the win celebration (§12b) and the whole animated background (§13c) —
the one-shot arrow flight is the game telling you what happened and keeps playing.

**Board rendering note:** the board surface is a sibling `Box`, not a `Card` wrapping the tiles,
because a Card clips children and would cut escaping arrows off at the edge. One logical cell =
one full touch target, with the visible tile inset inside it.

---

## 12b. Win celebration — FULLY IMPLEMENTED (Phase 6D) — **superseded by §4k**

> The board glow + sparkle burst described here (`BoardCelebration`, `WinCelebration.glow/burst`, the `simplified` / `perfect`
> flags) was **removed** in the shape-discovery pass: every win now runs the outline confirmation (§4k) on one clock and then
> the reveal layer, which owns the only particles. `rememberWinCelebration(status, kind, reducedMotion, onLock, onPeak)` still
> fires the win stinger + celebration haptic from `onPeak` and nowhere else, and `GameFeedbackEffect` still plays nothing for
> `LevelComplete`. Timings in the table below are the old ones.

`ui/Celebration.kt`. Clearing a board no longer snaps straight to the result card: the card is
held back while a short celebration plays over the board.

| t | beat |
|---|---|
| 0 ms | last arrow escapes; `GameStatus.WON`; its normal launch VFX plays untouched |
| 380 ms | lead-in over — the board's completion glow starts to rise |
| 440 ms | **peak**: `sfx_level_complete` + `HapticEffect.CELEBRATION`, sparkle burst launches |
| ~1160 ms | burst spent |
| **~1220 ms** | the existing `ResultDialog` appears, unchanged |

**Effects.** Two, both plain Compose drawing on one `Canvas` over the board — no particle engine,
no new dependency. The glow is a radial white/gold wash plus a gold→teal 2.5dp edge stroke on the
board's own rounded rect (240ms up, 430ms down). The burst is 26 precomputed sparks from the board
centre — circles and small rotating confetti rects in gold, teal, white and coral — on an ease-out
radius with a little gravity, fading over the last 45%. Trajectories are generated once per win
from a seeded `Random`, so recomposition never reshuffles them mid-flight.

**Timing lives in the UI, not the ViewModel.** `GameViewModel` still emits `GameEvent.LevelComplete`
on the frame the board resolves; what changed is that `GameFeedbackEffect` no longer *plays* it.
The win stinger and its haptic are fired by the celebration's `onPeak`, so sound, haptic and
sparkles land on the same frame. `SoundPlayer`/`Haptics` are therefore built in `GameScreen` and
passed down. No gameplay, generator, level, persistence or event logic was touched.

**Campaign has a different ending (§4f).** A Campaign clear of a level that hides a discovery is *revealed*
rather than celebrated: `rememberWinCelebration(reveal = true)` only waits out the flight, lets the result layer
in at ~380ms and fires the sound and haptic on the reveal's peak (~900ms); the glow and sparkles are drawn by the
reveal inside the result layer. The timeline below is what Endless, Daily and the tutorial still get.

**Scope.** Campaign, Endless and Daily get the full sequence. Tutorial gets a simplified one —
glow and stinger, no confetti over a lesson (~860ms). **Game Over has no celebration at all**: a
loss shows its card immediately, exactly as before.

**Reduced motion** (`rememberReducedMotion()`): particles and the glow are both dropped — they are
decorative, which is what the setting is for. The sound and the haptic still fire, because neither
is animation, and the card arrives after ~420ms.

**Layout note:** `Board` is now wrapped in a `Box` that carries the weight/width constraints so the
celebration `Canvas` can `matchParentSize()` over it. `Board` still sizes itself by aspect ratio,
and the board renders identically.


---

## 12c. Campaign stars & Perfect Escape — FULLY IMPLEMENTED (Phase 6E)

`game/StarRating.kt` (pure, 1 object, no Android). One line of arithmetic:

```
stars = (3 - blockedTaps - (1 if hintUsed)) clamped to 1..3
```

**Why the thresholds are that tight.** A blocked tap costs a life and there are three, so the
*third* blocked tap ends the board — **a winning run can only ever have 0, 1 or 2 blocked taps.**
Spacing the tiers over "3+ mistakes" would make one star unreachable, so they sit on the range the
rules actually allow: 3 = flawless, 2 = one slip *or* a hint, 1 = two slips (or a slip and a hint).
The clamp keeps the formula correct if `GameState.STARTING_LIVES` is ever raised.

**Tracked per run, not per level.** `GameViewModel` holds `blockedTapsThisRun` / `hintUsedThisRun`.
Every way of opening a board resets them (`load`, `openGeneratedBoard`), so restart, replay and
"next level" all rate the attempt in front of the player. They are counted in every mode but only
ever *read* in Campaign, which is what keeps Endless, Daily and the replayed tutorial out of the
star system without a single mode branch in the rule. The first run of the tutorial is genuine
Campaign Level 1 and is rated like any other level; `GameMode.Tutorial` persists nothing and so
rates nothing.

**The tally is on disk.** `SavedGame` grew `blockedTaps` and `hintUsed`, and `requestHint()` writes
the campaign save immediately rather than waiting for the next move — otherwise being killed after
a hint would launder the run into a Perfect Escape. This changed one existing invariant:
`HintBehaviourTest` used to assert a hint writes no save; it now asserts a hint writes *only* its
own tally and still moves no arrow and costs no life.

**Save format: version 2, both records, backward-compatible.** *(Superseded for the in-progress board
by Discovery Phase 2: that record is now version 3 and older boards are discarded — see §10. The
progress record described here is unchanged.)*

| record | v1 (still read, never written) | v2 |
|---|---|---|
| `player_progress` | `1\|highest\|current\|csv-completed` | `2\|…\|csv-id:stars` |
| `saved_game` | `1\|levelId\|lives\|csv-ids` | `2\|…\|blockedTaps\|hintUsed` |

A v1 record parses exactly as it always did and comes back with no stars, so an upgrading player
keeps every unlock, completion and in-progress board and simply has no stars on what they cleared
before. There is no rewrite pass and no flag day. **Level ids are unchanged**, which is what makes
that work. Every new field is range-checked on the way in like the rest (`stars in 1..3`,
`blockedTaps in 0..99`, the hint flag strictly `0`/`1`, no duplicate level in the stars field, and
a star against a level that was never cleared is dropped). Endless, Daily and Settings formats are
untouched.

**Best, never last.** `ProgressRepository.markCompleted(levelId, stars)` records a star only when
it beats what is there, so replaying a three-star level and making a mistake cannot take the third
star away. `PlayerProgress.bestStars` is a sparse `Map<Int, Int>` — absent means
`StarRating.NONE`.

**Result card.** `ResultContext.Campaign` carries `stars` / `bestStars` / `isNewBest`, all
`NONE`/false on a loss, so the Game Over card is byte-for-byte what it was. A Campaign win shows a
three-glyph star row that pops in one star at a time (one `Animatable` and a `scale` — the same
pair the badge already uses), then one of: **PERFECT ESCAPE** in `tertiaryContainer` gold for three
stars, "New best" when the run raised the record, or "Best ★★★ kept" when it did not. The message
line speaks to the rating. **Next Level is still the primary action and no timing changed.**

**Perfect Escape in the celebration.** `rememberWinCelebration` took one new `perfect` flag: the
same burst with more of it (26 → 42 sparks) and the glow 25% brighter, clamped. **Every duration
is identical**, so a flawless clear is not slower to leave than any other. No new effect system.

**Level Select.** A completed tile shows `★★☆` under its number in the tile's own content colour,
standing in for the tick rather than sitting beside it; a level cleared before stars existed keeps
the tick. Locked tiles are untouched and still genuinely not clickable. The tile's
`contentDescription` gains ", 2 of 3 stars"; the glyph row itself is `clearAndSetSemantics {}`.

**Deliberately not added:** stars do not gate anything. No coins, rewards, currencies, shop, skins,
achievements, star-based unlocks, ads or leaderboards, and no star rating on Endless, Daily or the
tutorial. Stars are Campaign mastery/replay feedback and nothing else. `StatsScreen` was not
touched.

---

## 13. Audio — FULLY IMPLEMENTED (Phase 6C, fixed 6C.1)

`feedback/SoundPlayer.kt` is a `SoundPool` wrapper looking up four sounds **by name at runtime**
via `resources.getIdentifier(...)`. All four assets ship, and playback is verified end-to-end on an emulator:

| enum | file | duration | size | asset RMS | gain | effective |
|---|---|---|---|---|---|---|
| `ESCAPE` | `res/raw/sfx_escape.ogg` | 190 ms | 6.3 KB | -9.9 dBFS | 0.55 | -15.1 dBFS |
| `BLOCKED` | `res/raw/sfx_blocked.ogg` | 140 ms | 5.2 KB | -18.2 dBFS | 1.00 | -18.2 dBFS |
| `LEVEL_COMPLETE` | `res/raw/sfx_level_complete.ogg` | 740 ms | 9.3 KB | -12.7 dBFS | 0.95 | -13.1 dBFS |
| `GAME_OVER` | `res/raw/sfx_game_over.ogg` | 560 ms | 6.7 KB | -15.2 dBFS | 1.00 | -15.2 dBFS |

OGG Vorbis, 44.1 kHz mono, `oggenc --quality 6`; **27.6 KB total**. `SoundPlayer.MASTER_VOLUME`
is 1.0 — the per-sound `GameSound.gain` carries the entire mix balance and is the only knob
worth touching. `ESCAPE` fires on nearly every tap, so it sits deliberately under the stingers.

**Provenance:** 100% original, synthesised from scratch by `tools/sfx/gen_sfx.py` (Python
standard library only — additive sine partials, seeded pseudo-random noise, RBJ biquads). No
samples, sound packs or third-party audio, so there is nothing to license or attribute for Play
Store release. The script carries the exact regeneration command.

### Why the first cut was inaudible (fixed)

The Phase 6C assets played correctly — `SoundPool.play()` returned valid stream ids the whole
time — but could not be heard. Two independent causes, both in the **audio content**, not the
plumbing:

1. **Mastered far too quietly.** The assets were normalised to 0.52-0.88 peak and then trimmed
   *again* at playback. `ESCAPE`, the sound that fires most, landed at roughly **-25 dBFS**
   effective. It is now -15.1 dBFS, about 10 dB louder.
2. **Half the energy was below 500 Hz, where a phone speaker reproduces almost nothing.**
   `BLOCKED` (48% below 500 Hz) was a 118-185 Hz thump and `GAME_OVER` (50%) an A4-F4-D4
   descent. Both are now re-voiced so the *same* notes ride on harmonics 2-5 with the
   fundamental pulled down and a high-pass clearing the unplayable sub — the ear restores the
   missing fundamental. Low-frequency share is now 18% for both.

The sound design, note choices and durations are unchanged; only level and spectral balance.

### Robustness

`SoundPool.load()` is asynchronous — it returns a sample id immediately, but playing before the
decode finishes fails silently. `SoundPlayer` now registers an `OnLoadCompleteListener` and a
sound only becomes playable once its load reports success, so `play()` can never address an
unready sample. A `load()` return of 0 (failure) is rejected rather than stored. Every step
remains failure-tolerant: a missing asset, a failed decode, or `play()` on a released pool can
never take the game down, and the by-name lookup means swapping any sound is a file drop with
no code change.

### Runtime verification (emulator, Pixel 7 Pro API 36)

Driven through the real UI with `adb`, confirmed by logcat and `dumpsys media.audio_flinger`:
all four samples resolve and decode (`status=0`); 12x `ESCAPE` + `LEVEL_COMPLETE` on a perfect
clear of level 7; 3x `BLOCKED` + `GAME_OVER` on running out of lives; every `play()` returned a
non-zero stream id; up to 4 concurrent active mixer tracks. Sound **OFF** → events still arrive
but no stream is ever started; Sound **ON** → playback resumes. No `SoundPlayer`/`SoundPool`
warnings in logcat.

Levels were set from measured RMS and spectral analysis, not by ear — **nobody has yet listened
to these on real hardware.** If `ESCAPE` proves fatiguing in long sessions, lower its `gain`.

The Sound toggle in Settings works, is persisted, and gates real audio.

## 13b. World backgrounds — FULLY IMPLEMENTED (Phase 6A)

Five pieces of full-screen artwork, one per `GameWorld`, in `res/drawable-nodpi/` as 1440×2560
portrait WebP (209–411 KiB each, ~1.3 MB on the APK): `bg_sky_garden`, `bg_forest`,
`bg_sunset_canyon`, `bg_crystal_night`, `bg_cosmic`. `nodpi` on purpose, so they decode at the
authored size instead of being scaled up for a density bucket.

`ui/world/` is four small files:

| File | Android? | What it is |
|---|---|---|
| `GameWorld.kt` | no | the five-case enum, stable `id`s, `PROGRESSION` order |
| `GameWorlds.kt` | no | **which world a board is in** — the part with rules |
| `WorldStyle.kt` | Compose + `R` | drawable, accent colour, per-world scrim strengths |
| `WorldBackground.kt` | Compose | the one renderer, `ContentScale.Crop`, centred |

**Nothing about a world is persisted.** Every mapping is a pure function of a number the game
already saves, which is why no save format changed, no field needed range-checking, and no
`GENERATOR_VERSION` bump was owed:

- **Campaign** — `levelId`, six levels per world: 1–6 Sky Garden, 7–12 Forest, 13–18 Sunset
  Canyon, 19–24 Crystal Night, 25–30 Cosmic. Past level 30 clamps to Cosmic rather than wrapping.
- **Endless** — `puzzleNumber`, one world per puzzle, cycling: 1 Sky Garden … 5 Cosmic, 6 Sky
  Garden again.
- **Daily** — `SeededRandom.derive(seed, "WRLD")` mod 5, so it reuses the five rather than needing
  a sixth asset. Salted so it does not correlate with the tier draw, which is salted off the same
  seed. Measured 66–78 days per world over 2026. **The daily seed derivation itself is untouched.**
- **Tutorial** — Sky Garden.

Daily is told apart by a **restrained golden layer over whichever world it landed in**, not by its
own backdrop: a warm gradient wash in the background, a hairline `tertiary` edge on the HUD card
and the board, and the tier badge in `tertiaryContainer` instead of `secondaryContainer`. There is
no second `GameScreen` and no `GameMode` branch in the renderer — the world and a boolean are
parameters.

Chrome over artwork is translucent, with the alphas in `GameScreen.kt`: HUD card 0.92, board
surface 0.90 (plus 7% of the world's accent mixed in), footer line and Hint pill 0.82, all with a
1dp hairline edge. **No runtime blur anywhere** — it would cost more than the rest of the screen
and buy nothing a flat translucent surface does not.

All of it is decorative and none of it reaches TalkBack: the `Image` takes a null
`contentDescription`, which contributes no semantics node, and the scrims are bare `Box`es.

## 13c. Animated world backgrounds — FULLY IMPLEMENTED (Phase 6F)

The five WebPs are **unchanged** — not regenerated, replaced or re-encoded. What changed is that
the scene they paint is now very slowly alive, so the artwork stops reading as a screenshot. The
gameplay layer is untouched: board, arrows, HUD, progress bar, Hint pill, tutorial captions and
result cards are all siblings of the background in `GameScreen` and none of them is in the animated
tree.

`ui/world/` is now six files — the two new ones are the whole of this phase:

| File | What it is |
|---|---|
| `WorldAmbience.kt` | **data**: five rows saying how each world moves. `ImageMotion`, `AmbientDrift`, `AmbientParticles`, `Twinkle`. No `R`, no drawing. |
| `AmbientLayers.kt` | **the one renderer**: the master phase, the image-motion `Modifier`, the drift layer, the particle field and its draw. Knows nothing about which world it is painting. |

`WorldBackground.kt` composes them; `GameScreen.kt` was **not touched** — `WorldBackground` gained a
`reducedMotion` parameter that defaults to `rememberReducedMotion()`, so the existing call site is
unchanged.

**One animated `Float` drives the entire screen.** A single `rememberInfiniteTransition` ramps a
phase 0→1 linearly over `AMBIENCE_CYCLE_MS = 48_000` and restarts. Zoom, drift, every particle's
wander and every twinkle are closed-form functions of it, each on an **integer** number of cycles —
so the scene is exactly periodic and the wrap from 1 back to 0 is not a frame anyone can see. There
is no per-element animation, no `withFrameNanos` loop and no cross-fade.

**Nothing recomposes, re-measures or allocates per frame.** The phase is only ever read inside a
`graphicsLayer` block or a `Canvas` draw lambda. Drift brushes are built once per world; particles
are expanded once per world by `ambientMotes()`. Worst case on screen (Cosmic, 22 points) is 22
`drawCircle` calls plus two GPU layer transforms.

| Layer | How | Cost |
|---|---|---|
| Artwork | `graphicsLayer` on the existing `Image`: zoom 1.03–1.08 over 16–24s, plus a drift of 0.4–1.2% of the screen on a different beat per axis | one GPU layer transform |
| Atmosphere | 1–2 `Box`es with a **static** radial-gradient background, moved/scaled by `graphicsLayer` | one GPU layer each, no redraw |
| Particles | one `Canvas`, 16–22 precomputed points, circles only | ≤22 `drawCircle` |

**The one invariant.** `ContentScale.Crop` already fits the artwork exactly, so the only slack to
drift into is what the zoom creates: `drift < (zoomFrom - 1) / 2`, checked against the *smallest*
scale in the cycle. Every world satisfies it (tightest is Sky Garden, 0.012 against 0.0175). Break
it and the artwork's edge walks into frame at the bottom of the swell. `clipToBounds()` on the
background root is the belt-and-braces.

Per world:

| World | Ambience |
|---|---|
| Sky Garden | two cloud washes crossing at different speeds · 12 white/pale-blue motes · 5 occasional sparkles |
| Forest | two low mist bands · 10 haloed fireflies in the lower half, breathing · 6 faint motes above |
| Sunset Canyon | two warm haze blobs that slide **and swell** — the cheap stand-in for heat shimmer, no distortion shader · 16 small dim dust specks |
| Crystal Night | one slow violet wash · 12 haloed motes · 5 sparkles. **The crystals in the artwork do not pulse** — five glowing forms breathing together turns a calm scene into a slot machine |
| Cosmic | one barely-moving nebula wash · 18 stars that breathe rather than flash · 4 sparkles. The gentlest zoom of the five, because stars read as still |

Particles sit **above the artwork and below the scrim**, so a mote that wanders behind the HUD or
the footer is dimmed by the scrim that was already there — motion is quietest exactly where chrome
is. They are behind the board's 0.90-alpha surface, which all but erases them over the grid.

**The field is deterministic.** `ambientMotes()` seeds from `GameWorld.id.hashCode()` — the stable
string, not the enum ordinal, and `String.hashCode` is a specified function — so the same world
always comes back with the same sky, including after process death, and reordering the enum cannot
reshuffle it. Nothing is persisted and no save format changed.

**Reduced motion** (`rememberReducedMotion()`): layers 2–4 are not composed at all. No infinite
transition runs, no washes, no particles — the still WebP exactly as Phase 6A drew it. This is the
strictest reading of the setting and also the cheapest, which is the right place to land for a layer
that is pure decoration. Gameplay animation is unaffected and keeps its own rules.

**Measured on the emulator** (Pixel-class, 1440×3120), two frames 8s apart, per region:

| Region | Animated | Reduced motion |
|---|---|---|
| Background (sky / nebula) | 65–75% of pixels changed | — |
| **Board interior** | **0.00–0.02% changed, mean Δ 0.1** | — |
| HUD card interior | 0.2–0.6% changed, mean Δ 0.5, max Δ 14 | — |
| Whole screen over 9s | — | **273 px of 4.49M — the status-bar clock** |

The board does not move. The residue on the HUD and board is the *existing* chrome translucency
(0.92 / 0.90 alpha) letting 8–10% of the moving artwork through — the surfaces themselves are
stationary, and at mean Δ 0.5 it is well under perception. The artwork's left/right edge columns
never reach bare background at any point in the cycle, confirming the crop stays a crop.

**Deliberately not added:** no particle engine, no spawning, lifetimes or pooling, no Lottie, no
runtime blur, no distortion shader, no new dependency, no second `GameScreen`, no new world-selection
logic, no parallax or any response to input, and no preloading of the four worlds not on screen.

## 14. Theme — FULLY IMPLEMENTED

`ui/Theme.kt`, hand-authored light and dark `ColorScheme`s. Indigo primary `#3F51D5` / `#B6C1FF`,
teal secondary `#00A99D` / `#52DED0`. Three modes: System / Light / Dark, persisted.

**`tertiary` is now defined in both schemes** (Phase 6A), closing the gap that made the Phase 5
hint glow come out as Material's default muted brown. It is a warm gold — light
`#8B5E00` / `#FFDEA8`, dark `#F2C063` / `#5E4000` — chosen as the Daily Challenge's accent over
the five world backdrops, and it is what the Daily tier badge and the Daily HUD/board edges use.
The hint glow stays `secondary`; it was only ever brown by accident and teal is the right colour
for it.

Also fixed in Phase 6A: the dark scheme's `secondaryContainer` was written `0xFF12474170` — nine
hex digits, so the leading `FF` was truncated and what survived was a slate blue at **7% alpha**.
Every dark-theme surface using it, the endless/daily tier badge included, was effectively
unpainted. It is now `#124741`.

**Typography lives here too** (Phase 6G): `AppTypography` keeps Material 3's sizes and fixes the
*weight* per role, so screens no longer each add their own `fontWeight`. See §4b.

`res/values/colors.xml` holds only stock `black`/`white` and is unused by Compose.
`res/values/themes.xml` + `values-night/themes.xml` exist only to give the Activity a launch theme.

---

## 15. Accessibility — FULLY IMPLEMENTED

- Every arrow: `"Up pointing arrow, row 3, column 5"` — **direction and position only, never
  whether the path is clear.** The one exception is an arrow the player explicitly asked about,
  which appends `", suggested"`. A hint you cannot hear is not a hint.
- Labels sit on the node that can actually be *activated*, not on the decorative icon inside it.
- Merged nodes where fragments would be meaningless: `"3 of 3 lives remaining"`,
  `"18 of 18 arrows remaining"`, `"Levels cleared, 12 of 30"`,
  `"Daily Challenge, Sunday 4 October, ✓ Completed today, 1 day streak"`.
- `"Hint, highlight an arrow that can escape"`, `"Restart level"`, `"Settings"`, `"Back to home"`,
  `"Replay tutorial, play the first board with hints again"`.
- Decorative nodes cleared with `clearAndSetSemantics {}` (hearts, toggle label columns).
- `heading()` on every screen title and section label.
- 48dp minimum touch target enforced everywhere; the board gives a whole cell to the tap while the
  visible tile is inset inside it.
- Locked level tiles are genuinely not clickable, so they are not announced as actionable.
- Settings sheet is `skipPartiallyExpanded` + `verticalScroll` so every row is reachable at large
  system font sizes.
- Reduced-motion respected for looping animation.

**Discoveries (§4f).** The reveal speaks as one heading ("New discovery. Butterfly. Forest collection, 4 of 6
discovered."); the artwork, halo, pips and sparkles are decoration. Album slots speak "Butterfly, discovered, Forest"
/ "Undiscovered item, Level 11" / "Locked discovery" — the labels live on `DiscoverySlot`, whose mystery and locked
variants hold no discovery to name. Level Select tiles speak "Level 10, Butterfly discovered, 2 of 3 stars" /
"Level 11, undiscovered. This is your next mystery." / "Level 12, locked". The album leaves the accessibility
tree while its closer look is open; the reveal's buttons swallow presses until they have arrived. Every new target
is ≥ 48dp.

Everything above this paragraph was verified on-device with `uiautomator dump` (Phases 5–6); **§4f has not been
verified on a device**.

---

## 16. Test coverage — 914 tests, 0 failures, 3 skipped (gated stress tests; they pass with the flag)

911 fast (~25s) + 3 stress (gated). The table below lists the suites as of §4j; the shape-discovery additions are in §4k ("Tests added") and the retention/onboarding/launch ones (177) in §4l ("Tests added"). **No instrumentation tests** —
`app/src/androidTest/java` exists but is empty. No Compose UI tests, no screenshot tests.

| Suite | n | Covers |
|---|---|---|
| `ui.DailyProgressionTest` | 32 | daily end-to-end through the ViewModel, streaks, restoration, isolation |
| `ui.HintBehaviourTest` | 24 | what a hint points at, when it clears, every mode, finished boards |
| `progress.StarPersistenceTest` | 17 | star records, best-never-downgrades, v1 progress compatibility, old boards discarded, corruption |
| `ui.CampaignStarsTest` | 18 | all three tiers on a real board, run resets, process death, other modes unrated |
| `game.StarRatingTest` | 12 | the rule's boundaries, all three tiers reachable, monotonicity |
| `ui.TutorialFlowTest` | 26 | first-run only, persistence at the first escape, replay, must-not-touch-Campaign |
| `ui.EndlessProgressionTest` | 24 | endless end-to-end, replay honesty, process death |
| `ui.GameProgressionTest` | 25 | campaign end-to-end, unlock/resume, old-layout board restarts clean |
| `daily.DailyRepositoryTest` | 22 | streak rules, corruption, yesterday's board |
| `progress.ProgressRepositoryTest` | 23 | campaign save parsing, layout-version discard, corruption |
| `endless.PuzzleMetricsTest` | 20 | score components, depth curve regressions |
| `endless.EndlessRepositoryTest` | 19 | endless save parsing, corruption |
| `daily.DailyChallengeTest` | 18 | seed determinism, tier bounds, a year of solvable boards |
| `endless.PuzzleGeneratorTest` | 16 | determinism, witnesses, tier bounds, column cap |
| `tutorial.TutorialStateTest` | 19 | exhaustive state machine, hand/taught flags, the two lesson flags are independent |
| `game.HintEngineTest` | 14 | never-blocked, deterministic, every campaign + generated board |
| `time.GameDateTest` | 14 | **checked against `java.time` over 200 years of consecutive days** |
| `endless.EndlessTierTest` | 14 | quality rules, one broken rule at a time |
| `game.LevelCatalogueTest` | 14 | all 30 solvable via independent DFS + replayed witness, per-world arrow bands, several openers |
| `game.CampaignShapesTest` | 7 | every level's occupied cells = its silhouette blueprint, Level 1 still carries the tutorial lesson, writes the shape review table |
| `game.ShapeAuthoringTest` | 5 | the dev-only authoring helper builds verified, silhouette-faithful boards for every blueprint |
| `endless.SeededRandomTest` | 12 | distribution, reproducibility |
| `endless.BoardAnalysisTest` | 11 | peel, verifyOrder, runs |
| `ui.GameViewModelTest` | 11 | tap safety, animation bookkeeping |
| `ui.UnblockedFeedbackTest` | 9 | the "your move opened these up" pulse — derived from the board, not from any layout's ids |
| `progress.PersistenceIsolationTest` | 10 | all four stores crossed against each other |
| `game.GameStateTest` / `MoveValidatorTest` / `LevelProgressionTest` / `LevelsTest` / `CampaignRegressionTest` | 8/8/7/6/7 | rules, progression, catalogue (`CampaignRegressionTest` pins the 30 layouts by SHA-256, counts, dimensions and `LAYOUT_VERSION`) |
| `ui.world.GameWorldsTest` | 15 | world selection in all four modes, determinism, catalogue coverage |
| `ui.BoardPresentationTest` | 9 | mode → grid/shape, occupied bounds of all 30 levels, `ShapeFit` cell sizing and its 48dp floor at 320dp |
| `ui.world.CampaignDiscoveriesTest` | 17 | 30 discoveries, ids 1..30, six per world, unique art keys, invalid ids, collected state |
| `ui.world.DiscoveryStateTest` | 21 | the three slot states; **spoilers** (no non-collected slot can show or say a name/key at any of 0..30 cleared, and holds no discovery by reflection); world and global counts; all-30; latest-found; first-clear vs replay; the result's count includes the clear |
| `ui.DiscoveryRevealFlowTest` | 14 | the reveal through the real ViewModel: first clear vs replay vs relaunch, loss then retry, 1 star discovers as 3 do, **the win is on disk before any animation finishes**, count includes the clear, Level 30 completes the album, results clear with the run, Endless/Daily/tutorial reveal nothing |
| `ui.discovery.DiscoveryArtRegistryTest` | 9 | every catalogue art key (and the thirty in the brief) resolves, none orphaned, every path parses on a plain JVM, nothing off the 100×100 canvas, translucent details are never softened |
| `ui.discovery.RevealScheduleTest` | 11 | the reveal timeline's shape: art before peak, peak 900–1100ms, words in order, buttons never before visible, available ~1.5s and under 2s, reduced motion faster with no burst |
| `ui.ResultPresentationTest` | 5 | when a result is showing (win after its celebration, loss at once), the gameplay chrome is suppressed exactly then, the chrome fades over the scrim's span |
| `ui.LevelTileStateTest` | 6 | gold tile iff cleared and 3 best stars — every level × 0..3 stars; 1/2 stars and no-record clears are completed, uncleared is never gold |
| `ui.HomeOrderTest` | 3 | Home is Continue → Discoveries → Daily → Modes, each shown once |
| `ui.ScreenNavigationTest` | 2 | the back stack's enum is append-only (saved as ordinals) |
| `ui.discovery.DiscoveryArtSheetWriterTest` | 1 | dev only: writes the SVG review sheets; asserts nothing |
| `ui.PlayerStatsTest` | 7 | stats read from the right record; discoveries counted against the catalogue |
| `settings.GameSettingsTest` | 3 | defaults, theme keys |
| `game.LevelDifficultyReportTest` | 1 | writes the dev report, asserts no cliffs |
| `endless.GeneratorStressTest` | 3 | **gated** — 5,000 boards verified, distinct-layout check, reproducibility |

Testing conventions worth preserving:
- **A relaunch is a new `GameViewModel` over the same store.** That is exactly what process death
  looks like from inside.
- **No date-dependent test reads the machine clock** — all use `FixedDateProvider`.
- Boards are cleared "the honest way": only ever tapping an arrow `MoveValidator` says can escape.
- Loop guards (`check(guard++ < 500)`) everywhere, so a logic regression fails rather than hangs.

**Coverage gaps:** no Compose UI/instrumentation tests; `SettingsRepository` is untestable as a
unit (private constructor taking `Context`), so settings-corruption isolation is argued
structurally (separate prefs file) rather than tested.

---

## 17. Known bugs, placeholders and gaps

### Fixed during Phase 5 (recorded so they are not reintroduced)
1. **First daily clear reported itself as a replay.** `persistDaily` flipped `alreadyCleared`
   before the dialog read it. Fixed by giving the flag an open-time meaning; double-counting is
   prevented by idempotent `completing()`, which is where it belonged.
2. **Hint glow was Material's default brown** because the theme defines no `tertiary` (§14).

### Fixed during Phase 6A
3. **`tertiary` undefined in both schemes** — now a warm gold pair in each (§14).
4. **Dark `secondaryContainer` was a 7%-alpha slate blue**, from a nine-digit colour literal whose
   leading `FF` was truncated. Invisible against a flat gradient; would not have survived artwork.

### Open / accepted
| | Severity | Note |
|---|---|---|
| **Stock launcher icon** | medium | `ic_launcher_foreground.xml` is still the Android Studio green-robot template. Needs a real mark — an `ArrowGlyph`-derived adaptive icon would fit. |
| Device clock manipulates streaks | accepted | documented in `DateProvider` KDoc |
| Worst-case generation 1.9→2.6 ms | low | single noisy sample; average improved |
| Portrait only | by design | no landscape layout exists |
| `res/layout/` empty; vestigial `.keep` still in `res/raw/` | cosmetic | vestigial |
| Stock `TODO` comment in `res/xml/data_extraction_rules.xml` | cosmetic | the only TODO in the repo |
| `allowBackup="true"` with default rules | low | saves would restore across devices; may want the daily save excluded. A restored `arrow_escape_retention` can say "reminder on" on a device that has not granted notifications: the Settings row then shows its blocked state, and `syncOnStartup` re-plans the work |
| `versionCode = 1`, `versionName = "1.0"` | — | never bumped |
| `isMinifyEnabled = false` in release | low | no ProGuard/R8 shrinking configured |

**There are no `TODO`/`FIXME`/`stub` markers in any Kotlin source.** No feature is half-wired:
every switch over `GameMode` is exhaustive, every repository has a real implementation.

---

## 18. Explicitly NOT implemented

Deferred by instruction until retention is validated. **None of this is stubbed, scaffolded or
hinted at anywhere in the codebase** — adding any of it is greenfield work.

Monetisation: banner / interstitial / rewarded ads, purchases, currencies, shop, skins.
Backend: Firebase, accounts, cloud saves, leaderboards, remote/push notifications (the *local* daily reminder exists — §4l),
analytics, crash reporting, remote config.
Social & meta: achievements, friend streaks, daily-challenge countdown timer (a plain share sheet and a Rate action exist — §4l).
Content: level editor, additional level packs beyond 30, themes/skins, music (the four SFX
slots are filled; §13), tutorial for advanced concepts.
Platform: landscape layout, tablet-specific layout (widths are merely capped), wear/TV, widgets,
app shortcuts, general deep links (only the reminder's launch destination exists — §4l), localisation (`strings.xml` holds one string; **all UI copy is
hardcoded English in composables**).
Engineering: instrumentation/Compose tests, CI, R8 config, baseline profiles, signing config,
Play Store listing assets.

The hint system was explicitly built "free for now" to prepare for rewarded hints later — the
seam is `GameViewModel.requestHint()` / `canHint()`.

---

## 19. File map — where to work

**Rules (pure, no Android):**
`game/MoveValidator.kt` `game/GameState.kt` `game/HintEngine.kt` `game/StarRating.kt`
`endless/BoardAnalysis.kt`

**Generation:** `endless/PuzzleGenerator.kt` `endless/PuzzleMetrics.kt` `endless/EndlessTier.kt`
`endless/SeededRandom.kt` `daily/DailyChallenge.kt`

**Persistence:** `progress/ProgressRepository.kt` `endless/EndlessRepository.kt`
`daily/DailyRepository.kt` `settings/SettingsRepository.kt` `progress/ProgressStore.kt`

**State:** `ui/GameViewModel.kt` (695 lines — the hub) `ui/GameMode.kt` `tutorial/TutorialState.kt`

**Worlds:** `ui/world/GameWorld.kt` `ui/world/GameWorlds.kt` `ui/world/CampaignDiscovery.kt` (all pure) ·
`ui/world/WorldStyle.kt` `ui/world/WorldBackground.kt` `ui/world/WorldAmbience.kt`
`ui/world/AmbientLayers.kt` (Compose)

**UI:** `ui/DesignSystem.kt` (tokens, glass surfaces, buttons, chips, bars — start here before
touching any screen) `ui/GameIcons.kt` (Canvas glyphs) `ui/HomeScreen.kt` `ui/LevelSelectScreen.kt`
`ui/StatsScreen.kt` `ui/SettingsScreen.kt` `ui/DailyScreen.kt` `ui/ResultScreen.kt`
`ui/GameScreen.kt` (board + wiring) `ui/ResultPresentation.kt` (when a result shows, what it hides, how long it takes — pure) `ui/BoardPresentation.kt` (grid/shape choice, occupied bounds, cell fit — pure) `ui/CampaignShape.kt` (shape halo + arrow piece finish) `ui/GameHud.kt` (HUD + bottom controls) `ui/ArrowEscapeApp.kt`
(nav + transitions) `ui/Theme.kt` (colours **and** the type hierarchy) · artwork for menus:
`ui/world/WorldArt.kt` `ui/world/MenuBackdrop.kt`

**Discoveries (§4f):** `ui/world/DiscoveryState.kt` (pure: `DiscoverySlot`, `WorldCollection`, `DiscoveryCollection`,
`DiscoveryResult`) · `ui/discovery/` — `DiscoveryArtSpec.kt` (model, DSL, primitives), `SkyDiscoveryArt.kt` …
`CosmicDiscoveryArt.kt` (the 30 drawings), `DiscoveryArtRegistry.kt` (key → drawing), `DiscoveryArtwork.kt` (the one
renderer), `RevealSchedule.kt` (timeline, pure), `DiscoveryReveal.kt` (world glow, halo, sparkles) ·
`ui/DiscoveryResultContent.kt` (the Campaign result) · `ui/DiscoveriesScreen.kt` (the album) ·
`ui/DiscoveryComponents.kt` (mystery mark, world heading, complete chip)

**Shape discovery (§4k):** `shape/ShapeMask.kt` `shape/ShapeContour.kt` (`GridContourTracer`) `shape/MysteryShapes.kt` (templates +
catalogue) `shape/ShapePuzzleGenerator.kt` (`ShapeTierProfile`, the generator) `shape/MysteryShapePuzzles.kt` (Endless selection, `GENERATOR_VERSION`)
· `ui/CompletionFlow.kt` (phases + schedules, pure) `ui/Celebration.kt` (the clock + cues) `ui/ShapeConfirm.kt` (the outline)
`ui/ShapeRevealContent.kt` (Daily/Endless reveal, `ShapeRevealCopy`) · dev-only, test source set: `shape/ShapeSheet.kt`,
`shape/ShapeSheetWriterTest.kt` (SVG review sheets)

**Content:** `game/Level.kt` (the 30 layouts + `LAYOUT_VERSION`) · dev-only, test source set:
`game/CampaignShapes.kt` (silhouette blueprints) · `game/ShapeAuthoring.kt` (shape-constrained reverse construction + measurement)

**Time:** `time/GameDate.kt` `time/DateProvider.kt`

**Retention, onboarding & launch (§4l):**
`startup/` — `StartupDestination.kt` (+ `LaunchIntents`, `LaunchRouter`), `StartupProgress.kt` (tasks + target), `StartupCoordinator.kt`,
`AppStartup.kt` (the real steps), `StartupViewModel.kt`, `SplashProgress.kt` (smoothing + finish rules), `SplashChoreography.kt` (timing) — all pure
except the last two files · `ui/AppRoot.kt` (splash ▸ game) `ui/SplashScreen.kt` `MainActivity.kt` (system splash, intents) ·
`notification/` — `ReminderPreference.kt`, `ReminderSchedule.kt` (time + policy, pure), `ReminderController.kt` (+ `ReminderScheduler`),
`WorkManagerReminderScheduler.kt`, `DailyReminderWorker.kt`, `ReminderNotifier.kt` (channel, notification, tap intent),
`NotificationPermission.kt`, `ReminderServices.kt` · `retention/` — `RetentionPromptState.kt` (+ repository), `RetentionPromptPolicy.kt` (pure),
`RetentionCoordinator.kt`, `StoreActions.kt` (review, listing, share, `ReviewOutcome`), `StoreLinks.kt` · `tutorial/` — `DependencyLesson.kt`,
`OnboardingGuide.kt`, `OnboardingCoach.kt` (all pure) · `ui/TutorialHand.kt` `ui/RetentionUi.kt` `ui/RetentionDialogs.kt` `ui/PromptTiming.kt`
`ui/RetentionGlyphs.kt` · res: `values/themes.xml` (`Theme.ArrowEscape.Starting`), `drawable/splash_icon_blank.xml`, `drawable/ic_stat_arrow.xml`

### Invariants a future change must not break
1. **Never generate-then-test for solvability.** Construction backwards is what makes the witness
   free and the generator fast.
2. **Columns ≤ 6.** A 7th drops cells under 48dp at 320dp.
3. **Arrow ids are reading-order and are persisted.** Level ids too.
4. **Accessibility must not leak which arrows are free** (except an explicit hint).
5. **No production code calls the clock directly** — always `DateProvider`.
6. **Each repository touches only its own keys**, and Daily only its own file.
7. **Numeric difficulty score is debug-only.** Players see Beginner…Expert names.
8. `GameViewModel` must stay constructible without a `Context`, or ~140 tests become
   instrumentation tests.
9. Changing the daily seed derivation, `GameDate.iso`, or tier mapping requires bumping
   `DailyChallenge.GENERATOR_VERSION`. A *world* mapping does not — a world decides what a board
   is painted on, never which board is generated.
10. **A world is derived, never stored** — and so is a discovery's collected state (§4d).
    `ui/world/GameWorld.kt`, `GameWorlds.kt` and `CampaignDiscovery.kt` stay free of
    Compose, Android and `R` so the mappings keep being unit-testable on a plain JVM.
11. **Bump `Levels.LAYOUT_VERSION` whenever a Campaign layout changes** (§10): saved boards are arrow
    ids, ids are positions in a layout, and a stale save would otherwise restore silently onto the
    wrong arrows. `CampaignRegressionTest` fails on any layout change and says so.
12. **Only `DiscoverySlot.Collected` carries a `CampaignDiscovery`** (§4f). A screen that is not handed a collected slot
    has no name to print, no artwork to draw and nothing to speak, so what a player has not found cannot leak — not
    through text, TalkBack, content descriptions, Level Select, Home or the closer look. Do not add a field on a mystery
    or locked slot, and do not look a discovery up by level id from UI code: go through `DiscoveryCollection` /
    `DiscoverySlot.of`. `DiscoveryStateTest` fails if either regresses.
13. **Art keys are permanent and `DiscoveryArtRegistry` is their only resolver** (§4f). The registry's keys must equal
    the catalogue's; `DiscoveryArtRegistryTest` fails on a missing or orphaned drawing. Draw discoveries only through
    `DiscoveryArtwork` — there is no second artwork system.
14. **The reveal is presentation about a run that is already saved** (§4f). `GameViewModel` publishes
    `campaignDiscovery` only after `persistCampaign` has written the win, and `onPeak` (sound + haptic) is called by
    `rememberWinCelebration` and nowhere else. No "seen" flag is persisted.
15. **The shape is the clue, and the outline is the puzzle's own cells** (§4k). A confirmation outline is only ever traced from
    `ShapeMask.of(level)` — the level's *full* arrow list — never from an icon, a name or what is left on the board, and it is
    drawn in the formation's own box at its own cell size so nothing moves. Do not introduce a second source for it.
16. **A mystery shape's name exists only after the win** (§4k). `GameMode`, `Level.name`, `GameState` and every flow a screen
    collects while a Daily/Endless board is in play are name-free; the name is `GameViewModel.shapeReveal`, published after the
    progress write and cleared when another board opens. `ui.ShapeModesTest` scans all of it.
17. **Changing the Mystery catalogue, `ShapeTierProfile`'s construction or the selection changes boards:** bump
    `MysteryShapePuzzles.GENERATOR_VERSION` (Endless saves) and `DailyChallenge.GENERATOR_VERSION` (Daily seeds). Template
    ids are permanent. `MoveValidator` stays the only rule: the generator may not decide legality itself.
18. **The completion phase is a pure function of (status, launch-clock ms, schedule)** (§4k) and `onPeak` is called once, from
    `rememberWinCelebration`. A loss never starts the clock and never confirms a shape.
19. **Retention asks only after the reward, and never claims to know a rating happened** (§4l). Prompts start after `PromptTiming.settleDelayMs`
    from the result layer, only for won Campaign levels, once per session, never over a world/album completion; the record says
    `rateActionTapped`, never "rated". No reward, no gating, no "do you like it?" branch. A new permission request must be at the
    player's request, never on the first frame.
20. **The loading screen is not a destination and the launch destination is resolved once** (§4l): `LaunchRouter.consume` → the stack the app
    starts with; a recreated activity never re-offers the intent. Startup steps must each have a timeout and may not trap the player.
