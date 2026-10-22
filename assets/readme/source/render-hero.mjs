// Renders hero-layout.svg to ../hero.gif (the graph plots itself, then holds) and ../hero.png (the settled frame).
// Run from tools/web-smoke after `npm ci`, so Playwright resolves from there:
//   cd tools/web-smoke && npm ci && node ../../assets/readme/source/render-hero.mjs
// Needs ffmpeg. Motion follows beautify-github-readme's defaults: 30 fps, calm reveal, a still hold, and a last frame
// equal to the first so the loop has no jump.
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const { chromium } = createRequire(join(process.cwd(), "/"))("playwright");

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../../..");
const out = resolve(here, "..");
const fonts = join(root, "core/resources/src/commonMain/composeResources/font");
const face = (family, file, weight) =>
  `@font-face{font-family:'${family}';font-weight:${weight};src:url('${pathToFileURL(join(fonts, file))}')}`;

const FPS = 30;
const EMPTY = 0.5; // s, nothing plotted (matches the loop's last frame)
const PLOT = 3.4; // s, the shot's 28 s plotted
const HOLD = 2.4; // s, settled
const BACK = 0.5; // s, wound back to empty
const SHOT = 28; // s of shot on the timer

const ease = (t) => 1 - Math.pow(1 - t, 2.2); // gentle ease-out
const easeIn = (t) => t * t;
function progressAt(s) {
  if (s < EMPTY) return 0;
  if (s < EMPTY + PLOT) return ease((s - EMPTY) / PLOT);
  if (s < EMPTY + PLOT + HOLD) return 1;
  // Empty again by the last frame, so it matches the first.
  return 1 - easeIn(Math.min(1, (s - EMPTY - PLOT - HOLD) / (BACK - 1 / FPS)));
}

const svg = readFileSync(join(here, "hero-layout.svg"), "utf8").replace(
  'href="hero-photo.jpg"',
  `href="${pathToFileURL(join(here, "hero-photo.jpg"))}"`,
);
const html = `<!doctype html><meta charset="utf-8"><style>
${face("IBM Plex Mono", "plex_mono_regular.ttf", 400)}${face("IBM Plex Mono", "plex_mono_semibold.ttf", 600)}
${face("IBM Plex Sans", "plex_sans_regular.ttf", 400)}${face("IBM Plex Sans", "plex_sans_semibold.ttf", 600)}
html,body{margin:0;background:#0B0B0B}svg{display:block}</style>${svg}`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 500 }, deviceScaleFactor: 1 });
// A file:// page, so the SVG may load the local photo and fonts.
const frames = mkdtempSync(join(tmpdir(), "hero-frames-"));
writeFileSync(join(frames, "hero.html"), html);
await page.goto(pathToFileURL(join(frames, "hero.html")).href, { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);
// Readings follow the drawn curves: each value is read off its trace at the plotted x.
await page.evaluate(() => {
  const yAt = (path, x) => {
    const len = path.getTotalLength();
    let lo = 0, hi = len;
    for (let i = 0; i < 30; i++) {
      const mid = (lo + hi) / 2;
      if (path.getPointAtLength(mid).x < x) lo = mid; else hi = mid;
    }
    return path.getPointAtLength(lo).y;
  };
  const $ = (id) => document.getElementById(id);
  const clock = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
  window.setShot = (p, seconds) => {
    const x = 300 * p;
    $("trace-clip").setAttribute("width", Math.max(0, x + (p > 0 ? 2 : 0)));
    $("timer").textContent = clock(seconds);
    $("rec-time").textContent = `REC ${clock(seconds + 3)}`;
    const v = (id, y0, y1, max) => Math.max(0, ((y0 - yAt($(id), x)) / (y0 - y1)) * max);
    $("r-bar").textContent = (p === 0 ? 0 : v("t-pressure", 118, 24, 9)).toFixed(1);
    $("r-flow").textContent = (p === 0 ? 0 : v("t-flow", 118, 54, 2.1)).toFixed(1);
    $("r-weight").textContent = (p === 0 ? 0 : v("t-weight", 118, 14, 36)).toFixed(1);
    $("r-temp").textContent = (92.4 + 0.7 * p).toFixed(1);
  };
});

const total = Math.round((EMPTY + PLOT + HOLD + BACK) * FPS);
for (let i = 0; i < total; i++) {
  const p = progressAt(i / FPS);
  await page.evaluate(([p, s]) => window.setShot(p, s), [p, Math.round(p * SHOT)]);
  await page.screenshot({ path: join(frames, `f${String(i).padStart(4, "0")}.png`) });
}
await page.evaluate(() => window.setShot(1, 28));
await page.screenshot({ path: join(out, "hero.png") });
await browser.close();

const input = ["-y", "-loglevel", "error", "-framerate", String(FPS), "-i", join(frames, "f%04d.png")];
const palette = join(frames, "palette.png");
execFileSync("ffmpeg", [...input, "-vf", "palettegen=max_colors=256:stats_mode=full", palette]);
execFileSync("ffmpeg", [
  ...input, "-i", palette, "-lavfi", "paletteuse=dither=none:diff_mode=rectangle", "-loop", "0", join(out, "hero.gif"),
]);
rmSync(frames, { recursive: true, force: true });
for (const f of ["hero.gif", "hero.png"]) console.log(f, (statSync(join(out, f)).size / 1e6).toFixed(2), "MB");
