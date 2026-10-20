"""Builds the OnlyShots · Viewfinder logo kit's SVG masters from one description of the geometry.

    python3 design/logo/src/build.py

Every shape is a filled outline (no strokes, no live text), so the files scale, cut and recolour predictably.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from ttf_outline import Font, word_path  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', 'kit'))
FONT = os.path.normpath(
    os.path.join(HERE, '..', '..', '..', 'core', 'designsystem', 'src', 'commonMain', 'composeResources', 'font',
                 'open_sans_semibold.ttf'))

# Brand colours: the app's own (core/designsystem Tokens.kt).
ORANGE = '#EF4E2B'      # CgColors.Brand.Orange
ESPRESSO = '#120D0B'    # CgColors.Surface.Base
TILE = '#1C1412'        # CgColors.Brand.IconBackground
CREAM = '#F4EEEA'       # a warm white for the frame on dark grounds

# ── geometry ───────────────────────────────────────────────────────────────────────────────────────────────
# A 256 grid. The frame is four L brackets on a 196-wide box (30..226 at the outer edge); each arm is 48 long on
# the centreline, the corner a true arc. The curve is the shot: a short preinfusion flat, the rise, the peak, the
# long decline; the dot, set apart from its end, is "now".

def bracket(w, arm=48, inset=40, r=20):
    """Top-left bracket as a filled outline: centreline (inset, inset+arm) → corner arc → (inset+arm, inset)."""
    h = w / 2
    a, c = inset, inset + r          # line x, corner centre
    end = inset + arm
    ro, ri = r + h, r - h
    return (f'M{a - h} {end} V{c} A{ro} {ro} 0 0 1 {c} {a - h} H{end} A{h} {h} 0 0 1 {end} {a + h} '
            f'H{c} A{ri} {ri} 0 0 0 {a + h} {c} V{end} A{h} {h} 0 0 1 {a - h} {end} Z')


def rotate_path(d, quarter):
    """Rotate a path of M/V/H/A commands by quarter turns about (128,128), as absolute M/L/A commands."""
    import re
    toks = re.findall(r'[MVHAZ]|-?\d+(?:\.\d+)?', d)
    out, i, x, y = [], 0, 0.0, 0.0
    def rot(px, py):
        for _ in range(quarter):
            px, py = 256 - py, px
        return round(px, 3), round(py, 3)
    while i < len(toks):
        t = toks[i]; i += 1
        if t == 'M':
            x, y = float(toks[i]), float(toks[i + 1]); i += 2; out.append('M%s %s' % rot(x, y))
        elif t == 'V':
            y = float(toks[i]); i += 1; out.append('L%s %s' % rot(x, y))
        elif t == 'H':
            x = float(toks[i]); i += 1; out.append('L%s %s' % rot(x, y))
        elif t == 'A':
            rx, ry, xr, la, sw, ex, ey = toks[i:i + 7]; i += 7
            x, y = float(ex), float(ey)
            out.append(f'A{rx} {ry} {xr} {la} {sw} ' + '%s %s' % rot(x, y))
        elif t == 'Z':
            out.append('Z')
    return ''.join(out)


def frame(w):
    b = bracket(w)
    return ''.join(rotate_path(b, q) for q in range(4))


def cubic(p0, p1, p2, p3, t):
    u = 1 - t
    return tuple(u ** 3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3))


def centreline(dx, dy, scale=1.0):
    """Sample the shot curve's centreline densely: flat, then two cubics, tangent-continuous at the joins. [scale] draws
    it in towards the frame's centre, for room between the curve and the brackets."""
    S = lambda p: (128 + (p[0] + dx - 128) * scale, 128 + (p[1] + dy - 128) * scale)
    pts = [S((72, 164))]
    pts += [S((72 + 16 * k / 6, 164)) for k in range(1, 7)]
    a = [(88, 164), (108, 164), (110, 104), (132, 104)]
    b = [(132, 104), (152, 104), (160, 122), (170, 126)]
    for seg in (a, b):
        pts += [S(cubic(*seg, k / 40)) for k in range(1, 41)]
    return pts


