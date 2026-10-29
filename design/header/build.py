#!/usr/bin/env python3
"""Builds the App Store product page header video from a shot exported by OnlyShots.

The header is marketing art, not an app preview: Apple sets no device-capture rule for it, it loops forever and it
is muted until someone unmutes. So it is the app's own export — a real shot with its real graph over it — framed
in the Instrument look.

    ./gradlew :app:desktopApp:run        # export the recording at 16:9 / 4K, graph in the middle band
    python3 design/header/build.py master.mp4 --in 12 --out 34

Needs ffmpeg and Google Chrome (or the build Playwright keeps for tools/web-smoke, which render.py finds).

Spec, read 2026-10-29: developer.apple.com/help/app-store-connect/reference/app-information/creative-assets-specifications/
21:9 at 3840 x 1646, 30 or 60 fps, 5 to 30 s, .mov/.m4v/.mp4.
"""

import argparse
import pathlib
import shutil
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str(HERE.parent / "store"))
from render import CHROMES  # noqa: E402  the same Chrome discovery the store art uses

W, H, FPS = 3840, 1646, 30
MIN_S, MAX_S = 5, 30


def chrome() -> str:
    found = next((c for c in CHROMES if shutil.which(c) or pathlib.Path(c).exists()), None)
    if not found:
        raise SystemExit("needs Google Chrome or Chromium")
    return found


def furniture(out: pathlib.Path) -> None:
    """Shoots furniture.html keeping its alpha, so ffmpeg can lay it over the footage."""
    subprocess.run(
        [chrome(), "--headless=new", "--hide-scrollbars", "--allow-file-access-from-files",
         "--default-background-color=00000000", f"--window-size={W},{H}", "--force-device-scale-factor=1",
         "--virtual-time-budget=4000", f"--screenshot={out}", (HERE / "furniture.html").as_uri()],
        check=True, capture_output=True,
    )
    channels = subprocess.run(
        ["magick", "identify", "-format", "%[channels]", out], check=True, capture_output=True, text=True
    ).stdout
    # Every other page under design/ flattens its alpha away; this one must keep it or the overlay hides the shot.
    if "a" not in channels:
        raise SystemExit(f"furniture.png came out {channels}, with no alpha: the overlay would be opaque")


def compose(master: pathlib.Path, layer: pathlib.Path, start: float, end: float, out: pathlib.Path) -> None:
    length = end - start
    if not MIN_S <= length <= MAX_S:
        raise SystemExit(f"{length:g}s is outside Apple's {MIN_S}-{MAX_S}s for a header")
    # Fill the width, then take the middle 1646: an export is 16:9, so 257 px goes off the top and the bottom.
    chain = f"[0:v]scale={W}:-2,crop={W}:{H},fps={FPS},setsar=1[v];[v][1:v]overlay=0:0:format=auto[o]"
    subprocess.run(
        ["ffmpeg", "-y", "-ss", f"{start}", "-to", f"{end}", "-i", master, "-i", layer,
         "-filter_complex", chain, "-map", "[o]", "-map", "0:a?",
         "-c:v", "libx264", "-profile:v", "high", "-level", "4.0", "-preset", "slow",
         "-b:v", "12M", "-maxrate", "14M", "-bufsize", "24M", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
         "-c:a", "aac", "-b:a", "256k", "-ac", "2", "-ar", "48000", out],
        check=True, capture_output=True,
    )
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
         "-of", "csv=p=0:s=x", out], check=True, capture_output=True, text=True
    ).stdout.strip()
    # A wrong size is otherwise caught only by App Store Connect refusing the upload.
    if probe != f"{W}x{H}":
        raise SystemExit(f"{out.name}: wrote {probe}, wanted {W}x{H}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("master", type=pathlib.Path, help="the 16:9 export out of OnlyShots")
    ap.add_argument("--in", dest="start", type=float, default=0.0, help="seconds into the master to start")
    ap.add_argument("--out", dest="end", type=float, required=True, help="seconds into the master to stop")
    ap.add_argument("--to", type=pathlib.Path, default=HERE / "out" / "header-3840x1646.mp4")
    a = ap.parse_args()
    if not a.master.exists():
        raise SystemExit(f"no such master: {a.master}")
    a.to.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        layer = pathlib.Path(tmp) / "furniture.png"
        furniture(layer)
        compose(a.master, layer, a.start, a.end, a.to)
    size = a.to.stat().st_size / 1e6
    root = HERE.parent.parent
    where = a.to.relative_to(root) if a.to.is_relative_to(root) else a.to
    print(f"{where}  {W}x{H}  {a.end - a.start:g}s  {size:.1f} MB")


if __name__ == "__main__":
    main()
