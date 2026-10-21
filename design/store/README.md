# Store art: Play Store, Instrument redesign

The Play listing art in the Instrument look (logo: `design/logo`, screens: the redesign canvas
https://claude.ai/artifact/LTQSexsdusFXLmyuTFNVSa).

- **Figma** (layout source, and the template's Play listing mockup with our icon and screenshots):
  https://www.figma.com/design/C3IXXAYvSHQ0TEgwTijgFc, page *Play Store Template*. Phone screenshots are the
  `Pixel Preview01`–`06` components; tablets and the feature graphic sit to their right.
- **Build:** `python3 design/store/render.py && python3 design/store/compose.py` (Google Chrome, ImageMagick,
  network for IBM Plex). `render.py` shoots the artboards in `src/` to `screens/`; `compose.py` lays out the same
  frames as Figma and writes `play/`. Change copy in `compose.py` and in Figma together.

## Files in `play/`

| File | Size | Play slot |
| --- | --- | --- |
| `icon-512.png` | 512 × 512, 32-bit | App icon (copy of `logo/kit/android/play-store-512.png`) |
| `feature-graphic-1024x500.png` | 1024 × 500, no alpha | Feature graphic |
| `phone/01–06.png` | 1080 × 1920 | Phone screenshots |
| `tablet-7/01–04.png` | 1200 × 1920 | 7-inch tablet screenshots |
| `tablet-10/01–04.png` | 2560 × 1600 | 10-inch tablet screenshots |

## Frames

| # | Phone screen | Headline | Line |
| --- | --- | --- | --- |
| 1 | `Main` | YOUR SHOT, WITH ITS GRAPH | Film espresso with its graph on top: pressure, flow, weight or temp. |
| 2 | `Home-Found` | FINDS YOUR MACHINE AND SCALE | Gaggiuino over Wi-Fi, scales over Bluetooth. |
| 3 | `Rec-Recording` | STARTS WHEN THE SHOT DOES | Recording follows the brew switch. |
| 4 | `Rec-Graph` | THE GRAPH WHERE YOU WANT IT | Drag it, resize it, pick what it shows. |
| 5 | `Edit-Idle` | LINE IT UP AFTER | Any video, any shot file. Trim and export. |
| 6 | `Rec-Style` | SIX STYLES, OR YOUR OWN | Every export matches the preview. |

Tablets (screen `-M` for 7", `-B` for 10"), in order: `Rec-Recording`, `Edit-Idle`, `Home-Found`, `Rec-Style`, with
the phone headlines of the same screens.

The camera frame in the record and edit screens is a real shot on the Gaggiuino (`photos/`, resized, metadata and
location stripped): `armed.jpg` for Armed and the style sheet, `pouring.jpg` for Recording, `poured.jpg` for the
editor. `render.py` (`PHOTOS`) swaps it in for the canvas's drawn camera.

The screens show the redesign, not the shipping app: re-shoot them from the app once the Instrument theme lands in
`core/designsystem`.
