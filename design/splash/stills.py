"""Key frames of the splash (app timeline) as SVG stills, plus filmstrip.svg; PNGs are rendered with headless Chrome.

    python3 design/splash/stills.py
"""
import os
import subprocess

from motion import APP, frame_svg

HERE = os.path.dirname(os.path.abspath(__file__))
FRAMES = os.path.join(HERE, 'frames')
TIMES = [120, 300, 520, 760, 1000, 1100, 1200, 1300]
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'


def main():
    os.makedirs(FRAMES, exist_ok=True)
    for old in os.listdir(FRAMES):
        os.remove(os.path.join(FRAMES, old))
    cells = []
    for i, t in enumerate(TIMES):
        svg = frame_svg(t, APP)
        open(os.path.join(FRAMES, f'still-{t:04d}.svg'), 'w').write(svg + '\n')
        inner = svg.replace('<svg xmlns="http://www.w3.org/2000/svg" viewBox="-40 -40 336 336">', '').replace('</svg>', '')
        x = i * 360
        cells.append(f'<svg x="{x}" y="0" width="336" height="336" viewBox="-40 -40 336 336">{inner}</svg>'
                     f'<text x="{x + 168}" y="372" fill="#A3A19B" font-family="IBM Plex Mono, Menlo, monospace" '
                     f'font-size="20" text-anchor="middle">{t} ms</text>')
    w = len(TIMES) * 360 - 24
    strip = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} 396" width="{w}" height="396">'
             f'<rect width="{w}" height="396" fill="#161616"/>{"".join(cells)}</svg>\n')
    open(os.path.join(HERE, 'filmstrip.svg'), 'w').write(strip)
    if os.path.exists(CHROME):
        page = os.path.join(HERE, 'filmstrip.svg')
        subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', f'--window-size={w},396',
                        '--force-device-scale-factor=1', f'--screenshot={os.path.join(HERE, "filmstrip.png")}',
                        'file://' + page], check=True, capture_output=True)
    print('wrote', len(TIMES), 'stills and filmstrip')


if __name__ == '__main__':
    main()