def stroke_outline(pts, w, keep=40):
    """A round-capped stroke of width [w] along [pts], as one filled outline (offset sides joined by arcs)."""
    h = w / 2
    n = len(pts)
    normals = []
    for i in range(n):
        a = pts[max(i - 1, 0)]; b = pts[min(i + 1, n - 1)]
        tx, ty = b[0] - a[0], b[1] - a[1]
        L = math.hypot(tx, ty)
        normals.append((-ty / L, tx / L))
    left = [(p[0] + nx * h, p[1] + ny * h) for p, (nx, ny) in zip(pts, normals)]
    right = [(p[0] - nx * h, p[1] - ny * h) for p, (nx, ny) in zip(pts, normals)]
    # Keep a subset of points for a light path, always with both ends.
    idx = sorted(set([0, n - 1] + [round(k * (n - 1) / keep) for k in range(keep + 1)]))
    def smooth(seq):
        s = [seq[i] for i in idx]
        d = []
        for i in range(len(s) - 1):
            p0 = s[max(i - 1, 0)]; p1 = s[i]; p2 = s[i + 1]; p3 = s[min(i + 2, len(s) - 1)]
            c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
            c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
            d.append('C%.2f %.2f %.2f %.2f %.2f %.2f' % (*c1, *c2, *p2))
        return s, ''.join(d)
    ls, ld = smooth(left)
    rs, rd = smooth_rev(right, idx)
    return ('M%.2f %.2f' % ls[0]) + ld + ('A%g %g 0 0 0 %.2f %.2f' % (h, h, *rs[0])) + rd + \
        ('A%g %g 0 0 0 %.2f %.2f' % (h, h, *ls[0])) + 'Z'


def smooth_rev(seq, idx):
    s = [seq[i] for i in idx][::-1]
    d = []
    for i in range(len(s) - 1):
        p0 = s[max(i - 1, 0)]; p1 = s[i]; p2 = s[i + 1]; p3 = s[min(i + 2, len(s) - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d.append('C%.2f %.2f %.2f %.2f %.2f %.2f' % (*c1, *c2, *p2))
    return s, ''.join(d)


def dot_path(cx, cy, r):
    return f'M{cx - r:.2f} {cy:.2f}A{r} {r} 0 1 0 {cx + r:.2f} {cy:.2f}A{r} {r} 0 1 0 {cx - r:.2f} {cy:.2f}Z'


def symbol(w, dot_r, scale, gap=9):
    """(frame path, shot path) for one weight. The curve is shifted to sit at the frame's optical centre."""
    dx, dy = -9, -6
    pts = centreline(dx, dy, scale)
    end, before = pts[-1], pts[-4]
    tx, ty = end[0] - before[0], end[1] - before[1]
    L = math.hypot(tx, ty)
    reach = w / 2 + gap + dot_r
    cx, cy = end[0] + tx / L * reach, end[1] + ty / L * reach
    return frame(w), stroke_outline(pts, w) + dot_path(cx, cy, dot_r)


WEIGHTS = {
    'regular': (20, 13, 0.9),     # everything from 48 px up
    'small': (28, 17, 0.8),       # 32 px and below: heavier, so the frame and the dot survive
    'reversed': (18, 12, 0.9),    # light on dark at large sizes: thinned against irradiation
}

# ── files ──────────────────────────────────────────────────────────────────────────────────────────────────

def svg(view, body, title, w=None, h=None):
    size = f' width="{w}" height="{h}"' if w else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}"{size}>\n'
            f'  <title>{title}</title>\n{body}</svg>\n')


def mark(weight, frame_c, shot_c, transform=''):
    f, s = symbol(*WEIGHTS[weight])
    g = f' transform="{transform}"' if transform else ''
    return f'  <g{g}>\n    <path fill="{frame_c}" d="{f}"/>\n    <path fill="{shot_c}" d="{s}"/>\n  </g>\n'


def write(name, text):
    with open(os.path.join(OUT, name), 'w') as fh:
        fh.write(text)


