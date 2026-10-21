# OnlyShots: logo guidelines

## 1. The logo

- **Idea:** a camera's four corners frame the shot as it is recorded. The curve is drawn up to a playhead, and the dot
  rides the playhead where the curve meets it: the app films the shot with its graph being written over it. The frame
  and the playhead are the camera, the orange curve and the dot are the shot.
- **Versions:** horizontal lockup (primary) · stacked lockup · symbol · wordmark.
- **Weights of the symbol:** *regular* from 48 px up · *small* (heavier strokes, playhead and dot) from
  16 to 47 px and in every favicon · *reversed* (thinned) for light marks on dark grounds at large sizes.
- **Files:** `kit/` holds the SVG masters; `kit/png/`, `kit/web/`, `kit/ios/`, `kit/android/` and `kit/desktop/` the
  rendered sets; `kit/variants/` the one-colour files. Everything is a filled outline: no strokes, no live text.

## 2. Clear space

Keep a margin of **x** on every side, where x is the dot's diameter (14 % of the symbol's height). Nothing
else, including text, other logos and the edge of the screen, comes closer.

## 3. Minimum size

| Use | Screen | Print |
| --- | --- | --- |
| Symbol, regular | 48 px | 10 mm |
| Symbol, small cut | 16 px | 6 mm |
| Horizontal lockup | 140 px wide | 30 mm wide |
| Stacked lockup | 72 px wide | 18 mm wide |

## 4. Colour

| Name | HEX | RGB | CMYK (approx.) | Pantone (nearest, check on a swatch) |
| --- | --- | --- | --- | --- |
| Orange, the shot | `#EF4E2B` | 239 78 43 | 0 80 85 0 | 172 C |
| Espresso, the frame on light | `#120D0B` | 18 13 11 | 60 70 70 90 | Black 4 C |
| Tile, the icon ground | `#1C1412` | 28 20 18 | 60 70 70 85 | Black 4 C |
| Cream, the frame on dark | `#F4EEEA` | 244 238 234 | 3 5 6 0 | 9224 C |

- **On light grounds:** espresso frame and playhead, orange curve and dot (`onlyshots-symbol.svg`, `onlyshots-horizontal.svg`).
- **On dark grounds:** cream frame and playhead, orange curve and dot (`*-on-dark.svg`). Orange holds 5 : 1 on the tile and 5.3 : 1 on
  espresso.
- **One colour:** black, white or orange (`kit/variants/`). Use one colour whenever two can't be reproduced.
- Orange on white is 3.6 : 1: fine for the mark, too faint for small orange text beside it.

## 5. Typography

- **Wordmark:** "onlyshots" in lower case, Open Sans SemiBold, outlined, tracked in by 1.2 %, with *ly*, *ot* and *ts*
  eased in and *ys* opened a touch by hand (`src/build.py`). Its x-height is centred on the symbol's centre line. Always
  use the outlined file, never retype it. In running text the name is written OnlyShots.
- **With the logo:** Open Sans in the app's weights (Light, Regular, SemiBold). Web fallback: `system-ui, sans-serif`.
- **Licence:** Open Sans is open source (Apache 2.0 for the static release the app ships, SIL OFL for the variable one).
  Both allow its use in a logo.

## 6. App icons

- **iOS:** `ios/AppIcon-1024.png`, a full square; the system rounds it. The mark fills the middle 62 %.
- **Android:** adaptive layers in `android/`, 108 × 108 dp: tile background, the mark inside the 66 dp safe zone, and a
  monochrome layer for themed icons. `play-store-512.png` for the store.
- **Desktop:** `desktop/icon-*.png`, a rounded tile with the mark at 56 %.
- **Web:** `web/` has `favicon.svg`, `favicon.ico` (16, 32, 48), the PNG set, `site.webmanifest` and `head-snippet.html`. The
  favicon uses the small cut.

## 7. Don'ts

- Don't recolour the frame or playhead orange, or the curve and dot in the frame's colour: the two colours carry the
  idea.
- Don't move the dot off the playhead, set it apart from the curve, or add more dots (including a REC light).
- Don't redraw the curve as another chart shape (bars, a zigzag, a rising arrow), or draw it past the playhead.
- Don't add a cup, steam or beans.
- Don't stretch, rotate, outline, shadow or put a gradient on the mark.
- Don't use the regular weight below 48 px or the small cut above 64 px.
- Don't set the wordmark in another face or retype it.

## 8. Rebuilding

`python3 design/logo/src/build.py` regenerates every SVG master in `kit/`, and the one-colour SVGs in `kit/variants/`,
from the geometry in one file. The PNGs and icon sets are rendered from those masters.

A professional trademark search has not been done; run one before the mark is registered or launched.
