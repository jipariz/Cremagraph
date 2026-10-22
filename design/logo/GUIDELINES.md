# OnlyShots: logo guidelines (Signal tile)

## 1. The logo

- **Idea:** the live trace of the shot enters a solid signal tile from its edge and is cut out of it, up to the pen head
  that is writing it. The tile is the instrument; the cut is the shot being recorded. It keeps the earlier mark's idea
  (the shot recorded as its graph) and drops the viewfinder corners, to match the Instrument redesign: square, flat,
  one accent.
- **Versions:** horizontal lockup (primary) · stacked lockup · symbol · wordmark.
- **Weights of the symbol:** *regular* from 48 px up · *small* (wider trace, bigger head, thicker stem) from 16 to 47 px
  and in every favicon. There is no reversed weight: the mark is one colour and the ground shows through the cut.
- **Files:** `kit/` holds the SVG masters; `kit/png/`, `kit/web/`, `kit/ios/`, `kit/android/` and `kit/desktop/` the
  rendered sets; `kit/variants/` the one-colour files. Everything is one filled outline per mark: no strokes, no live
  text, no fill-rule holes.

## 2. Construction

256 grid. Tile 32–224 (192 square, square corners). The trace: a preinfusion flat at y 166 from the tile's left edge
to x 80, an S-curve up to y 100 at x 140, then flat to the pen head. Head: a square centred at (172, 100). Stem: from
the head's bottom to y 196, 28 short of the tile's bottom edge, so the tile stays one piece.

| Weight | Trace | Head | Stem |
| --- | --- | --- | --- |
| Regular | 20 | 32 | 12 |
| Small | 24 | 36 | 16 |

## 3. Clear space

Keep a margin of **x** on every side, where x is the head's side (32 on the grid, a sixth of the tile). Nothing else,
including text, other logos and the edge of the screen, comes closer. The trace's entry on the tile's left edge is part
of the mark: never crop or butt the tile's left edge against anything.

## 4. Minimum size

| Use | Screen | Print |
| --- | --- | --- |
| Symbol, regular | 48 px | 10 mm |
| Symbol, small cut | 16 px | 5 mm |
| Horizontal lockup | 140 px wide | 30 mm wide |
| Stacked lockup | 72 px wide | 18 mm wide |

## 5. Colour

| Name | HEX | RGB | CMYK (approx.) | Pantone (nearest, check on a swatch) |
| --- | --- | --- | --- | --- |
| Signal, the tile | `#FF5A1F` | 255 90 31 | 0 75 90 0 | Orange 021 C |
| Ink, the ground | `#0B0B0B` | 11 11 11 | 70 65 65 90 | Black 6 C |
| Bone, text on ink | `#E8E6E1` | 232 230 225 | 8 7 10 0 | Cool Gray 1 C |

- **Primary:** signal tile on ink (`onlyshots-symbol-on-dark.svg`, `onlyshots-horizontal-on-dark.svg`; wordmark in bone).
  Signal holds 6.3 : 1 on ink.
- **On light grounds:** signal tile, wordmark in ink (`onlyshots-symbol.svg`, `onlyshots-horizontal.svg`). Signal on
  white is 3.1 : 1: fine for the mark, too faint for small orange text.
- **One colour:** black, white or signal (`kit/variants/`). The cut always shows the ground; never fill it with a
  second colour except in the app icons, where ink fills it.
- **On photos and video:** use the symbol on a calm area or on an ink band; never over a busy frame.

## 6. Typography

- **Wordmark:** "ONLYSHOTS" in capitals, IBM Plex Mono SemiBold, outlined, tracked out 2 %. Its cap height is centred on
  the tile's centre line in the horizontal lockup and as wide as the tile in the stacked one. Always use the outlined
  file, never retype it. In running text the name is written OnlyShots.
- **With the logo:** IBM Plex Mono for labels and numbers, IBM Plex Sans for sentences, as in the app.
- **Licence:** IBM Plex is under the SIL Open Font License 1.1 (`src/fonts/OFL.txt`), which allows its use in a logo.
  The OFL reserves the name "Plex": don't ship a modified font under that name.

## 7. App icons

- **iOS:** `ios/AppIcon-1024.png`, the tile full bleed with ink in the cut; the system rounds it. The trace still
  enters from the icon's left edge.
- **Android:** adaptive layers in `android/`, 108 × 108 dp: the background is signal, the foreground is the cut in ink
  mapped so the tile fills the layer (the pen head sits inside the 66 dp safe zone; the trace runs in from the edge,
  the stem's foot may be masked), and a monochrome layer of the cut for themed icons. `play-store-512.png` for the
  store.
- **Desktop:** `desktop/icon-*.png`, the platforms' rounded square in ink with the tile at 72 % inside it.
- **Web:** `web/` has `favicon.svg`, `favicon.ico` (16, 32, 48), the PNG set, `site.webmanifest` and
  `head-snippet.html`. The favicon uses the small cut.

## 8. Don'ts

- Don't round the tile's corners, outline it or put a shadow or gradient on it.
- Don't close the trace's entry on the tile's left edge or move the trace off the head.
- Don't fill the cut with another colour (except ink in the app icons) or add a second accent.
- Don't redraw the trace as another chart shape (bars, a zigzag, a rising arrow), or run it past the head.
- Don't add a dot, a REC light, a cup, steam or beans.
- Don't stretch, rotate or crop the mark.
- Don't use the regular weight below 48 px or the small cut above 64 px.
- Don't set the wordmark in another face, in lower case, or retype it.

## 9. Rebuilding

`python3 design/logo/src/build.py` regenerates every SVG master in `kit/`, the one-colour SVGs in `kit/variants/` and
the icon SVGs from the geometry in one file. The PNGs and icon sets are rendered from those masters with the
logo-design skill's `render_png.py` and `export_variants.py --web-icons --favicon-source kit/onlyshots-symbol-small.svg`.

The previous mark (Viewfinder · Playhead, orange `#EF4E2B` on espresso), the concepts, the presentation board and the
size test are in git history.

A professional trademark search has not been done; run one before the mark is registered or launched.
