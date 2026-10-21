"""REC-dot variants of B · Playhead, in colour, on espresso and on white."""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src'))
from build import frame  # noqa: E402
F = frame(20)
ORANGE, REC, CREAM, ESP = '#EF4E2B', '#DC1B34', '#F4EEEA', '#120D0B'

def mark(frame_c, curve_c, head_c, rec=None):
    line = f'fill="none" stroke="{curve_c}" stroke-width="20" stroke-linecap="round" stroke-linejoin="round"'
    s = f'<path fill="{frame_c}" d="{F}"/>'
    s += f'<path {line} d="M64 168 H86 C104 168 110 110 132 110 H170"/>'
    s += f'<path fill="none" stroke="{frame_c}" stroke-width="14" stroke-linecap="round" d="M178 78 V182"/>'
    s += f'<circle cx="178" cy="110" r="18" fill="{head_c}"/>'
    if rec == 'corner':   # in the top-left corner of the frame, as a camera shows REC
        s += f'<circle cx="80" cy="80" r="12" fill="{REC}"/>'
    return s

variants = [
    ('0-playhead', 'Playhead, no REC', mark(CREAM, ORANGE, ORANGE)),
    ('1-rec-head', 'The playhead dot is the REC light', mark(CREAM, ORANGE, REC)),
    ('2-rec-corner', 'A REC light in the frame\'s corner', mark(CREAM, ORANGE, ORANGE, 'corner')),
]
cells = ''
for i, (_, label, body) in enumerate(variants):
    x = 40 + i * 300
    cells += f'<g transform="translate({x} 40)"><rect width="256" height="256" rx="56" fill="#1C1412"/>{body}</g>'
    cells += f'<g transform="translate({x} 340)"><rect width="256" height="256" rx="56" fill="#FFFFFF" stroke="#E5DED9"/>' + body.replace(CREAM, ESP) + '</g>'
    cells += f'<text x="{x}" y="636" font-family="system-ui" font-size="18" fill="#111">{label}</text>'
    for j, s in enumerate((48, 24)):  # true small sizes on the tile
        k = s / 256
        cells += f'<g transform="translate({x + 160 + j * 60} 660) scale({k})"><rect width="256" height="256" rx="56" fill="#1C1412"/>{body}</g>'
open('rec-variants.svg', 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 940 730"><rect width="940" height="730" fill="#F6F3F0"/>{cells}</svg>')
print('ok')
