"""OnlyShots' own icons ("Instrument" icons), drawn here as geometry and written as Compose vector drawables
(core/resources/.../drawable/ic_*.xml), white and tinted where they are used. Also writes design/icons/svg/*.svg and a
contact sheet, design/icons/sheet.html.

    python3 design/icons/build.py

The rules (design/icons/GUIDELINES.md): a 24 grid with a 2-unit margin (live area 20 × 20); one stroke, 1.5, square
caps and mitred joins; straight lines and right angles, diagonals at 45°; accents as filled squares, the logo's pen
head. No curves at all: nothing round, nothing rounded off.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "core", "resources", "src", "commonMain", "composeResources", "drawable")
SVG = os.path.join(HERE, "svg")
W = 1.5  # the one stroke


def stroke(d):
    return ("stroke", d)


def fill(d):
    return ("fill", d)


def rect(x, y, w, h):
    return f"M{x:g} {y:g}H{x + w:g}V{y + h:g}H{x:g}Z"


ICONS = {
    # Direction: chevrons on 45°, 7 units across.
    "ic_chevron_left": [stroke("M14.5 5L7.5 12L14.5 19")],
    "ic_chevron_right": [stroke("M9.5 5L16.5 12L9.5 19")],
    "ic_chevron_down": [stroke("M5 9.5L12 16.5L19 9.5")],
    "ic_caret_down": [fill("M7 9.5H17L12 14.5Z")],
    "ic_arrow_right": [stroke("M4 12H19"), stroke("M13.5 6.5L19 12L13.5 17.5")],
    "ic_launch": [stroke("M10 5H5V19H19V14"), stroke("M13 5H19V11"), stroke("M19 5L11 13")],
    # Amounts.
    "ic_plus": [stroke("M12 5V19"), stroke("M5 12H19")],
    "ic_minus": [stroke("M5 12H19")],
    # How much of the graph's ground shows: a square, half filled.
    "ic_contrast": [stroke(rect(4.75, 4.75, 14.5, 14.5)), fill(rect(12, 4.75, 7.25, 14.5))],
    # The video's frame: two crop corners.
    "ic_frame": [stroke("M7 3V17H21"), stroke("M3 7H17V21")],
    # The shot's start, placed by hand: a pole and a square flag.
    "ic_flag": [stroke("M6 3V21"), fill(rect(6.75, 4, 12, 8))],
    "ic_check": [stroke("M4.5 12.5L9.5 17.5L19.5 7.5")],
    "ic_close": [stroke("M6 6L18 18"), stroke("M18 6L6 18")],
    # Playback: solid shapes, so they read at a glance over video.
    "ic_play": [fill("M7 4.5V19.5L19 12Z")],
    "ic_pause": [fill(rect(6, 5, 4, 14)), fill(rect(14, 5, 4, 14))],
    "ic_stop": [fill(rect(6, 6, 12, 12))],
    # A name that can be changed: a square-ended pencil on 45°, its tip a filled square corner.
    "ic_edit": [stroke("M15.5 4.5L19.5 8.5L9 19H5V15Z"), stroke("M13 7L17 11")],
    # Bluetooth's rune, in straight strokes.
    "ic_bluetooth": [stroke("M7 7.5L16.5 16.5L12 21V3L16.5 7.5L7 16.5")],
    # Nothing leaves the device: a squared shield, its point at 45°, with a tick.
    "ic_shield": [stroke("M4.75 3.75H19.25V13L12 20.25L4.75 13Z"), stroke("M8.5 11L11 13.5L15.5 9")],
    # An espresso machine, solid: the housing, its column and drip tray, the group head over the cup.
    "ic_machine": [fill(rect(4, 3, 16, 7)), fill(rect(4, 10, 3, 10)), fill(rect(4, 19, 16, 2)),
                   fill(rect(10.5, 10, 3, 2.5)), fill(rect(9.5, 14, 5, 4))],
    # A shot file: a page with its corner folded on 45°.
    "ic_file": [stroke("M5.75 2.75H14L18.25 7V21.25H5.75Z"), stroke("M13.5 2.75V7.5H18.25")],
    # A video: the camera body and its lens.
    "ic_film": [stroke(rect(2.75, 6.75, 13, 10.5)), fill("M17.5 12L21.5 8V16Z")],
    # Settings as an instrument's sliders: two rails, two square knobs.
    "ic_settings": [stroke("M3 8H21"), stroke("M3 16H21"), fill(rect(13, 5, 6, 6)), fill(rect(5, 13, 6, 6))],
    # Recording: the record key's square frame around its dot, a square too.
    "ic_record": [stroke(rect(3.75, 3.75, 16.5, 16.5)), fill(rect(8.5, 8.5, 7, 7))],
    # About OnlyShots: a question in a square, its dot a square.
    "ic_help": [stroke(rect(3.75, 3.75, 16.5, 16.5)), stroke("M9 9.5V8H15V12H12V14.5"), fill(rect(11.25, 16, 1.5, 1.5))],
    # A shot start the recording fixed: a squared padlock.
    "ic_lock": [stroke("M8 10.5V6H16V10.5"), fill(rect(5, 10.5, 14, 10))],
    # A notification: a squared bell, its sides at 45° to a flat foot, the clapper a square.
    "ic_bell": [stroke("M8 4.75H16V12L19.25 15.25V17.25H4.75V15.25L8 12Z"), fill(rect(10.5, 18.75, 3, 2.5))],
    # Share: an arrow up out of an open box.
    "ic_share": [stroke("M12 3.5V14.5"), stroke("M7.5 8L12 3.5L16.5 8"), stroke("M5.25 11.5V20.25H18.75V11.5")],
    # The gallery: a picture frame, a mountain on 45° in it, the sun a square.
    "ic_gallery": [stroke(rect(3.75, 4.75, 16.5, 14.5)), stroke("M3.75 17L9 11.75L13 15.75L15.5 13.25L20.25 18"),
                   fill(rect(14.5, 7.5, 2.5, 2.5))],
}


def svg(shapes):
    parts = []
    for kind, d in shapes:
        if kind == "stroke":
            parts.append(f'<path d="{d}" fill="none" stroke="currentColor" stroke-width="{W}" '
                         'stroke-linecap="square" stroke-linejoin="miter"/>')
        else:
            parts.append(f'<path d="{d}" fill="currentColor"/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">{"".join(parts)}</svg>'


def vector(name, shapes):
    paths = []
    for kind, d in shapes:
        if kind == "stroke":
            paths.append(f'    <path\n        android:pathData="{d}"\n        android:strokeColor="#FFFFFFFF"\n'
                         f'        android:strokeWidth="{W}"\n        android:strokeLineCap="square"\n'
                         '        android:strokeLineJoin="miter" />')
        else:
            paths.append(f'    <path\n        android:pathData="{d}"\n        android:fillColor="#FFFFFFFF" />')
    return ("<!-- OnlyShots' own icon, written by design/icons/build.py: edit that, not this. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n' + "\n".join(paths) + "\n</vector>\n")


def main():
    os.makedirs(SVG, exist_ok=True)
    for f in os.listdir(SVG):
        os.remove(os.path.join(SVG, f))
    cells = []
    for name, shapes in ICONS.items():
        open(os.path.join(OUT, name + ".xml"), "w").write(vector(name, shapes))
        s = svg(shapes)
        open(os.path.join(SVG, name[3:] + ".svg"), "w").write(s + "\n")
        big = s.replace('viewBox="0 0 24 24"', 'viewBox="0 0 24 24" width="48" height="48"')
        small = s.replace('viewBox="0 0 24 24"', 'viewBox="0 0 24 24" width="16" height="16"')
        cells.append(f"<div class=c>{big}<div class=s>{small}{small.replace('16', '24', 2)}</div>"
                     f"<span>{name[3:]}</span></div>")
    open(os.path.join(HERE, "sheet.html"), "w").write(
        "<html><body style='margin:0;padding:12px;background:#0b0b0b;color:#e8e6e1;font:10px monospace'>"
        "<style>.c{display:inline-flex;flex-direction:column;align-items:center;gap:6px;width:120px;padding:10px 0;"
        "border:1px solid #2a2a28;margin:0 -1px -1px 0}.s{display:flex;gap:10px;align-items:center}svg{color:#e8e6e1}"
        "</style>" + "".join(cells) + "</body></html>")
    print(len(ICONS), "icons")


if __name__ == "__main__":
    main()
