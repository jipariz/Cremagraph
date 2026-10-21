"""The splash's timeline and drawing, shared by stills.py, launch.py and avd.py (and mirrored in splash.html).

The mark is the logo kit's Signal tile (design/logo/src/build.py, regular weight) on ink. At rest the frame equals the
logo exactly: the solid tile with the trace, head and stem cut out of it, ink showing through. While it plays, the
tile is drawn solid and the cut is drawn over it in ink. The pen (the head on its stem) is a playhead: it stands up at
the tile's left edge and sweeps through the shot, the head riding the trace, which is drawn behind it as a butt-capped
stroke along the logo's centreline. At the plateau's end the head pulses once, a ring going out from it, and the mark
hands off to the app.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'logo', 'src'))
import build  # noqa: E402

SIGNAL, INK = build.SIGNAL, build.INK
T0, T1 = build.T0, build.T1
TRACE_W, HEAD, STEM_W = build.WEIGHTS['regular']
PEN_X, PEN_Y, STEM_END = build.PEN_X, build.PEN_Y, build.STEM_END
HEAD_LEAD = HEAD / 2                       # the head's centre runs this far ahead of the trace's tip

# ── timelines (ms) ─────────────────────────────────────────────────────────────────────────────────────────────
APP = dict(wipe=(0, 260), land=(220, 380), draw=(380, 1100), pulse=(1100, 1400), handoff=(1400, 1750),
           screen_in=1520, total=1750)
ANDROID = dict(wipe=(0, 200), land=(180, 300), draw=(300, 1000), total=1000)
WEB_LOOP = dict(draw=720, hold=200, refill=250)
RING = 40                                   # how far the pulse's ring grows past the head
BUMP = 0.25                                 # how much the head swells in the pulse

# Easings, as CSS cubic-bezier control points (also Compose CubicBezierEasing / Android PathInterpolator).
EASE = dict(wipe=(.33, 1, .68, 1),          # out-cubic
            land=(.34, 1.6, .64, 1),        # out-back 1.6
            draw=(.37, 0, .63, 1),          # in-out sine
            ring=(.33, 1, .68, 1),          # out-cubic
            handoff=(.65, 0, .35, 1))       # in-out cubic


def bezier(x1, y1, x2, y2):
    """CSS cubic-bezier(x1, y1, x2, y2) as a function of x in [0, 1]."""
    def at(t, a, b):
        u = 1 - t
        return 3 * u * u * t * a + 3 * u * t * t * b + t ** 3

    def f(x):
        if x <= 0:
            return 0.0
        if x >= 1:
            return 1.0
        lo, hi = 0.0, 1.0
        for _ in range(40):
            mid = (lo + hi) / 2
            if at(mid, x1, x2) < x:
                lo = mid
            else:
                hi = mid
        return at((lo + hi) / 2, y1, y2)
    return f


E = {k: bezier(*v) for k, v in EASE.items()}


def seg(t, ab):
    a, b = ab
    return max(0.0, min(1.0, (t - a) / (b - a)))


# ── the centreline, extended by HEAD_LEAD so the head can ride ahead of the tip ─────────────────────────────────
TRACE_END = PEN_X - HEAD / 2               # the trace stops at the head's left edge
PATH = build.centreline(TRACE_END, n=400) + [(PEN_X, PEN_Y)]
LEN = [0.0]
for a, b in zip(PATH, PATH[1:]):
    LEN.append(LEN[-1] + math.dist(a, b))
TRACE_LEN = LEN[-2]                         # up to the head's left edge
CURVE_D = (f'M{T0} {build.FLAT_Y} H{build.RISE[0]} C{(build.RISE[0] + build.RISE[1]) / 2} {build.FLAT_Y} '
           f'{(build.RISE[0] + build.RISE[1]) / 2} {PEN_Y} {build.RISE[1]} {PEN_Y} H{TRACE_END}')


def point_at(s):
    s = max(0.0, min(LEN[-1], s))
    for i in range(1, len(PATH)):
        if LEN[i] >= s:
            a, b = PATH[i - 1], PATH[i]
            f = (s - LEN[i - 1]) / max(1e-9, LEN[i] - LEN[i - 1])
            return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f
    return PATH[-1]


def upto(s):
    out = [PATH[0]]
    for i in range(1, len(PATH)):
        if LEN[i] >= s:
            out.append(point_at(s))
            return out
        out.append(PATH[i])
    return out


def state(t, p=APP):
    """Everything the frame at time t needs, as plain numbers."""
    wipe = E['wipe'](seg(t, p['wipe']))
    land = E['land'](seg(t, p['land']))
    draw = E['draw'](seg(t, p['draw']))
    pulse = seg(t, p['pulse']) if 'pulse' in p else 0.0
    s = TRACE_LEN * draw
    head = point_at(s + HEAD_LEAD)
    bump = math.sin(math.pi * min(1.0, pulse * 1.6)) * BUMP if 0 < pulse < 1 else 0.0
    ring = E['ring'](pulse)
    return dict(wipe=wipe, land=land, draw=draw, stem=min(1.0, land), s=s, head=head, head_scale=land * (1 + bump),
                ring=HEAD + RING * ring, ring_alpha=0.85 * (1 - ring) if 0 < pulse < 1 else 0.0)


def frame_svg(t, p=APP, view='-40 -40 336 336', ground=True):
    st = state(t, p)
    x0 = T0
    body = ''
    if ground:
        vb = [float(v) for v in view.split()]
        body += f'<rect x="{vb[0]}" y="{vb[1]}" width="{vb[2]}" height="{vb[3]}" fill="{INK}"/>'
    if st['wipe'] >= 1 and st['draw'] >= 1 and st['head_scale'] == 1 and st['ring_alpha'] == 0:
        # At rest: the logo itself, one outline.
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}">{body}<path fill="{SIGNAL}" d="{build.tile_path("regular")}"/></svg>'
    w = (T1 - T0) * st['wipe']
    if w > 0:
        body += f'<rect x="{x0}" y="{T0}" width="{w:.2f}" height="{T1 - T0}" fill="{SIGNAL}"/>'
    if st['s'] > 0.5:
        pts = upto(st['s'])
        d = 'M' + 'L'.join(f'{x:.2f} {y:.2f}' for x, y in pts)
        body += f'<path d="{d}" fill="none" stroke="{INK}" stroke-width="{TRACE_W}" stroke-linecap="butt" stroke-linejoin="round"/>'
    hx, hy = st['head']
    if st['stem'] > 0:
        # The playhead's stem stands under the head wherever it is, down to the stem's foot.
        top = hy + HEAD / 2
        body += f'<rect x="{hx - STEM_W / 2:.2f}" y="{top:.2f}" width="{STEM_W}" height="{max(0.0, STEM_END - top) * st["stem"]:.2f}" fill="{INK}"/>'
    hs = HEAD * st['head_scale']
    if hs > 0.2:
        x = max(hx, T0 + hs / 2)             # the landing overshoot never pokes out of the tile's edge
        body += f'<rect x="{x - hs / 2:.2f}" y="{hy - hs / 2:.2f}" width="{hs:.2f}" height="{hs:.2f}" fill="{INK}"/>'
    if st['ring_alpha'] > 0:
        r = st['ring']
        body += (f'<rect x="{hx - r / 2:.2f}" y="{hy - r / 2:.2f}" width="{r:.2f}" height="{r:.2f}" fill="none" '
                 f'stroke="{INK}" stroke-width="5" opacity="{st["ring_alpha"]:.3f}"/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}">{body}</svg>'
