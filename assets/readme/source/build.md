# README art: how it's built

Everything in `assets/readme/` comes from the project's own material: the logo kit (`design/logo/kit`), the store art
(`design/store/play`) and the real pour on the Gaggiuino (`design/store/photos/pouring.jpg`).

| File | Made from | How |
| --- | --- | --- |
| `hero.gif`, `hero.png` | `source/hero-layout.svg` + `source/hero-photo.jpg` | `render-hero.mjs` (below). The GIF plots the shot, holds, winds back; its last frame equals its first. `hero.png` is the settled frame, shown when the viewer asks for reduced motion. |
| `showcase-phones.webp` | `design/store/play/phone/01–06.png` | The store frames run the phone off their bottom edge; in a grid that reads as a cut, so each tile's bottom fades into ink first: `magick -size 1080x420 gradient:'rgba(11,11,11,0)-rgba(11,11,11,1)' fade.png`, then per frame `magick 0N.png fade.png -gravity south -composite faded/0N.png`, then `magick montage faded/0{1,2,3,4,5,6}.png -tile 3x2 -geometry 460x818+10+20 -background '#0B0B0B' wall.png && cwebp -q 82 wall.png -o showcase-phones.webp` |
| `record`, `edit`, `connect`, `styles` `.webp` | `design/store/play/tablet-10/01–04.png` | The device picture without the store headline: `magick 01.png -crop 1640x1060+880+250 +repage c.png && cwebp -q 84 -resize 1400 0 c.png -o record.webp` (02 edit, 03 connect, 04 styles) |
| `section-*.svg`, `workflow.svg` | Hand-written | Plain SVG: system fonts, own ink background (reads the same in GitHub's light and dark themes), `<title>` and `<desc>`. |
| `source/hero-photo.jpg` | `design/store/photos/pouring.jpg` | `magick pouring.jpg -strip -gravity center -crop 1542x1770+0-40 +repage -resize 704x -quality 85 hero-photo.jpg` |

Hero (needs ffmpeg; Playwright comes from `tools/web-smoke`, Plex from `core/resources`):

```
cd tools/web-smoke && npm ci && node ../../assets/readme/source/render-hero.mjs
```

When the store art is re-shot from the app, rebuild the webp files from it. When the headline changes, change it in
`hero-layout.svg` and in `design/store/compose.py` together.
