"""The app's icons: IBM Carbon (carbon:*, made to sit beside IBM Plex), fetched with better-icons and written as
Compose vector drawables (core/resources/.../drawable/ic_*.xml), drawn white and tinted where they are used.

    python3 design/icons/build.py            # fetch the SVGs (design/icons/svg/) and write the drawables
    python3 design/icons/build.py --offline  # rebuild the drawables from the SVGs already here

Icons: Carbon Design System, IBM, Apache License 2.0 (https://github.com/carbon-design-system/carbon).
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SVG = os.path.join(HERE, "svg")
OUT = os.path.join(HERE, "..", "..", "core", "resources", "src", "commonMain", "composeResources", "drawable")

# drawable name -> Carbon id
ICONS = {
    "ic_chevron_left": "chevron-left",
    "ic_chevron_right": "chevron-right",
    "ic_chevron_down": "chevron-down",
    "ic_caret_down": "caret-down",
    "ic_plus": "add",
    "ic_minus": "subtract",
    "ic_contrast": "contrast",
    "ic_frame": "crop",
    "ic_flag": "flag",
    "ic_check": "checkmark",
    "ic_arrow_right": "arrow-right",
    "ic_play": "play-filled-alt",
    "ic_pause": "pause-filled",
    "ic_stop": "stop-filled-alt",
    "ic_edit": "edit",
    "ic_bluetooth": "bluetooth",
    "ic_launch": "launch",
    "ic_close": "close",
    "ic_shield": "security",
    "ic_machine": "cafe",
    "ic_film": "video",
    "ic_settings": "settings",
    "ic_record": "recording",
    "ic_help": "help",
    "ic_lock": "locked",
}


def fetch(carbon_id):
    path = os.path.join(SVG, carbon_id + ".svg")
    if "--offline" not in sys.argv:
        svg = subprocess.run(["npx", "-y", "better-icons", "get", "carbon:" + carbon_id], capture_output=True,
                             text=True, check=True).stdout.strip()
        if not svg.startswith("<svg"):
            raise SystemExit(f"carbon:{carbon_id}: {svg[:200]}")
        open(path, "w").write(svg + "\n")
    return open(path).read()


def shapes(svg):
    """The SVG's drawn shapes as path data: paths, rects and circles; transparent boxes are left out."""
    out = []
    for tag, attrs in re.findall(r"<(path|rect|circle|polygon)\b([^>]*)/?>", svg):
        a = dict(re.findall(r'([\w:-]+)="([^"]*)"', attrs))
        if a.get("fill") == "none" or "fill:none" in a.get("style", "").replace(" ", ""):
            continue
        if tag == "path":
            d = a["d"]
        elif tag == "rect":
            x, y = float(a.get("x", 0)), float(a.get("y", 0))
            w, h = float(a["width"]), float(a["height"])
            d = f"M{x:g} {y:g}h{w:g}v{h:g}h{-w:g}z"
        elif tag == "circle":
            cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
            d = f"M{cx - r:g} {cy:g}a{r:g} {r:g} 0 1 0 {2 * r:g} 0a{r:g} {r:g} 0 1 0 {-2 * r:g} 0z"
        else:
            d = "M" + a["points"].strip() + "z"
        transform = a.get("transform")
        out.append((d, a.get("fill-rule") == "evenodd", transform))
    return out


def drawable(name, carbon_id, svg):
    vb = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    body = []
    for d, evenodd, transform in shapes(svg):
        rule = '\n        android:fillType="evenOdd"' if evenodd else ""
        path = (f'    <path\n        android:fillColor="#FFFFFFFF"{rule}\n        android:pathData="{d}" />')
        m = re.match(r"translate\(([-\d.]+)[ ,]+([-\d.]+)\)", transform or "")
        if m:
            path = (f'    <group android:translateX="{m.group(1)}" android:translateY="{m.group(2)}">\n'
                    + path.replace("\n", "\n    ") + "\n    </group>")
        elif transform:
            raise SystemExit(f"carbon:{carbon_id}: unsupported transform {transform}")
        body.append(path)
    return (f"<!-- carbon:{carbon_id} (IBM Carbon, Apache 2.0), written by design/icons/build.py. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            f'    android:viewportWidth="{vb[2]:g}"\n    android:viewportHeight="{vb[3]:g}">\n'
            + "\n".join(body) + "\n</vector>\n")


def main():
    os.makedirs(SVG, exist_ok=True)
    for name, carbon_id in ICONS.items():
        svg = fetch(carbon_id)
        open(os.path.join(OUT, name + ".xml"), "w").write(drawable(name, carbon_id, svg))
        print(name, "<-", "carbon:" + carbon_id)


if __name__ == "__main__":
    main()
