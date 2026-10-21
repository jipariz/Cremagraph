# OnlyShots splash: plotting the shot (Signal tile)

The splash plays the logo's own idea once: the signal tile cuts in, the pen lands on its edge and plots the shot's
trace across it, the stem drops, the head ticks to mark "now", and the mark hands off to the app. Everything is drawn
from the logo kit's geometry (`design/logo/src/build.py`, regular weight) on ink `#0B0B0B`: the tile signal `#FF5A1F`,
the cut ink. At rest the frame is the logo itself.

- Prototype: `splash.html` (open it in a browser; scrub, ¼ speed, every platform mode, reduced motion, Android mask).
- Key frames: `filmstrip.png` · `frames/still-*.svg`. Regenerate with `python3 stills.py`.
- Timeline and drawing, shared by every generator: `motion.py`.

## How it is drawn while it plays

The tile is drawn solid and the cut is drawn over it in ink. When the animation ends, the frame swaps to the logo's
single outline, which is the same picture.
- **Trace:** a butt-capped stroke along the logo's centreline, 20 units wide.
- **Head:** a 32-unit square riding 16 units ahead of the trace's tip.
- **Stem:** a 12-unit bar growing down from the head's bottom edge to y 196.

## Timeline (in-app, 1.75 s)

Coordinates are the 256 logo grid. Easings as CSS `cubic-bezier`, also valid for Compose `CubicBezierEasing` and
Android `PathInterpolator`.

| ms | Element | What moves | Easing |
| --- | --- | --- | --- |
| 0–260 | Tile | wipes in from its left edge, x 32 → 224 (a clip, or `scaleX` 0 → 1 pivoted at x 32) | out-cubic · `cubic-bezier(.33,1,.68,1)` |
| 220–340 | Head | lands on the tile's left edge at the start of the trace (scale 0 → 1, kept inside the tile) | out-back 1.6 · `cubic-bezier(.34,1.6,.64,1)` |
| 300–1000 | Trace, head | the trace is drawn 0 → 100 % (trim end) to the head's left edge; the head rides 16 units ahead of its tip | in-out sine · `cubic-bezier(.37,0,.63,1)` |
| 1000–1120 | Stem | drops from the head's bottom edge, y 116 → 196 | out-cubic · `cubic-bezier(.33,1,.68,1)` |
| 1120–1400 | Head | the tick: swells to 1.2× and back in the first 60 % | sine bump |
| 1400–1750 | Whole mark | hands off: moves and scales into the first screen's 32 dp header mark (Start on phones) while the screen fades in from 1520 ms | in-out cubic · `cubic-bezier(.65,0,.35,1)` |

**Fade exit**, used where there is no header mark to land on: the mark scales 1 → 0.92 and fades out over 1400–1750
while the screen underneath fades in.

## Rules

- **Never wait for the animation beyond its draw.** The draw always plays to its end (1.12 s with the stem; Android's
  system part 1 s), even when the app is ready sooner. If the app isn't ready by then:
  - phones and desktop hold the finished mark;
  - the web loops the draw.
  Then the tick and the hand-off play once the app is ready.
- **Reduced motion:** the finished mark fades in over 200 ms, holds, and cross-fades to the app. Nothing moves. This
  applies when any of these is set: Android animator scale 0 or "Remove animations", iOS Reduce Motion,
  `prefers-reduced-motion`.
- **Once per cold start.** Returning from the background shows the app directly.
- **Optional haptic:** a light tick on the head's tick, on phones only.

## Per platform

### Android 12+ (SplashScreen API)

