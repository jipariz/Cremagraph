"""Builds the OnlyShots · Signal tile logo kit's SVG masters from one description of the geometry.

    python3 design/logo/src/build.py

Every shape is a filled outline (no strokes, no live text, no holes made by fill rules), so the files scale, cut and
recolour predictably.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from ttf_outline import Font, word_path  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', 'kit'))
FONT = os.path.join(HERE, 'fonts', 'IBMPlexMono-SemiBold.ttf')

# Brand colours: the Instrument palette (the app's new tokens).
SIGNAL = '#FF5A1F'      # the tile, the only accent
INK = '#0B0B0B'         # the ground on dark, the cut on light icons
BONE = '#E8E6E1'        # text on dark

# ── geometry ───────────────────────────────────────────────────────────────────────────────────────────────
# A 256 grid. The tile is a 192 square (32..224), square cornered. A channel is cut out of it: the shot's trace
# (a short preinfusion flat, the rise, the plateau) enters at the tile's left edge and runs to the pen head, a square,
# which stands on a stem. The stem stops short of the bottom edge so the tile stays one piece. Ground shows through
# the channel, so the mark is one colour on any background.
T0, T1 = 32, 224
WEIGHTS = {
    #           trace  head  stem
    'regular': (20,    32,   12),
    'small':   (24,    36,   16),   # 16–47 px and every favicon
}
PEN_X, PEN_Y = 172, 100            # the head's centre: where the trace meets the pen
FLAT_Y = 166                       # the preinfusion flat
RISE = (80, 140)                   # the S-curve runs from x 80 to x 140
STEM_END = T1 - 28


def f(v):
    s = f'{v:.2f}'.rstrip('0').rstrip('.')
    return '0' if s == '-0' else s


def cubic(p0, p1, p2, p3, t):
    u = 1 - t
    return (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
            u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])


def centreline(end_x, n=40):
    xa, xb = RISE
    c = [(xa, FLAT_Y), ((xa + xb) / 2, FLAT_Y), ((xa + xb) / 2, PEN_Y), (xb, PEN_Y)]
    pts = [(T0, FLAT_Y)] + [cubic(*c, i / n) for i in range(n + 1)] + [(end_x, PEN_Y)]
    out = [pts[0]]
    for p in pts[1:]:
        if math.dist(p, out[-1]) > 0.5:
            out.append(p)
    return out


def edges(pts, w):
    """The two edges of the trace: lower (below the centreline) and upper, both from left to right."""
    h = w / 2
    lower, upper = [], []
    for i, p in enumerate(pts):
        a, b = pts[max(i - 1, 0)], pts[min(i + 1, len(pts) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln          # points down for a left-to-right segment (SVG y grows down)
        lower.append((p[0] + nx * h, p[1] + ny * h))
        upper.append((p[0] - nx * h, p[1] - ny * h))
    lower[0] = (T0, lower[0][1])            # square the entry exactly on the tile's edge
    upper[0] = (T0, upper[0][1])
    return lower, upper


def channel_points(weight):
    """The channel (trace + head + stem) as one closed outline, clockwise from the trace's lower entry."""
    tw, m, sw = WEIGHTS[weight]
    hl, hr, ht, hb = PEN_X - m / 2, PEN_X + m / 2, PEN_Y - m / 2, PEN_Y + m / 2
    sl, sr = PEN_X - sw / 2, PEN_X + sw / 2
    lower, upper = edges(centreline(hl), tw)
    lower[-1] = (hl, PEN_Y + tw / 2)
    upper[-1] = (hl, PEN_Y - tw / 2)
    pen = [(hl, hb), (sl, hb), (sl, STEM_END), (sr, STEM_END), (sr, hb), (hr, hb), (hr, ht), (hl, ht)]
    return lower, pen, upper[::-1]


