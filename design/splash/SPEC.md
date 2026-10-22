# OnlyShots splash: playing the shot (Signal tile)

The splash plays the logo's own idea once, the way a player runs through a recording: the signal tile wipes in, the
pen (the head on its stem) stands up at the tile's edge as a playhead and sweeps through the shot, the trace drawn
behind it; at the plateau's end the head pulses once, a ring going out from it, and the mark hands off to the app.
Everything is drawn from the logo kit's geometry (`design/logo/src/build.py`, regular weight) on ink `#0B0B0B`: the tile
signal `#FF5A1F`, the cut ink. At rest the frame is the logo itself.

- Prototype: `splash.html` (open it in a browser; scrub, ¼ speed, every platform mode, reduced motion, Android mask).
- Key frames: `filmstrip.png` · `frames/still-*.svg`. Regenerate with `python3 stills.py`.
- Timeline and drawing, shared by every generator: `motion.py`.

## How it is drawn while it plays

The tile is drawn solid and the cut is drawn over it in ink. When the animation ends, the frame swaps to the logo's
single outline, which is the same picture.
- **Trace:** a butt-capped stroke along the logo's centreline, 20 units wide, drawn up to the playhead.
- **Head:** a 32-unit square riding the trace 16 units ahead of its tip.
- **Stem:** a 12-unit bar standing under the head wherever it is, down to y 196: head and stem are the playhead.
- **Ring:** in the pulse, a 5-unit square outline growing from the head's 32 to 72 units and fading out.

## Timeline (in-app, 1.75 s)

Coordinates are the 256 logo grid. Easings as CSS `cubic-bezier`, also valid for Compose `CubicBezierEasing` and
Android `PathInterpolator`.

| ms | Element | What moves | Easing |
| --- | --- | --- | --- |
| 0–260 | Tile | wipes in from its left edge, x 32 → 224 (a clip, or `scaleX` 0 → 1 pivoted at x 32) | out-cubic · `cubic-bezier(.33,1,.68,1)` |
| 220–380 | Playhead | stands up at the start of the trace: the head scales 0 → 1 (kept inside the tile), the stem grows under it | out-back 1.6 · `cubic-bezier(.34,1.6,.64,1)` |
| 380–1100 | Playhead, trace | sweeps through the shot: the head rides the trace 16 units ahead of its tip, the stem under it, the trace drawn 0 → 100 % (trim end) behind | in-out sine · `cubic-bezier(.37,0,.63,1)` |
| 1100–1400 | Head | the pulse: swells to 1.25× and back in the first 60 %, a ring going out from it 32 → 72 and fading | sine bump · ring out-cubic |
| 1400–1750 | Whole mark | hands off: moves and scales into the first screen's 32 dp header mark (Start on phones) while the screen fades in from 1520 ms | in-out cubic · `cubic-bezier(.65,0,.35,1)` |

**Fade exit**, used where there is no header mark to land on: the mark scales 1 → 0.92 and fades out over 1400–1750
while the screen underneath fades in.

## Rules

- **Never wait for the animation beyond its draw.** The draw always plays to its end (1.1 s; Android's system part 1 s), even when the app is ready sooner. If the app isn't ready by then:
  - phones and desktop hold the finished mark;
  - the web loops the draw.
  Then the pulse and the hand-off play once the app is ready.
- **Reduced motion:** the finished mark fades in over 200 ms, holds, and cross-fades to the app. Nothing moves. This
  applies when any of these is set: Android animator scale 0 or "Remove animations", iOS Reduce Motion,
  `prefers-reduced-motion`.
- **Once per cold start.** Returning from the background shows the app directly.
- **Optional haptic:** a light tick on the head's pulse, on phones only.

## Per platform

### Android 12+ (SplashScreen API)

- **Setup:** `androidx.core:core-splashscreen`, with a `Theme.SplashScreen` theme:
  - `windowSplashScreenBackground` `#0B0B0B`
  - `windowSplashScreenAnimatedIcon` `@drawable/splash_icon`
  - `windowSplashScreenAnimationDuration` 1000 (the system's limit)
- **Size:** the icon box is 240 dp, and its visible area is a 160 dp circle. The 256 grid is drawn in a 420 viewport, so
  the tile's corners (136 units from the centre) stay inside the circle. The prototype's Android mask shows this.
- **The AVD (`avd.py`)** runs the compressed Android profile: wipe 0–200 ms, the playhead stands up 180–300, sweep
  300–1000.
  - Tile: `scaleX` 0 → 1, pivoted at x 32.
  - Trace: `trimPathEnd` 0 → 1 on the centreline (`motion.CURVE_D`).
  - Head: `translateX` and `translateY` keyframes along the centreline, 16 units ahead, plus `scaleX`/`scaleY` for the
    landing.
  - Stem: the same `translateX`/`translateY` keyframes under the head, and `scaleY` keyframes for its length to y 196.
- **Hand-over:** in `setOnExitAnimationListener` the app draws the finished mark at the icon view's position, plays the
  pulse and the hand-off in Compose (the in-app 1100–1750 part), then removes the splash view.
- **Android 11 and lower (the backport):** `splash_icon_still.xml` shows the finished mark still, then the app plays the
  same pulse and hand-off.

### iOS

- The launch screen can't animate. `LaunchScreen` shows ink and the finished mark (`LaunchMark`, from `launch.py`) at the
  same size and place as the icon view on Android.
- The first composable takes over seamlessly and plays only the pulse and the hand-off. The draw belongs to the
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
  - the playhead: the head and the stem under it follow `M32 166 H80 C110 166 110 100 140 100 H172`, 16 units ahead of
    the trace
- **Loop:** it loops the sweep until the app calls a `window` hook when Compose has drawn its first frame. Each cycle:
  - hold 200 ms;
  - back 250, where the playhead runs back to the start and the trace trims back behind it;
  - sweep 720.
- **Done:** at the pen, it plays the pulse and then fades into the editor.
- `@media (prefers-reduced-motion: reduce)`: the static mark only, no loop.

## Decided

- **Exit per screen:** hand off into the first screen's own header mark wherever there is one (Start on the phones);
  fade elsewhere, including the web, whose splash is the page's own and can't see the editor's header.
- **The draw is never cut short** (see Rules).
- **No extra marks:** no REC dot, no scanline, no particles. The head's pulse is the only accent.
- **Sound:** none. A shutter or beep would be a cliché, and most launches are silent.

## Implementation

Where each part lives; change the timeline in `motion.py` and mirror it in all of them.

- **App** (`core/designsystem/.../component/SplashMark.kt`): `SplashTimeline.app` / `.android`, `Splash.pose(t)` (pure
  numbers, tested by `core/ui/.../SplashTest.kt`) and the drawing. `SplashHost` (`core/ui/.../Splash.kt`) plays it,
  waits for the platform's splash, pulses (with the haptic) and hands off to `OnlyShotsMark(splashTarget = true)`. The
  mark's 256 grid is 146 dp, 256 / 420 of Android's 240 dp icon box, on every platform.
- **Android:** `python3 design/splash/avd.py` writes `res/drawable/splash_icon.xml` and `splash_icon_still.xml`;
  `MainActivity.markBounds` finds the system's mark at 1.5 × 256 / 420 of the icon view.
- **iOS:** `python3 design/splash/launch.py` writes `LaunchMark.imageset` (146 pt); `LaunchBackground` is ink.
- **Web:** `app/webApp/.../index.html` draws and loops it in inline SVG; `Main.kt` calls `onlyshotsReady()`.
- **Key frames:** `screenshots/.../SplashPreviews.kt` (120, 300, 760, 1100, 1200 ms).