def main():
    os.makedirs(OUT, exist_ok=True)
    T = 'OnlyShots logo'
    # Symbol: one-colour master, the two-colour brand versions, and the size cuts.
    write('onlyshots-symbol-master.svg', svg('0 0 256 256', mark('regular', '#000000', '#000000'), T))
    write('onlyshots-symbol-small-master.svg', svg('0 0 256 256', mark('small', '#000000', '#000000'), T))
    write('onlyshots-symbol.svg', svg('0 0 256 256', mark('regular', ESPRESSO, ORANGE), T))
    write('onlyshots-symbol-on-dark.svg', svg('0 0 256 256', mark('reversed', CREAM, ORANGE), T))
    write('onlyshots-symbol-small.svg', svg('0 0 256 256', mark('small', ESPRESSO, ORANGE), T))
    write('onlyshots-symbol-small-on-dark.svg', svg('0 0 256 256', mark('small', CREAM, ORANGE), T))

    # Wordmark: Open Sans SemiBold, outlined, tracked in a touch, with a few pairs eased by eye.
    font = Font(FONT)
    size, base = 112, 158
    pairs = {'ly': -0.02, 'ys': 0.006, 'ot': -0.008, 'ts': -0.012}
    wd, wend = word_path(font, 'onlyshots', 0, base, size, tracking=-0.012, adjust=pairs)
    wwidth = math.ceil(wend)

    # Horizontal lockup: symbol, a gap of two frame strokes, the word on the symbol's optical centre line.
    gap = 40
    hw = 256 + gap + wwidth + 8
    for name, fc, sc, wc, weight in [('onlyshots-horizontal.svg', ESPRESSO, ORANGE, ESPRESSO, 'regular'),
                                     ('onlyshots-horizontal-on-dark.svg', CREAM, ORANGE, CREAM, 'reversed'),
                                     ('onlyshots-horizontal-master.svg', '#000000', '#000000', '#000000', 'regular')]:
        body = mark(weight, fc, sc) + f'  <path fill="{wc}" transform="translate({256 + gap} 0)" d="{wd}"/>\n'
        write(name, svg(f'0 0 {hw} 256', body, T))

    # Stacked lockup: the word centred under the symbol, a quarter wider than it.
    ws = 256 * 1.25 / wwidth
    sw = 256 * 1.25 + 16
    sx = (sw - 256) / 2
    wx = (sw - wwidth * ws) / 2
    for name, fc, sc, wc, weight in [('onlyshots-stacked.svg', ESPRESSO, ORANGE, ESPRESSO, 'regular'),
                                     ('onlyshots-stacked-on-dark.svg', CREAM, ORANGE, CREAM, 'reversed')]:
        body = mark(weight, fc, sc, f'translate({sx} 0)') + \
            f'  <path fill="{wc}" transform="translate({wx:.2f} {258 - 78 * ws:.2f}) scale({ws:.4f})" d="{wd}"/>\n'
        write(name, svg(f'0 0 {sw:.0f} 330', body, T))

    # Wordmark alone.
    write('onlyshots-wordmark.svg', svg(f'0 50 {wwidth + 8} 170', f'  <path fill="{ESPRESSO}" d="{wd}"/>\n', T))

    # One-colour variants: the masters in black, white and the brand orange.
    os.makedirs(os.path.join(OUT, 'variants'), exist_ok=True)
    for kind in ('symbol', 'horizontal'):
        master = open(os.path.join(OUT, f'onlyshots-{kind}-master.svg')).read()
        for label, colour in (('black', '#000000'), ('white', '#ffffff'), ('mono-ef4e2b', '#ef4e2b')):
            text = master.replace('#000000', colour).replace('<svg ', f'<svg fill="{colour}" ', 1)
            write(f'variants/onlyshots-{kind}-{label}.svg', text)

    # App icons. iOS and the stores: the full square, the system rounds it. The mark sits in the middle 62 %.
    def tile_icon(weight, scale, rounded=False, size=1024):
        k = size / 256 * scale
        off = size * (1 - scale) / 2
        rect = (f'  <rect width="{size}" height="{size}" rx="{size * 0.2237:.0f}" fill="{TILE}"/>\n' if rounded
                else f'  <rect width="{size}" height="{size}" fill="{TILE}"/>\n')
        return svg(f'0 0 {size} {size}', rect + mark(weight, CREAM, ORANGE, f'translate({off:.2f} {off:.2f}) scale({k:.4f})'),
                   T, size, size)
    write('app-icon-ios-1024.svg', tile_icon('regular', 0.62))
    write('app-icon-store-512.svg', tile_icon('regular', 0.62, size=512))
    write('app-icon-desktop-1024.svg', tile_icon('regular', 0.56, rounded=True))
    write('app-icon-small-tile.svg', tile_icon('small', 0.70, rounded=True, size=256))

    # Android adaptive icon: 108 × 108 dp layers; the mark inside the 66 dp safe circle (and so any mask).
    k = 60 / 256
    fg = mark('regular', CREAM, ORANGE, f'translate(24 24) scale({k:.4f})')
    os.makedirs(os.path.join(OUT, 'android'), exist_ok=True)
    write('android/android-adaptive-foreground.svg', svg('0 0 108 108', fg, T, 108, 108))
    write('android/android-adaptive-background.svg', svg('0 0 108 108', f'  <rect width="108" height="108" fill="{TILE}"/>\n', T, 108, 108))
    mono = mark('regular', '#000000', '#000000', f'translate(24 24) scale({k:.4f})')
    write('android/android-adaptive-monochrome.svg', svg('0 0 108 108', mono, T, 108, 108))
    print('wrote kit to', OUT, '· word width', wwidth)


if __name__ == '__main__':
    main()
