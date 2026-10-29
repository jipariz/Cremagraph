#!/usr/bin/env python3
"""Renders the Instrument redesign artboards in src/ to PNG screens for the store art.

The artboards are static HTML from the redesign canvas (390x844 phone, 800x1280 "medium",
1280x800 "big"). Each is shot with headless Chromium at the scale that gives the store size:
phone x3, medium x1.5 (1200x1920, 7" tablet), big x2 (2560x1600, 10" tablet).

    python3 design/store/render.py            # needs Google Chrome and network for Plex
"""

import pathlib
import re
import shutil
import subprocess
import tempfile

HERE = pathlib.Path(__file__).parent
SRC = HERE / "src"
OUT = HERE / "screens"

# Playwright keeps a full Chrome build, and this repo already installs one for tools/web-smoke, so the art can be
# made on a machine with no Chrome of its own. A real Chrome still wins: these go last.
_PLAYWRIGHT = pathlib.Path.home() / "Library" / "Caches" / "ms-playwright"
CHROMES = ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "google-chrome", "chromium"] + [
    str(c)
    for pattern in (
        "chromium-*/chrome-*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
        "chromium-*/chrome-linux/chrome",
    )
    for c in sorted(_PLAYWRIGHT.glob(pattern), reverse=True)
]
SCALE = {(390, 844): 3, (800, 1280): 1.5, (1280, 800): 2, (1280, 860): 2}

# The drawn camera frame swapped for a real shot on the Gaggiuino (photos/, metadata stripped). Board name prefix ->
# photo and where to centre the crop (the stream runs at about 46 % across).
PHOTOS = {
    "Rec-Recording": ("pouring.jpg", "46% 50%"),
    "Rec-Graph": ("armed.jpg", "46% 50%"),
    "Rec-Style": ("armed.jpg", "46% 50%"),
    "Edit-Idle": ("poured.jpg", "46% 55%"),
}
CAMERA = re.compile(r'<svg aria-hidden="true" viewBox="[^"]*"[^>]*>\s*<defs><radialGradient.*?</svg>', re.S)
DIM = '<div style="position: absolute; inset: 0; background: rgba(0,0,0,.6)"></div>'


def with_photo(name: str, html: str) -> str:
    hit = next((v for k, v in PHOTOS.items() if name == k or name.startswith(k + "-")), None)
    if not hit:
        return html
    photo, focus = hit
    img = (
        f'<img alt="" src="{(HERE / "photos" / photo).as_uri()}" style="position: absolute; inset: 0; width: 100%;'
        f' height: 100%; object-fit: cover; object-position: {focus}">'
    )
    if CAMERA.search(html):
        return CAMERA.sub(lambda _: img, html, count=1)
    return html.replace(DIM, img + DIM, 1)  # a sheet over the camera: the photo goes under its dim layer


def size_of(html: str) -> tuple[int, int]:
    m = re.search(r'"\$preview":\{"width":(\d+),"height":(\d+)\}', html)
    return int(m.group(1)), int(m.group(2))


def render(path: pathlib.Path, chromium: str) -> pathlib.Path:
    html = path.read_text()
    w, h = size_of(html)
    scale = SCALE.get((w, h), 2)
    # The canvas runtime is not needed: the boards are plain markup. Drop its script tags.
    html = html.replace('<script src="./support.js"></script>', "")
    html = re.sub(r'<script type="text/x-dc".*?</script>', "", html, flags=re.S)
    name = path.name.removesuffix(".dc.html")
    html = with_photo(name, html)
    out = OUT / (name + ".png")
    with tempfile.TemporaryDirectory() as tmp:
        page = pathlib.Path(tmp) / "page.html"
        page.write_text(html)
        subprocess.run(
            [
                chromium,
                "--headless=new",
                "--hide-scrollbars",
                "--allow-file-access-from-files",
                f"--window-size={w},{h}",
                f"--force-device-scale-factor={scale}",
                "--virtual-time-budget=4000",
                f"--screenshot={out}",
                page.as_uri(),
            ],
            check=True,
            capture_output=True,
        )
    return out


def main() -> None:
    chromium = next((c for c in CHROMES if shutil.which(c) or pathlib.Path(c).exists()), None)
    if not chromium:
        raise SystemExit("needs Google Chrome or Chromium")
    OUT.mkdir(exist_ok=True)
    for path in sorted(SRC.glob("*.dc.html")):
        print(render(path, chromium).relative_to(HERE))


if __name__ == "__main__":
    main()
