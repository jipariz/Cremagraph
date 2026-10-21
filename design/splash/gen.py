"""Writes splash.html, the OnlyShots splash animation prototype, from the logo kit's own geometry."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'logo', 'src'))
from build import frame, PLAYHEAD_X, PLATEAU_Y  # noqa: E402

W = 20
BRACKETS = frame(W)
# The frame path holds the four brackets one after another; split them so each can move on its own diagonal.
parts = ['M' + p for p in BRACKETS.split('M') if p]
START_X = 40 + W + 12
CURVE = f'M{START_X} 168 H88 C106 168 112 {PLATEAU_Y} 134 {PLATEAU_Y} H{PLAYHEAD_X}'

html = open(os.path.join(HERE, 'template.html')).read()
html = html.replace('%BRACKETS%', '\n'.join(
    f'<path class="bracket" data-i="{i}" fill="var(--frame)" d="{d}"/>' for i, d in enumerate(parts)))
html = html.replace('%CURVE%', CURVE).replace('%PX%', str(PLAYHEAD_X)).replace('%PY%', str(PLATEAU_Y))
html = html.replace('%SX%', str(START_X))
open(os.path.join(HERE, 'splash.html'), 'w').write(html)
print('wrote splash.html')
