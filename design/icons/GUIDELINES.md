# OnlyShots icons

The app's own icon set, drawn for the Instrument look: square, hairline, mono. `build.py` holds every icon as
geometry and writes the vector drawables (`core/resources/.../drawable/ic_*.xml`), the SVGs (`svg/`) and a contact sheet
(`sheet.html`). Never a character in place of an icon; draw a new one here.

## Rules

- **Grid:** 24 × 24, with a 2-unit margin: the live area is 20 × 20. Lines sit on whole or half units so the 1.5 stroke
  lands on the pixel grid at 1×, 2× and 3×.
- **Stroke:** one weight, 1.5, square caps, mitred joins. Never rounded.
- **Shapes:** straight lines and right angles; diagonals at 45°. Squares and rectangles over circles.
- **Accents:** filled squares, like the logo's pen head: the flag, a slider's knob, the padlock's body, the help dot.
- **Circles:** only where the meaning is round: the record dot in its ring.
- **Fills:** playback (play, pause, stop) and the caret are solid, so they read at a glance over video.
- **Colour:** drawn white and tinted where used (`CgIcon(icon, tint = …)`), at 12–24 dp; 16 dp is the default.

## Adding one

Add its shapes to `ICONS` in `build.py` (`stroke(d)` / `fill(d)`, `rect`, `circle`), run `python3 design/icons/build.py`,
look at `sheet.html` at 16, 24 and 48 px next to its neighbours, then name it in `CgIcons`.