def tile_path(weight, k=1.0, ox=0.0, oy=0.0):
    """The tile with the channel cut into it: a single outline that runs around the tile and in through the cut."""
    lower, pen, upper_back = channel_points(weight)
    P = lambda x, y: f'{f(x * k + ox)} {f(y * k + oy)}'  # noqa: E731
    pts = [(T0, T0), (T1, T0), (T1, T1), (T0, T1)]                 # tile: top-left, clockwise to bottom-left
    pts += [lower[0]]                                              # up the left edge to the cut's lower lip
    # Into the cut: along the lower lip to the pen, around the pen, back along the upper lip, out to the edge.
    pts += lower[1:] + pen + upper_back
    return 'M' + 'L'.join(P(x, y) for x, y in pts) + 'Z'


def channel_path(weight, k=1.0, ox=0.0, oy=0.0):
    """The channel alone (for Android's foreground and monochrome layers)."""
    lower, pen, upper_back = channel_points(weight)
    P = lambda x, y: f'{f(x * k + ox)} {f(y * k + oy)}'  # noqa: E731
    return 'M' + 'L'.join(P(x, y) for x, y in lower + pen + upper_back) + 'Z'


def svg(view, body, title, w=None, h=None):
    size = f' width="{w}" height="{h}"' if w else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}"{size}>\n'
            f'  <title>{title}</title>\n{body}</svg>\n')


def mark(weight, colour, transform=''):
    t = f' transform="{transform}"' if transform else ''
    return f'  <path fill="{colour}"{t} d="{tile_path(weight)}"/>\n'


def write(name, text):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w').write(text)


