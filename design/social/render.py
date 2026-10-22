#!/usr/bin/env python3
"""Renders the web editor's link banners (og:image) from src/og.html into the web app's resources.

One artboard, three sizes: the 1.91:1 og:image every platform uses, a 16:9 one for X (twitter:image) and a square one.
Shot with headless Chrome at 1x, then written as JPEG (a photo: a PNG would be five times the size).

    python3 design/social/render.py            # needs Google Chrome, ImageMagick, and network for Plex
"""

import pathlib
import shutil
import subprocess
import tempfile

HERE = pathlib.Path(__file__).parent
SRC = HERE / "src" / "og.html"
OUT = HERE.parent.parent / "app" / "webApp" / "src" / "webMain" / "resources" / "social"

CHROMES = ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "google-chrome", "chromium"]
SIZES = {"og.jpg": (1200, 630), "og-16x9.jpg": (1200, 675), "og-square.jpg": (1200, 1200)}


def main() -> None:
    chrome = next((c for c in CHROMES if shutil.which(c) or pathlib.Path(c).exists()), None)
    if not chrome:
        raise SystemExit("needs Google Chrome or Chromium")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (w, h) in SIZES.items():
        with tempfile.TemporaryDirectory() as tmp:
            png = pathlib.Path(tmp) / "shot.png"
            subprocess.run(
                [chrome, "--headless=new", "--hide-scrollbars", "--allow-file-access-from-files",
                 f"--window-size={w},{h}", "--force-device-scale-factor=1", "--virtual-time-budget=5000",
                 f"--screenshot={png}", f"{SRC.resolve().as_uri()}#{w}x{h}"],
                check=True, capture_output=True,
            )
            subprocess.run(["magick", png, "-strip", "-quality", "86", "-sampling-factor", "4:2:0", OUT / name],
                           check=True)
        print(f"{OUT / name}  {w}x{h}  {(OUT / name).stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
