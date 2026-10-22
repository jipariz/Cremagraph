# Link banners: the web editor's link preview

What Discord, Slack, X, iMessage, WhatsApp and the rest show when someone shares https://onlyshots.parez.dev: a real
pour on the Gaggiuino (`design/store/photos/pouring.jpg`) with the Instrument graph, as an export draws it.

- **Source:** `src/og.html`, one artboard for every size (`og.html#1200x630`). The graph copies the Instrument style
  (`overlay/.../GraphStyles.kt`, golden `OverlayPreviews.InstrumentAfter`): change it there, change it here.
- **Build:** `python3 design/social/render.py` (Google Chrome, ImageMagick, network for IBM Plex). Writes the JPEGs
  straight into `app/webApp/src/webMain/resources/social/`, which the web build ships as `/social/`.
- **Tags:** `index.html`, `privacy.html` and `open/index.html` in the same resources. Image URLs are absolute, on
  onlyshots.parez.dev. Crawlers cache previews for days: to see a new image at once, rename the file or add `?v=2`.

| File | Size | Used by |
| --- | --- | --- |
| `og.jpg` | 1200 × 630 | `og:image`: Discord, Slack, Facebook, LinkedIn, iMessage, WhatsApp, Telegram |
| `og-square.jpg` | 1200 × 1200 | second `og:image`, for clients that show a square |
| `og-16x9.jpg` | 1200 × 675 | `twitter:image`, X's large card |
