# OnlyShots splash: recording the shot

The splash plays the logo's own idea once: the viewfinder finds focus, the shot is recorded behind a playhead, the dot
marks "now", and the mark hands off to the app. Everything is drawn from the logo kit's geometry
(`design/logo/src/build.py`, regular weight) on espresso `#120D0B`; the frame and playhead are cream `#F4EEEA`, the curve
and dot orange `#EF4E2B`.

- Prototype: `splash.html` (open it in a browser; scrub, ¼ speed, every platform mode, reduced motion).
- Key frames: `filmstrip.png` · `frames/still-*.svg`. Regenerate with `python3 gen.py` and `python3 stills.py`.

## Timeline (in-app, 1.75 s)

Coordinates are the 256 logo grid. Easings as CSS `cubic-bezier`, also valid for Compose `CubicBezierEasing` and
Android `PathInterpolator`.

| ms | Element | What moves | Easing |
| --- | --- | --- | --- |
| 0–320, +40 per corner (TL, TR, BR, BL) | Corners | from 26 units out along their diagonal to rest, opacity 0 → 1 over the first 140 ms | out-back 1.4 · `cubic-bezier(.34,1.4,.64,1)` |
| 260–420 | Playhead | grows from its middle (y 130) to y 78–182, at the curve's start (x 72) | out-cubic · `cubic-bezier(.33,1,.68,1)` |
| 380–540 | Dot | scales 0 → 18 at the curve's start | out-back 1.8 · `cubic-bezier(.34,1.8,.64,1)` |
| 380–1100 | Curve, playhead, dot | the curve is drawn 0 → 100 % (trim end); the playhead's x and the dot ride its tip | in-out sine · `cubic-bezier(.37,0,.63,1)` |
| 1100–1400 | Dot, ring | the dot swells to 1.28× and back in the first 60 %; a 4-unit ring grows 18 → 44 and fades 0.6 → 0 | sine bump · out-cubic |
| 1400–1750 | Whole mark | hands off: moves and scales into the first screen's 32 dp header mark (Start on phones, Open on the desktop) while the screen fades in from 1520 ms | in-out cubic · `cubic-bezier(.65,0,.35,1)` |

Fade exit, where there is no header mark to land on: scale 1 → 0.92 and fade out over 1400–1750, the screen under it fades in.

## Rules

- **Never wait for the animation beyond its draw.** The draw always plays to its end (1.1 s; Android's system part 1 s),
  even when the app is ready sooner; if the app isn't ready by then, hold the finished mark (phones, desktop) or loop
  the draw (web) and pulse and hand off when it is.
- **Reduced motion** (Android animator scale 0 or "Remove animations", iOS Reduce Motion, `prefers-reduced-motion`): the
  finished mark fades in over 200 ms, holds, and cross-fades to the app. Nothing moves.
- **Once per cold start.** Returning from the background shows the app directly.
- **Optional haptic:** a light tick on the pulse (phones only).

## Per platform

### Android 12+ (SplashScreen API)

- `androidx.core:core-splashscreen`; theme `Theme.SplashScreen` with `windowSplashScreenBackground` `#120D0B`,
  `windowSplashScreenAnimatedIcon` an animated vector drawable of the mark, and
  `windowSplashScreenAnimationDuration` 1000 (the system's limit).
- The icon box is 240 dp with the visible area a 160 dp circle: the mark is drawn at about 64 % of the box so its corners
  stay inside the circle (the prototype's dashed circle).
- The AVD runs the compressed Android profile: corners 0–280 ms (30 ms stagger), playhead 220–360, draw 320–960.
  - Curve: `trimPathEnd` 0 → 1.
  - Dot: a `translateX`/`translateY` group animated along the curve's own `pathData`.
  - Playhead: `translateX` along the same timing, plus `scaleY` for its growth.
- `setOnExitAnimationListener`: the app draws the finished mark at the icon view's position, then plays the pulse and
  the hand-off in Compose (the in-app 1100–1750 part), then removes the splash view.
- Android 11 and lower (the backport): the static finished mark, then the same pulse and hand-off in the app.

### iOS

- The launch screen can't animate. `LaunchScreen` shows espresso and the finished mark at the same size and place as the
  icon view on Android.
- The first composable takes over seamlessly: it plays only the pulse and the hand-off. The draw belongs to the
  app-icon tap; there is no time to replay it before Home.

### Desktop (macOS, Windows, Linux)

- The editor's window opens on espresso and plays the full 1.75 s sequence while `initKoin` and the library reconcile
  run, then fades to the Open screen (no header mark to land on).
- If start-up finishes first, skip to the pulse.

### Web

- The first paint is `index.html` itself, long before the wasm or JS bundle has loaded: inline SVG of the mark plus CSS
  keyframes.
  - The playhead uses `translateX`, the curve `stroke-dashoffset`.
  - The dot rides `offset-path: path('M72 168 H88 C106 168 112 110 134 110 H178')` with `offset-distance` 0 → 100 %.
- It loops the draw (draw 720 ms, hold 200, fade the curve 250, sweep again) until the app calls a `window` hook when
  Compose has drawn its first frame. The CSS then adds `done`: the pulse, then a fade into the editor.
- `@media (prefers-reduced-motion: reduce)`: the static mark only, no loop.

## Decided

- **Exit per screen:** hand-off into the first screen's own header mark wherever there is one: Start on the phones, Open
  on the desktop. Fade where there is none, and on the web, whose splash is the page's own (it can't see the editor's
  header).
- **The draw is never cut short** (see Rules).
- **Sound:** none. A shutter or beep would be a cliché, and most launches are silent.

## Implementation

- **Shared:** `SplashMark.kt` (the drawing and timeline, `core/designsystem`), `Splash.kt` (`SplashHost` over the app's
  root, `core/ui`). The first screen's `OnlyShotsMark(splashTarget = true)` says where the hand-off lands. Key frames
  are screenshot-tested (`SplashPreviews`).
- **Android:** `splash_icon.xml` / `splash_icon_still.xml` are generated by `avd.py`; `MainActivity` hands the system
  splash over to the app.
- **iOS:** `LaunchMark` is rendered by `launch.py`; `LaunchBackground` is espresso.
- **Web:** `index.html` draws it and `Main.kt` calls `onlyshotsReady()`.
