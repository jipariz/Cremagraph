"""The finished mark on a clear ground, for iOS's launch screen (LaunchMark): what the splash shows when its shot is drawn."""
import os, re, subprocess, sys, tempfile
HERE = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]]
# stills.py writes its frames on import; only still() is wanted here.
src = open(os.path.join(HERE, 'stills.py')).read().split("os.makedirs(os.path.join(HERE, 'frames')")[0]
ns = {'__file__': os.path.join(HERE, 'stills.py')}
exec(compile(src, 'stills.py', 'exec'), ns)
svg = ns['still'](1100)
svg = re.sub(r'<rect [^>]*/>', '', svg).replace('viewBox="-40 -40 336 336"', 'viewBox="0 0 256 256" width="100%" height="100%"')
OUT = os.path.join(HERE, '..', '..', 'app', 'iosApp', 'iosApp', 'Assets.xcassets', 'LaunchMark.imageset')
os.makedirs(OUT, exist_ok=True)
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
with tempfile.TemporaryDirectory() as tmp:
    page = os.path.join(tmp, 'mark.html')
    style = 'html,body{margin:0;width:100vw;height:100vh;background:transparent;overflow:hidden}svg{display:block;width:100vw;height:100vh}'
    open(page, 'w').write(f'<html><head><style>{style}</style></head><body>{svg}</body></html>')
    # Rendered large once (headless Chrome mis-sizes small windows), then scaled down to 154 pt, the size the app's
    # splash draws it (Android's 240 dp icon box at 64 %).
    big = os.path.join(tmp, 'big.png')
    subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', '--window-size=1024,1024',
                    '--force-device-scale-factor=1', '--default-background-color=00000000', f'--screenshot={big}',
                    'file://' + page], check=True, capture_output=True)
    for scale in (1, 2, 3):
        px = str(154 * scale)
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