def main():
    os.makedirs(OUT, exist_ok=True)
    T = 'OnlyShots logo'
    ground = f'  <rect width="256" height="256" fill="{INK}"/>\n'

    # Symbol: one-colour masters and the brand versions. The mark is one colour; on dark it simply sits on ink.
    write('onlyshots-symbol-master.svg', svg('0 0 256 256', mark('regular', '#000000'), T))
    write('onlyshots-symbol-small-master.svg', svg('0 0 256 256', mark('small', '#000000'), T))
    write('onlyshots-symbol.svg', svg('0 0 256 256', mark('regular', SIGNAL), T))
    write('onlyshots-symbol-small.svg', svg('0 0 256 256', mark('small', SIGNAL), T))
    write('onlyshots-symbol-on-dark.svg', svg('0 0 256 256', ground + mark('regular', SIGNAL), T))
    write('onlyshots-symbol-small-on-dark.svg', svg('0 0 256 256', ground + mark('small', SIGNAL), T))

    # Wordmark: IBM Plex Mono SemiBold, upper case like the app's labels, outlined. A monospace face sets its own
    # rhythm; it is tracked out 2 % so the caps breathe at small sizes.
    font = Font(FONT)
    size = 100
    cap = 0.698 * size                      # Plex Mono's cap height
    wd, wend = word_path(font, 'ONLYSHOTS', 0, 0, size, tracking=0.02)
    wwidth = math.ceil(wend)

    # Horizontal lockup: the tile, a gap of two traces, the caps centred on the tile's centre line.
    gap = 40
    x0 = 0                                  # the same 32 margin on every side
    word_x = T1 + gap
    hw = word_x + wwidth + T0
    base = 128 + cap / 2
    for name, tc, wc, dark in [('onlyshots-horizontal.svg', SIGNAL, INK, False),
                               ('onlyshots-horizontal-on-dark.svg', SIGNAL, BONE, True),
                               ('onlyshots-horizontal-master.svg', '#000000', '#000000', False)]:
        body = (f'  <rect x="{x0}" width="{hw}" height="256" fill="{INK}"/>\n' if dark else '')
        body += mark('regular', tc) + f'  <path fill="{wc}" transform="translate({word_x} {f(base)})" d="{wd}"/>\n'
        write(name, svg(f'{x0} 0 {hw} 256', body, T))

    # Stacked lockup: the word centred under the tile, as wide as the tile.
    ws = (T1 - T0) / wwidth
    sh = 256 + cap * ws + 24
    for name, tc, wc, dark in [('onlyshots-stacked.svg', SIGNAL, INK, False),
                               ('onlyshots-stacked-on-dark.svg', SIGNAL, BONE, True)]:
        body = (f'  <rect width="256" height="{f(sh)}" fill="{INK}"/>\n' if dark else '')
        body += mark('regular', tc) + \
            f'  <path fill="{wc}" transform="translate({T0} {f(T1 + 32 + cap * ws)}) scale({ws:.4f})" d="{wd}"/>\n'
        write(name, svg(f'0 0 256 {f(sh)}', body, T))

    # Wordmark alone.
    write('onlyshots-wordmark.svg', svg(f'0 {f(-cap - 8)} {wwidth} {f(cap + 16)}', f'  <path fill="{INK}" d="{wd}"/>\n', T))

    # One-colour variants: black, white and signal.
    for kind in ('symbol', 'horizontal'):
        master = open(os.path.join(OUT, f'onlyshots-{kind}-master.svg')).read()
        for label, colour in (('black', '#000000'), ('white', '#ffffff'), ('mono-ff5a1f', '#ff5a1f')):
            write(f'variants/onlyshots-{kind}-{label}.svg', master.replace('#000000', colour))

    # App icons. iOS and the stores: the tile IS the icon, full bleed; the system rounds its corners. Ink fills the
    # cut, so the trace still enters from the icon's left edge.
    def full_bleed(weight, size):
        k = size / (T1 - T0)
        o = -T0 * k
        body = (f'  <rect width="{size}" height="{size}" fill="{INK}"/>\n'
                f'  <path fill="{SIGNAL}" d="{tile_path(weight, k, o, o)}"/>\n')
        return svg(f'0 0 {size} {size}', body, T, size, size)
    write('app-icon-ios-1024.svg', full_bleed('regular', 1024))
    write('app-icon-store-512.svg', full_bleed('regular', 512))

    # Desktop: the platforms' rounded-square template (824 in 1024), in ink, with the tile at 72 % inside it.
    def desktop(weight, size, scale):
        inset = size * 100 / 1024
        side = size - 2 * inset
        k = side * scale / (T1 - T0)
        o = inset + side * (1 - scale) / 2 - T0 * k
        body = (f'  <rect x="{f(inset)}" y="{f(inset)}" width="{f(side)}" height="{f(side)}" rx="{f(side * 0.2237)}" fill="{INK}"/>\n'
                f'  <path fill="{SIGNAL}" d="{tile_path(weight, k, o, o)}"/>\n')
        return svg(f'0 0 {size} {size}', body, T, size, size)
    write('app-icon-desktop-1024.svg', desktop('regular', 1024, 0.72))
    write('app-icon-small-tile.svg', desktop('small', 256, 0.76))

    # Android adaptive icon (108 × 108 dp): the background layer is the tile's signal; the foreground is the cut in
    # ink, mapped so the tile spans the whole layer — the trace runs in from the layer's edge and the pen sits inside
    # the 66 dp safe circle.
    k = 108 / (T1 - T0)
    o = -T0 * k
    write('android/android-adaptive-background.svg',
          svg('0 0 108 108', f'  <rect width="108" height="108" fill="{SIGNAL}"/>\n', T, 108, 108))
    write('android/android-adaptive-foreground.svg',
          svg('0 0 108 108', f'  <path fill="{INK}" d="{channel_path("regular", k, o, o)}"/>\n', T, 108, 108))
    write('android/android-adaptive-monochrome.svg',
          svg('0 0 108 108', f'  <path fill="#000000" d="{channel_path("regular", k, o, o)}"/>\n', T, 108, 108))
    print('wrote kit to', OUT, '· word width', wwidth)


if __name__ == '__main__':
    main()
