"""The finished mark on a clear ground, for iOS's launch screen (LaunchMark): what the splash shows when its trace is
plotted. LaunchBackground stays ink (#0B0B0B).

    python3 design/splash/launch.py   # writes into app/iosApp/iosApp/Assets.xcassets/LaunchMark.imageset
"""
import os
import subprocess
import tempfile

import motion as M

# The mark at the same size and place as Android's icon view: the 256 grid at 146 pt (256 / 420 of the
# 240 dp icon box, avd.py's viewport), the tile inside it.
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="100%" height="100%">'
       f'<path fill="{M.SIGNAL}" d="{M.build.tile_path("regular")}"/></svg>')
OUT = os.path.join(M.HERE, '..', '..', 'app', 'iosApp', 'iosApp', 'Assets.xcassets', 'LaunchMark.imageset')
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, 'mark.html')
        style = 'html,body{margin:0;width:100vw;height:100vh;background:transparent;overflow:hidden}svg{display:block;width:100vw;height:100vh}'
        open(page, 'w').write(f'<html><head><style>{style}</style></head><body>{svg}</body></html>')
        # Rendered large once (headless Chrome mis-sizes small windows), then scaled down to 146 pt.
        big = os.path.join(tmp, 'big.png')
        subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', '--window-size=1024,1024',
                        '--force-device-scale-factor=1', '--default-background-color=00000000', f'--screenshot={big}',
                        'file://' + page], check=True, capture_output=True)
        for scale in (1, 2, 3):
            px = str(146 * scale)
            subprocess.run(['sips', '-z', px, px, big, '--out', os.path.join(OUT, f'launch-mark@{scale}x.png')],
                           check=True, capture_output=True)
    open(os.path.join(OUT, 'Contents.json'), 'w').write('''{
  "images" : [
    { "filename" : "launch-mark@1x.png", "idiom" : "universal", "scale" : "1x" },
    { "filename" : "launch-mark@2x.png", "idiom" : "universal", "scale" : "2x" },
    { "filename" : "launch-mark@3x.png", "idiom" : "universal", "scale" : "3x" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
''')
    print('wrote', OUT)


if __name__ == '__main__':
    main()
