# Product page header video

Apple added product page headers on 5 October 2026: a 21:9 slot above the product page taking an image or a video,
separate from screenshots and app previews. It is marketing art, it loops forever, and it is muted until someone
unmutes it — so unlike an app preview, it carries no device-capture rule.

Ours is the app's own output: a real shot with its real graph drawn over it, framed in the Instrument look. For an
app whose pitch is "export matches preview", nothing else is as strong.

- **Spec** (read 2026-10-29):
  <https://developer.apple.com/help/app-store-connect/reference/app-information/creative-assets-specifications/> —
  21:9 at 3840 × 1646 (or 16:9 at 5244 × 2950), 30 or 60 fps, 5 to 30 s, `.mov` / `.m4v` / `.mp4`.
- **Build:** `python3 design/header/build.py <master.mp4> --in 12 --out 34` (ffmpeg, and Google Chrome or the one
  Playwright keeps for `tools/web-smoke`, which `design/store/render.py` finds). Writes
  `design/header/out/header-3840x1646.mp4`.

## 1. Record the shot

In the app, with the machine and the scale connected.

- **Hold the phone in landscape before arming.** `formatThatFits` → `RecordViewModel.captureFormat` picks 9:16 or
  16:9 from how the screen stands, and turning it while armed re-arms. Landscape gives the 16:9 an ultra-wide
  header needs.
- **Keep the graph in the middle of the frame.** The master is 3840 × 2160 and the header is 3840 × 1646, so 257 px
  comes off the top and 257 off the bottom. Anything in the outer 12 % of the height is lost.
- Let the shot run to its stop weight, so the weight series climbs its full range.
- Audio is kept. The header is silent until someone unmutes, and a real pour is worth hearing.

One recording gives the video and its sidecar together, already aligned — no shot file to import, no "match by
clock".

## 2. Export the master

The desktop editor does this; there is no separate tool.

```
./gradlew :app:desktopApp:run
```

Open → the recording and its shot → format 16:9, quality 4K → check the graph sits in the middle band → export.
`Quality.size(format)` (`core/model/.../Video.kt`) fixes output to one of eight sizes and none of them is 21:9,
which is why the master is 16:9 and the 21:9 framing happens in `build.py`.

## 3. Compose

`build.py` shoots `furniture.html` — the still Instrument layer, **the one page under `design/` whose alpha must
survive**, since ffmpeg lays it over the footage — then crops the master to 3840 × 1646, trims to `--in`/`--out`,
overlays the furniture and encodes H.264 at 30 fps with AAC stereo. It refuses a length outside Apple's 5–30 s and
asserts the written size, because a wrong one is otherwise caught only by App Store Connect refusing the upload.

**Looping.** The header cuts straight back to the start. Pick `--in` and `--out` so that cut is not jarring:
ending on a steady pour that resembles the opening frame beats ending mid-transition.

## Where the files live

`out/` and any master are git-ignored. The finished header is tens of megabytes, and App Store Connect's Asset
Library now stores approved assets and lets you swap them without shipping a new version — that is the artefact's
home. The repo keeps the source and the build.

## App preview — not built here

The other slot, 886 × 1920, 15–30 s, **is** device capture, so it is a screen recording off the iPhone rather than
anything this folder makes.
<https://developer.apple.com/help/app-store-connect/reference/app-preview-specifications/>: H.264 High ≤ 4.0 at
10–12 Mbps, max 30 fps, stereo 256 kbps AAC with **all tracks enabled** — a preview with no audio track at all is
a common rejection, so give a silent capture a silent stereo track. Up to three per size and language.

A capture comes off the phone at 1320 × 2868 and 60 fps, so it needs scaling to 886 × 1920 and halving to 30 fps.

Shot list, using the machine or Home's "try a sample shot" (the `SampleShots.zer0` replay through
`SourceHolder.useSample`, which needs no hardware and is the path App Review uses):

| | Beat | Seconds |
| --- | --- | --- |
| 1 | Home, machine and scale found | 3 |
| 2 | Record armed, the shot starting itself off the brew switch | 6 |
| 3 | The graph drawn live over the pour, dragged to another corner | 7 |
| 4 | Edit: a style picked, the overlay changing with it | 5 |
| 5 | Export, and the finished video playing | 4 |
