"""Instrument logo concepts for OnlyShots: three directions, black first, filled outlines only."""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'src'))
from ttf_outline import Font, word_path  # noqa: E402

FONT = os.path.join(HERE, '..', '..', 'src', 'fonts', 'IBMPlexMono-SemiBold.ttf')
OUT = HERE


def f(v):
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def rect(x, y, w, h):
    return f'M{f(x)} {f(y)}H{f(x + w)}V{f(y + h)}H{f(x)}Z'


def cubic(p0, p1, p2, p3, t):
    u = 1 - t
    return (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
            u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])


def trace_centreline(x0, x1, y0, ya, xa, xb, n=48):
    """Flat lead-in at y0 from x0 to xa, an S-curve up to ya ending at xb, flat to x1."""
    pts = [(x0, y0), (xa, y0)]
    c = [(xa, y0), (xa + (xb - xa) * 0.5, y0), (xa + (xb - xa) * 0.5, ya), (xb, ya)]
    for i in range(1, n + 1):
        pts.append(cubic(*c, i / n))
    pts.append((x1, ya))
    out = [pts[0]]
    for p in pts[1:]:
        if math.dist(p, out[-1]) > 0.4:
            out.append(p)
    return out


def ribbon(pts, w):
    """Outline a polyline centreline with butt ends (square instrument terminals)."""
    h = w / 2
    left, right = [], []
    for i, p in enumerate(pts):
        a = pts[max(i - 1, 0)]
        b = pts[min(i + 1, len(pts) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        left.append((p[0] + nx * h, p[1] + ny * h))
        right.append((p[0] - nx * h, p[1] - ny * h))
    ring = left + right[::-1]
    return 'M' + 'L'.join(f'{f(x)} {f(y)}' for x, y in ring) + 'Z'


def bracket_set(inset, arm, t):
    """Four square-ended L brackets: the viewfinder, drawn like crop marks."""
    a, b = inset, 256 - inset
    d = ''
    d += rect(a, a, arm, t) + rect(a, a + t, t, arm - t)                      # TL
    d += rect(b - arm, a, arm, t) + rect(b - t, a + t, t, arm - t)            # TR
    d += rect(b - arm, b - t, arm, t) + rect(b - t, b - arm, t, arm - t)      # BR
    d += rect(a, b - t, arm, t) + rect(a, b - arm, t, arm - t)                # BL
    return d


def svg(view, body, title='OnlyShots logo concept'):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}">\n  <title>{title}</title>\n{body}</svg>\n')


def path(d, fill='#000000', rule=None):
    r = f' fill-rule="{rule}"' if rule else ''
    return f'  <path fill="{fill}"{r} d="{d}"/>\n'


# --- A: Crop marks — the existing idea, redrawn as an instrument (evolution) ---------------------------------
def concept_a(frame='#000000', shot='#000000', small=False):
    t = 20 if small else 16
    arm = 60 if small else 56
    brackets = bracket_set(28 if small else 32, arm, t)
    px = 172                      # pen x
    pw = 16 if small else 12      # stem width
    my = 104                      # where the trace meets the pen
    m = 34 if small else 30       # the pen head: a square, the newest sample
    stem = rect(px - pw / 2, my, pw, 184 - my)          # the stem runs from the head down to the baseline only
    line = trace_centreline(64, px - m / 2, 166, my, 84, 140)
    trace = ribbon(line, 22 if small else 16)
    head = rect(px - m / 2, my - m / 2, m, m)
    return (path(brackets, frame) + path(stem, frame) + path(trace, shot) + path(head, shot))


# --- B: Signal tile — the frame becomes a solid tile, the trace is cut out of it (middle) ------------------
def concept_b(tile='#000000', knock='#FFFFFF', small=False):
    s0, s1 = 32, 224
    t = 24 if small else 20
    px = 172
    pw = 16 if small else 12
    my = 100
    m = 36 if small else 32
    line = trace_centreline(s0, px - m / 2, 166, my, 80, 140)
    trace = ribbon(line, t)
    stem = rect(px - pw / 2, my, pw, s1 - 28 - my)       # the stem stops short of the edge so the tile stays one piece
    head = rect(px - m / 2, my - m / 2, m, m)
    return (path(rect(s0, s0, s1 - s0, s1 - s0), tile) + path(trace, knock) + path(stem, knock) + path(head, knock))


# --- C: Dial and head — a pressure gauge read by a recording head (revolution) -----------------------------
def ring_arc(cx, cy, r0, r1, a0, a1, n=96):
    """Annular sector from angle a0 to a1 (degrees, clockwise from 3 o'clock in SVG space)."""
    outer = [(cx + r1 * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
              cy + r1 * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]
    inner = [(cx + r0 * math.cos(math.radians(a1 - (a1 - a0) * i / n)),
              cy + r0 * math.sin(math.radians(a1 - (a1 - a0) * i / n))) for i in range(n + 1)]
    return 'M' + 'L'.join(f'{f(x)} {f(y)}' for x, y in outer + inner) + 'Z'


def concept_c(frame='#000000', shot='#000000', small=False):
    cx, cy = 120, 128
    r1 = 96
    t = 30 if small else 24
    gap = 30 if small else 26               # degrees left open where the head sits
    dial = ring_arc(cx, cy, r1 - t, r1, gap, 360 - gap)
    hx = cx + r1 - t / 2 + 6                # the head: a bar standing in the opening
    hw = 16 if small else 12
    head = rect(hx - hw / 2, 64, hw, 128)
    m = 34 if small else 28
    marker = rect(hx - m / 2, cy - m / 2, m, m)
    return path(dial, frame) + path(head, shot) + path(marker, shot)


def wordmark(font):
    size, base = 92, 0
    d, end = word_path(font, 'ONLYSHOTS', 0, base, size, tracking=0.02)
    return d, end, size


def main():
    os.makedirs(OUT, exist_ok=True)
    font = Font(FONT)
    wd, wend, size = wordmark(font)
    cap = size * 0.698                       # Plex Mono cap height ≈ 0.698 em
    for key, fn in [('a-cropmarks', concept_a), ('b-signal-tile', concept_b), ('c-dial-head', concept_c)]:
        open(os.path.join(OUT, f'{key}.svg'), 'w').write(svg('0 0 256 256', fn()))
        open(os.path.join(OUT, f'{key}-small.svg'), 'w').write(svg('0 0 256 256', fn(small=True)))
        gap = 44
        w = 256 + gap + math.ceil(wend) + 8
        ty = 128 + cap / 2
        body = fn() + f'  <path fill="#000000" transform="translate({256 + gap} {f(ty)})" d="{wd}"/>\n'
        open(os.path.join(OUT, f'{key}-lockup.svg'), 'w').write(svg(f'0 0 {w} 256', body))
    # Colour hints (Instrument palette) for the recommendation.
    SIG, INK, BONE = '#FF5A1F', '#0B0B0B', '#E8E6E1'
    open(os.path.join(OUT, 'a-cropmarks-colour-dark.svg'), 'w').write(
        svg('0 0 256 256', path(rect(0, 0, 256, 256), INK) + concept_a(BONE, SIG)))
    open(os.path.join(OUT, 'b-signal-tile-colour-dark.svg'), 'w').write(
        svg('0 0 256 256', path(rect(0, 0, 256, 256), INK) + concept_b(SIG, INK)))
    open(os.path.join(OUT, 'c-dial-head-colour-dark.svg'), 'w').write(
        svg('0 0 256 256', path(rect(0, 0, 256, 256), INK) + concept_c(BONE, SIG)))


if __name__ == '__main__':
    main()