- **Setup:** `androidx.core:core-splashscreen`, with a `Theme.SplashScreen` theme:
  - `windowSplashScreenBackground` `#0B0B0B`
  - `windowSplashScreenAnimatedIcon` `@drawable/splash_icon`
  - `windowSplashScreenAnimationDuration` 1000 (the system's limit)
- **Size:** the icon box is 240 dp, and its visible area is a 160 dp circle. The 256 grid is drawn in a 420 viewport, so
  the tile's corners (136 units from the centre) stay inside the circle. The prototype's Android mask shows this.
- **The AVD (`avd.py`)** runs the compressed Android profile: wipe 0–200 ms, land 180–260, draw 240–880, stem 880–1000.
  - Tile: `scaleX` 0 → 1, pivoted at x 32.
  - Trace: `trimPathEnd` 0 → 1 on the centreline (`motion.CURVE_D`).
  - Head: `translateX` and `translateY` keyframes along the centreline, 16 units ahead, plus `scaleX`/`scaleY` for the
    landing.
  - Stem: `scaleY` 0 → 1.
- **Hand-over:** in `setOnExitAnimationListener` the app draws the finished mark at the icon view's position, plays the
  tick and the hand-off in Compose (the in-app 1120–1750 part), then removes the splash view.
- **Android 11 and lower (the backport):** `splash_icon_still.xml` shows the finished mark still, then the app plays the
  same tick and hand-off.

### iOS

- The launch screen can't animate. `LaunchScreen` shows ink and the finished mark (`LaunchMark`, from `launch.py`) at the
  same size and place as the icon view on Android.
- The first composable takes over seamlessly and plays only the tick and the hand-off. The draw belongs to the
  app-icon tap; there is no time to replay it before Start.

### Desktop (macOS, Windows, Linux)

- The editor's window opens on ink and plays the full 1.75 s sequence while `initKoin` runs.
- It then uses the fade exit into the Open screen. The desktop Open screen has a header mark, so the hand-off can be
  switched on when the screen is built.

### Web

- **First paint:** the first paint is `index.html` itself, long before the wasm or JS bundle has loaded. It draws the
  mark as inline SVG with CSS keyframes:
  - the tile: `clip-path: inset(0 100% 0 0)` → `inset(0)`
  - the trace: `stroke-dashoffset`
  - the head: `offset-path: path('M32 166 H80 C110 166 110 100 140 100 H172')`, `offset-distance` 0 → 100 %, 16 units
    ahead of the trace
  - the stem: `scaleY`
- **Loop:** it loops the draw until the app calls a `window` hook when Compose has drawn its first frame. Each cycle:
  - draw 720 ms;
  - hold 200;
  - refill 250, where the trace trims back from its tip and the stem retracts;
  - plot again.
- **Done:** the CSS then adds `done`, which plays the tick and then fades into the editor.
- `@media (prefers-reduced-motion: reduce)`: the static mark only, no loop.

## Decided

- **Exit per screen:** hand off into the first screen's own header mark wherever there is one (Start on the phones);
  fade elsewhere, including the web, whose splash is the page's own and can't see the editor's header.
- **The draw is never cut short** (see Rules).
- **No extra marks:** no REC dot, no scanline, no particles. The head's tick is the only accent.
- **Sound:** none. A shutter or beep would be a cliché, and most launches are silent.

## Implementation

Where each part lives; change the timeline in `motion.py` and mirror it in all of them.

- **App** (`core/designsystem/.../component/SplashMark.kt`): `SplashTimeline.app` / `.android`, `Splash.pose(t)` (pure
  numbers, tested by `core/ui/.../SplashTest.kt`) and the drawing. `SplashHost` (`core/ui/.../Splash.kt`) plays it,
  waits for the platform's splash, ticks (with the haptic) and hands off to `OnlyShotsMark(splashTarget = true)`. The
  mark's 256 grid is 146 dp, 256 / 420 of Android's 240 dp icon box, on every platform.
- **Android:** `python3 design/splash/avd.py` writes `res/drawable/splash_icon.xml` and `splash_icon_still.xml`;
  `MainActivity.markBounds` finds the system's mark at 1.5 × 256 / 420 of the icon view.
- **iOS:** `python3 design/splash/launch.py` writes `LaunchMark.imageset` (146 pt); `LaunchBackground` is ink.
- **Web:** `app/webApp/.../index.html` draws and loops it in inline SVG; `Main.kt` calls `onlyshotsReady()`.
- **Key frames:** `screenshots/.../SplashPreviews.kt` (120, 300, 760, 1000, 1220 ms).
