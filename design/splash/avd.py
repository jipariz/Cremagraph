"""Writes Android's system splash icon (res/drawable/splash_icon.xml): an animated vector of the mark recording its shot,
on the compressed Android timeline of SPEC.md (corners 0-280 ms, 30 ms apart; playhead 220-360; draw 320-960). The
system shows it for at most a second; the app carries on with the pulse and the hand-off."""
import math, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'logo', 'src'))
from build import frame, PLAYHEAD_X, PLATEAU_Y  # noqa: E402

W = 20
FRAME_C, SHOT_C = '#FFF4EEEA', '#FFEF4E2B'
BRACKETS = ['M' + p for p in frame(W).split('M') if p]
DIAG = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
SX, SY = 72, 168
CURVE = f'M{SX} {SY} H88 C106 {SY} 112 {PLATEAU_Y} 134 {PLATEAU_Y} H{PLAYHEAD_X}'
DURATION = 1000
FOCUS, STAGGER, HEAD, DRAW = (0, 280), 30, (220, 360), (320, 960)
# The 256 grid is 64 % of the system's 240 dp icon box, so the corners stay inside its 160 dp visible circle.
VIEW = 400
INSET = (VIEW - 256) / 2


def curve_points(n=2000):
    """The curve, densely sampled, with the arc length at each sample."""
    def cubic(p0, p1, p2, p3, t):
        u = 1 - t
        return tuple(u ** 3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3))
    pts = [(SX + (88 - SX) * i / 50, SY) for i in range(51)]
    pts += [cubic((88, SY), (106, SY), (112, PLATEAU_Y), (134, PLATEAU_Y), i / n) for i in range(1, n + 1)]
    pts += [(134 + (PLAYHEAD_X - 134) * i / 50, PLATEAU_Y) for i in range(1, 51)]
    lengths = [0.0]
    for a, b in zip(pts, pts[1:]):
        lengths.append(lengths[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    return pts, lengths


PTS, LENGTHS = curve_points()


def tip(p):
    target = LENGTHS[-1] * p
    for i in range(1, len(PTS)):
        if LENGTHS[i] >= target:
            a, b = PTS[i - 1], PTS[i]
            f = (target - LENGTHS[i - 1]) / max(1e-9, LENGTHS[i] - LENGTHS[i - 1])
            return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f
    return PTS[-1]


in_out_sine = lambda x: -(math.cos(math.pi * x) - 1) / 2


def interp(x1, y1, x2, y2):
    return (f'<aapt:attr name="android:interpolator"><pathInterpolator android:controlX1="{x1}" android:controlY1="{y1}" '
            f'android:controlX2="{x2}" android:controlY2="{y2}"/></aapt:attr>')


OUT_BACK_14 = interp(0.34, 1.4, 0.64, 1)
OUT_BACK_18 = interp(0.34, 1.8, 0.64, 1)
OUT_CUBIC = interp(0.33, 1, 0.68, 1)
IN_OUT_SINE = interp(0.37, 0, 0.63, 1)


def anim(prop, frm, to, start, end, interpolator, value_type='floatType'):
    return (f'<objectAnimator android:propertyName="{prop}" android:valueFrom="{frm}" android:valueTo="{to}" '
            f'android:valueType="{value_type}" android:startOffset="{start}" android:duration="{end - start}">'
            f'{interpolator}</objectAnimator>')


def ride(axis):
    """The tip's x or y over the draw, as keyframes: the playhead and the dot ride the curve as it is drawn."""
    frames = []
    n = 32
    for i in range(n + 1):
        f = i / n
        x, y = tip(in_out_sine(f))
        frames.append(f'<keyframe android:fraction="{f:.4f}" android:value="{(x if axis == 0 else y):.3f}"/>')
    name = 'translateX' if axis == 0 else 'translateY'
    return (f'<objectAnimator android:startOffset="{DRAW[0]}" android:duration="{DRAW[1] - DRAW[0]}">'
            f'<propertyValuesHolder android:propertyName="{name}" android:valueType="floatType">{"".join(frames)}'
            f'</propertyValuesHolder></objectAnimator>')


def target(name, animators):
    return (f'<target android:name="{name}"><aapt:attr name="android:animation"><set android:ordering="together">'
            f'{"".join(animators)}</set></aapt:attr></target>')


groups, targets = [], []
for i, d in enumerate(BRACKETS):
    dx, dy = DIAG[i]
    a, b = FOCUS[0] + i * STAGGER, FOCUS[1] + i * STAGGER
    groups.append(f'<group android:name="corner{i}" android:translateX="{dx * 26}" android:translateY="{dy * 26}">'
                  f'<path android:name="cornerPath{i}" android:fillColor="{FRAME_C}" android:fillAlpha="0" android:pathData="{d}"/></group>')
    targets.append(target(f'corner{i}', [anim('translateX', dx * 26, 0, a, b, OUT_BACK_14),
                                         anim('translateY', dy * 26, 0, a, b, OUT_BACK_14)]))
    targets.append(target(f'cornerPath{i}', [anim('fillAlpha', 0, 1, a, a + 140, OUT_CUBIC)]))
# The playhead grows from its middle at the start of the curve, then rides the tip.
groups.append(f'<group android:name="head" android:translateX="{SX}" android:translateY="130">'
              f'<path android:name="headLine" android:strokeColor="{FRAME_C}" android:strokeWidth="14" '
              f'android:strokeLineCap="round" android:strokeAlpha="0" android:pathData="M0,0 L0,0"/></group>')
targets.append(target('headLine', [anim('pathData', 'M0,0 L0,0', 'M0,-52 L0,52', HEAD[0], HEAD[1], OUT_CUBIC, 'pathType'),
                                   anim('strokeAlpha', 0, 1, HEAD[0], HEAD[0] + 1, '')]))
targets.append(target('head', [ride(0)]))
# The shot, drawn behind the playhead.
groups.append(f'<path android:name="curve" android:strokeColor="{SHOT_C}" android:strokeWidth="{W}" '
              f'android:strokeLineCap="round" android:strokeLineJoin="round" android:trimPathEnd="0" android:pathData="{CURVE}"/>')
targets.append(target('curve', [anim('trimPathEnd', 0, 1, DRAW[0], DRAW[1], IN_OUT_SINE)]))
# The dot rides the tip, and grows in as the draw starts.
groups.append(f'<group android:name="dotAt" android:translateX="{SX}" android:translateY="{SY}">'
              f'<group android:name="dot" android:scaleX="0" android:scaleY="0">'
              f'<path android:fillColor="{SHOT_C}" android:pathData="M-18,0 A18,18 0 1,0 18,0 A18,18 0 1,0 -18,0 Z"/>'
              f'</group></group>')
targets.append(target('dotAt', [ride(0), ride(1)]))
targets.append(target('dot', [anim('scaleX', 0, 1, DRAW[0], DRAW[0] + 160, OUT_BACK_18),
                              anim('scaleY', 0, 1, DRAW[0], DRAW[0] + 160, OUT_BACK_18)]))

xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/splash/avd.py: edit that, not this. The mark recording its shot for the system splash
     (design/splash/SPEC.md, Android profile, {DURATION} ms). -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector android:width="240dp" android:height="240dp" android:viewportWidth="{VIEW}" android:viewportHeight="{VIEW}">
            <group android:translateX="{INSET:g}" android:translateY="{INSET:g}">
                {(chr(10) + "                ").join(groups)}
            </group>
        </vector>
    </aapt:attr>
    {(chr(10) + "    ").join(targets)}
</animated-vector>
'''
res = os.path.join(HERE, '..', '..', 'app', 'androidApp', 'src', 'main', 'res', 'drawable')
open(os.path.join(res, 'splash_icon.xml'), 'w').write(xml)

# Android 11 and lower: the backport shows its icon still, so it gets the finished mark, as the animation ends.
tx, ty = tip(1)
still = [f'<path android:fillColor="{FRAME_C}" android:pathData="{d}"/>' for d in BRACKETS]
still.append(f'<path android:strokeColor="{FRAME_C}" android:strokeWidth="14" android:strokeLineCap="round" '
             f'android:pathData="M{tx:.2f},78 L{tx:.2f},182"/>')
still.append(f'<path android:strokeColor="{SHOT_C}" android:strokeWidth="{W}" android:strokeLineCap="round" '
             f'android:strokeLineJoin="round" android:pathData="{CURVE}"/>')
still.append(f'<path android:fillColor="{SHOT_C}" android:pathData="M{tx - 18:.2f},{ty:.2f} A18,18 0 1,0 {tx + 18:.2f},{ty:.2f} '
             f'A18,18 0 1,0 {tx - 18:.2f},{ty:.2f} Z"/>')
static = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/splash/avd.py: edit that, not this. The finished mark, for the splash backport (Android 11 and
     lower), which does not animate its icon. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="240dp" android:height="240dp"
    android:viewportWidth="{VIEW}" android:viewportHeight="{VIEW}">
    <group android:translateX="{INSET:g}" android:translateY="{INSET:g}">
        {(chr(10) + "        ").join(still)}
    </group>
</vector>
'''
open(os.path.join(res, 'splash_icon_still.xml'), 'w').write(static)
print('wrote splash_icon.xml, splash_icon_still.xml')
