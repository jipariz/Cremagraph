# Store art: Play Store and App Store, Instrument redesign

The Play listing art in the Instrument look (logo: `design/logo`, screens: the redesign canvas
https://claude.ai/artifact/LTQSexsdusFXLmyuTFNVSa).

- **Figma** (layout source, and the template's Play listing mockup with our icon and screenshots):
  https://www.figma.com/design/C3IXXAYvSHQ0TEgwTijgFc, page *Play Store Template*. Phone screenshots are the
  `Pixel Preview01`–`06` components; tablets and the feature graphic sit to their right.
- **Build:** `python3 design/store/render.py && python3 design/store/compose.py` (Google Chrome or the one
  Playwright keeps for `tools/web-smoke`, ImageMagick, network for IBM Plex). `render.py` shoots the artboards in
  `src/` to `screens/`; `compose.py` lays out the same frames as Figma and writes `play/` and `appstore/`. Change
  copy in `compose.py` and in Figma together.

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
| 1 | `Main` | FILM THE SHOT. KEEP THE DATA. | Film espresso with its live graph from your machine and scale. |
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

## Files in `appstore/`

Sizes from Apple's own spec, which the third-party guides disagree with:
<https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications/>. Required are one iPhone
with Dynamic Island (medium) and, since the app runs on iPad, one iPad 13-inch; the large iPhone set is the highest
resolution, which App Store Connect scales the smaller slots down from. Duo is required from April 2027 on
iOS 27.1+ (docs/DEVELOPMENT.md). Apple allows ten per slot.

Sizes are the ones the App Store template's artwork is drawn for, all on Apple's accepted list.

| Folder | Size | App Store slot | Screen | Frame |
| --- | --- | --- | --- | --- |
| `iphone/01–06.png` | 1206 × 2622 | Dynamic Island, medium — **required** | phone board | iPhone 18 Pro |
| `ipad-13/01–04.png` | 2048 × 2732 | iPad 13-inch, portrait — **required** | `-M` board | hairline |
| `ipad-13-landscape/01–04.png` | 2732 × 2048 | iPad 13-inch, landscape | `-B` board | hairline |
| `ipad-11/01–04.png` | 1668 × 2420 | iPad 11-inch, portrait | `-M` board | iPad Pro 11 |
| `ipad-11-landscape/01–04.png` | 2420 × 1668 | iPad 11-inch, landscape | `-B` board | iPad Pro 11 |
| `duo-outer/01–03.png` | 1398 × 2034 | iPhone Duo, outer | **simulator capture** | Duo cover |
| `duo-inner/01–03.png` | 2853 × 2007 | iPhone Duo, inner | **simulator capture** | Duo inner |

No 6.9-inch set: Apple scales the larger iPhone slots down from the medium one, and the template draws no 6.9-inch
device to frame it with.

**Device frames** live in `frames/`, exported from the App Store template's Figma file
(`tR7IFIYoSGarqgLDOsF8re`, pages *iPhone*, *iPad …*, *iPhone Duo …*; each page carries a `… device / …` symbol
whose raw image is the bezel with its screen cut out). `compose.py`'s `Device` table holds each one's pixel size
and the cut-out inside it, so nothing is rescaled. The iPhone's is the whole component rather than a cut-out, so
its picture is drawn over the placeholder screen instead, with a corner radius to match.

A screen that is not the frame's shape letterboxes onto ink rather than cropping: on an ink UI the bars cannot be
seen, and no part of the app is lost. That is what the two iPad frames need, their screen being 1210 × 834 against
the boards' 1280 × 800.

**The 13-inch slots keep the hairline frame.** The only iPad the template draws is an 11-inch, and framing a
13-inch shot with it would stretch the hardware; the 11-inch slots are framed at their own native size instead.

**The Duo slots use real simulator captures**, not artboards: its side column is iOS's own bar on a hinged device
and exists in no artboard. Run `tools/ios-screenshots/capture.sh` first — the pictures are git-ignored, and these
two slots are skipped with a message when they are not there.

`compose.py`'s `APPSTORE` table holds one row per slot; `frame w × scale` has to equal Apple's pixel size exactly,
and `shoot` asserts that on every write, because a wrong scale is otherwise only caught by App Store Connect
refusing the upload.
