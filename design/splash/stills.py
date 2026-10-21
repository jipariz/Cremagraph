"""Key frames of the splash as SVG stills, computed with the prototype's timeline and easing (app profile)."""
import math, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'logo', 'src'))
from build import frame, centreline, stroke_outline, dot_path, capsule, PLAYHEAD_X, PLATEAU_Y  # noqa: E402

W = 20
FRAME_C, SHOT_C, BG = '#F4EEEA', '#EF4E2B', '#120D0B'
P = dict(focus=(0, 320), stagger=40, head=(260, 420), draw=(380, 1100), pulse=(1100, 1400))
DIAG = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
clamp = lambda x: max(0.0, min(1.0, x))
seg = lambda t, a, b: clamp((t - a) / (b - a))
out_cubic = lambda x: 1 - (1 - x) ** 3
in_out_sine = lambda x: -(math.cos(math.pi * x) - 1) / 2
out_back = lambda x, s=1.5: 1 + (s + 1) * (x - 1) ** 3 + s * (x - 1) ** 2
brackets = ['M' + p for p in frame(W).split('M') if p]
pts = centreline(40 + W + 12)
lengths = [0.0]
for a, b in zip(pts, pts[1:]):
    lengths.append(lengths[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
total = lengths[-1]

def upto(p):
    """The curve's samples up to fraction p of its length, ending exactly at the tip."""
    target = total * p
    out = [pts[0]]
    for i in range(1, len(pts)):
        if lengths[i] >= target:
            a, b = pts[i - 1], pts[i]
            f = (target - lengths[i - 1]) / max(1e-9, lengths[i] - lengths[i - 1])
            out.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
            return out
        out.append(pts[i])
    return out

def still(t):
    body = ''
    for i, d in enumerate(brackets):
        k = seg(t, P['focus'][0] + i * P['stagger'], P['focus'][1] + i * P['stagger'])
        off = (1 - out_back(k, 1.4)) * 26
        op = out_cubic(seg(t, P['focus'][0] + i * P['stagger'], P['focus'][0] + i * P['stagger'] + 140))
        body += f'<path fill="{FRAME_C}" opacity="{op:.3f}" transform="translate({DIAG[i][0] * off:.2f} {DIAG[i][1] * off:.2f})" d="{d}"/>'
    p = in_out_sine(seg(t, *P['draw']))
    h = out_cubic(seg(t, *P['head']))
    part = upto(p) if p > 0 else [pts[0]]
    tip = part[-1]
    if h > 0:
        body += f'<path fill="{FRAME_C}" d="{capsule(tip[0], 130 - 52 * h, 130 + 52 * h, 14)}"/>'
    if p > 0.004 and len(part) > 2:
        body += f'<path fill="{SHOT_C}" d="{stroke_outline(part, W, keep=min(40, len(part) - 1))}"/>'
    pk = seg(t, *P['pulse'])
    bump = math.sin(math.pi * min(1, pk * 1.6)) * 0.28
    rk = out_cubic(pk)
    if 0 < pk < 1:
        body += f'<circle cx="{tip[0]:.2f}" cy="{tip[1]:.2f}" r="{18 + 26 * rk:.2f}" fill="none" stroke="{SHOT_C}" stroke-width="4" opacity="{0.6 * (1 - rk):.3f}"/>'
    r = 18 * out_back(seg(t, P['draw'][0], P['draw'][0] + 160), 1.8) * (1 + bump)
    if r > 0.1:
        body += f'<path fill="{SHOT_C}" d="{dot_path(tip[0], tip[1], round(r, 2))}"/>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-40 -40 336 336"><rect x="-40" y="-40" width="336" height="336" fill="{BG}"/>{body}</svg>'

os.makedirs(os.path.join(HERE, 'frames'), exist_ok=True)
for t in (150, 420, 700, 1000, 1180):
    open(os.path.join(HERE, 'frames', f'still-{t}.svg'), 'w').write(still(t))
print('ok')
