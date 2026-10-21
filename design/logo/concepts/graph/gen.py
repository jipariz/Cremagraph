"""Graph-element concepts inside the existing viewfinder frame (frame geometry from design/logo/src/build.py)."""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src'))
from build import frame  # noqa: E402

W = 20  # the frame's stroke, shared by the graph
F = frame(W)

def svg(name, note, body):
    open(name, 'w').write(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
  <!-- {note} -->
  <path fill="#000" d="{F}"/>
{body}</svg>
''')

line = f'fill="none" stroke="#000" stroke-width="{W}" stroke-linecap="round" stroke-linejoin="round"'

# A · REC trace: the dot leaves the curve and becomes the REC light; the profile is angular, so it reads as data.
svg('a-rec-trace-v1.svg', 'A · REC trace: an angular shot profile; the dot is the camera\'s REC light.',
    f'  <circle cx="82" cy="82" r="13" fill="#000"/>\n'
    f'  <path {line} d="M68 170 H96 L122 112 H150 L188 128"/>\n')

# B · Playhead: the curve is drawn up to a playhead; the dot rides the playhead where the curve meets it.
svg('b-playhead-v1.svg', 'B · Playhead: the shot drawn so far, up to the playhead; the dot is where it is being written.',
    f'  <path {line} d="M66 172 H90 C108 172 112 112 134 112 H164"/>\n'
    f'  <path fill="none" stroke="#000" stroke-width="12" stroke-linecap="round" d="M176 72 V184"/>\n'
    f'  <circle cx="176" cy="112" r="17" fill="#000"/>\n')

# C · Area: the shot as a filled area rising in the frame, like crema in a cup; no dot to become a head.
svg('c-area-v1.svg', 'C · Area: the shot profile as a filled area inside the frame.',
    '  <path fill="#000" d="M64 186 V170 Q64 162 72 162 H88 C106 162 112 108 134 108 C156 108 174 116 192 120 V186 Q192 192 186 192 H70 Q64 192 64 186 Z"/>\n')
print('ok')

# v2 ------------------------------------------------------------------------------------------------------------
# A · Write head: the dot rides the line's own end, level with the plateau, like a pen still drawing.
svg('a-write-head-v2.svg', 'A · Write head: the shot profile with the dot on its end, where the pen is now.',
    f'  <path {line} d="M64 168 H90 L118 108 H170"/>\n'
    f'  <circle cx="182" cy="108" r="18" fill="#000"/>\n')

# B · Playhead, tuned: the curve stops at a playhead that spans the frame; the dot rides the playhead.
svg('b-playhead-v2.svg', 'B · Playhead: the shot drawn so far, up to the playhead; the dot is where it is being written.',
    f'  <path {line} d="M64 170 H86 C104 170 110 110 132 110 H172"/>\n'
    f'  <path fill="none" stroke="#000" stroke-width="14" stroke-linecap="round" d="M178 66 V190"/>\n'
    f'  <circle cx="178" cy="110" r="18" fill="#000"/>\n')

# C · Through-line: the trace enters by the frame's left gap and leaves by the right one; the camera records a
# line that keeps going.
svg('c-through-line-v2.svg', 'C · Through-line: the shot trace runs through the viewfinder, in by one gap and out by the other.',
    f'  <path {line} d="M14 150 H78 L108 106 H158 L242 120"/>\n')

# v3 ------------------------------------------------------------------------------------------------------------
svg('b-playhead-v3.svg', 'B · Playhead: the shot drawn so far, up to the playhead; the dot is where it is being written.',
    f'  <path {line} d="M64 168 H86 C104 168 110 110 132 110 H170"/>\n'
    f'  <path fill="none" stroke="#000" stroke-width="14" stroke-linecap="round" d="M178 78 V182"/>\n'
    f'  <circle cx="178" cy="110" r="18" fill="#000"/>\n')

svg('c-through-line-v3.svg', 'C · Through-line: the shot trace runs through the viewfinder, in by one gap and out by the other.',
    f'  <path {line} d="M12 136 H80 L106 100 H156 L244 116"/>\n')

# Lockups with the kit's outlined wordmark.
import re
master = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'kit', 'onlyshots-horizontal-master.svg')).read()
word = re.findall(r'<path fill="#000000" transform="translate\([^)]*\)" d="[^"]*"/>', master)[0].replace('#000000', '#000')
vb = re.search(r'viewBox="([^"]*)"', master).group(1)
for sym in ['a-write-head-v2', 'b-playhead-v3', 'c-through-line-v3']:
    body = open(sym + '.svg').read().split('-->\n', 1)[1].replace('</svg>\n', '')
    open(sym.rsplit('-v', 1)[0] + '-lockup.svg', 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">\n{body}  {word}\n</svg>\n')
