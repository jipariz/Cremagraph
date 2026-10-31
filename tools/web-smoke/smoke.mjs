// Smoke test of the built web editor in headless Chromium and WebKit: the site the deploy ships
// (`:app:webApp:composeCompatibilityBrowserDistribution`), served as is, once on the wasm build and once on the
// Kotlin/JS fallback that browsers without WasmGC get.
//
//   ./gradlew :app:webApp:composeCompatibilityBrowserDistribution
//   cd tools/web-smoke && npm ci && npx playwright install chromium webkit && node smoke.mjs
//
// Each run checks that the page's splash hands over (the app calls onlyshotsReady, which removes #splash), that the
// editor paints its canvas (more than the background colour), and that nothing is logged as an error. The flows
// themselves are the editor's UI tests (`:uitests:desktopTest`); Compose on the web draws to a canvas, so this checks
// what only the browser can break: loading, the wasm/JS pick, Skia, the page's own script.
//
// WebKit is here because Safari is the one engine nobody runs by accident, and every Safari-only break has reached
// users first. It is Playwright's own build rather than Safari itself, so it catches engine faults and not whatever
// a real iPhone does with memory or a GPU.
//
// Playwright's WebKit reports Version/27.2, which index.html's gate turns away (that build cannot draw Skiko's
// canvas on a real Safari), so the two WebKit runs load through ?webkit272=ok to get at the editor behind it. A last
// run checks the gate itself: an iPhone on Safari, an iPhone on Chrome and a Mac, all on 27.2, land on
// /unsupported/ before any of the app is fetched, and the way past works and sticks.
import { chromium, webkit } from "playwright";
import { createServer } from "node:http";
import { readFile, stat, writeFile } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = fileURLToPath(new URL(".", import.meta.url));
const site = resolve(
    process.argv[2] ?? join(here, "../../app/webApp/build/dist/composeWebCompatibility/productionExecutable"),
);
const TYPES = {
    ".html": "text/html",
    ".js": "application/javascript",
    ".mjs": "application/javascript",
    ".wasm": "application/wasm",
    ".json": "application/json",
    ".webmanifest": "application/manifest+json",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".ico": "image/x-icon",
    ".ttf": "font/ttf",
    ".xml": "application/xml",
};

// Errors that are known and not the app's: Kotlin/wasm's notice that a dependency (Compose's) still reads memory
// through `wasmExports`; the app's own code uses `wasmMemory` (WebMemory.wasmJs.kt). Drop it once Compose updates.
// The second is Skiko asking WebGL which GPU it is on: Chrome answers, Safari does not expose the extension and logs
// the refusal. It says nothing about whether Skia drew, which the colour count below actually checks.
const KNOWN = [
    /^Accessing `memory` via `wasmExports` is deprecated/,
    /WEBGL_debug_renderer_info not enabled/,
];

// The editor's first screen, the splash's hand-over and the first frames: well within this on a CI machine.
const READY_MS = 60_000;

function serve(root) {
    const server = createServer(async (req, res) => {
        const path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname)).replace(/^(\.\.[/\\])+/, "");
        let file = join(root, path);
        try {
            if ((await stat(file)).isDirectory()) file = join(file, "index.html");
            const body = await readFile(file);
            res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" });
            res.end(body);
        } catch {
            res.writeHead(404);
            res.end();
        }
    });
    return new Promise((done) => server.listen(0, "127.0.0.1", () => done(server)));
}

async function run(browser, url, build, label) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
    const errors = [];
    page.on("console", (m) => {
        if (m.type() === "error" && !KNOWN.some((k) => k.test(m.text()))) errors.push(m.text());
    });
    page.on("pageerror", (e) => errors.push(String(e)));
    page.on("requestfailed", (r) => errors.push(`${r.url()}: ${r.failure()?.errorText}`));
    page.on("response", (r) => r.status() >= 400 && errors.push(`${r.url()}: HTTP ${r.status()}`));
    const loaded = [];
    page.on("request", (r) => loaded.push(new URL(r.url()).pathname));
    if (build === "js") {
        // What a browser without WasmGC answers to the loader's probe; Skia's own wasm still loads, as there.
        await page.addInitScript(() => {
            const validate = WebAssembly.validate.bind(WebAssembly);
            let first = true;
            WebAssembly.validate = (bytes) => (first ? ((first = false), false) : validate(bytes));
        });
    }
    await page.goto(url);
    await page.waitForFunction(() => !document.getElementById("splash"), null, { timeout: READY_MS });
    const want = build === "js" ? "/originJsOnlyshots.js" : "/originWasmOnlyshots.js";
    if (!loaded.includes(want)) throw new Error(`${label}: ${want} not loaded (loaded: ${loaded.join(", ")})`);
    // The first frame comes before the strings and fonts have loaded: wait until two frames in a row are the same.
    let shot = await page.screenshot();
    for (let waited = 0; waited < READY_MS; waited += 1000) {
        await page.waitForTimeout(1000);
        const next = await page.screenshot();
        if (next.equals(shot)) break;
        shot = next;
    }
    // Set SHOTS to a folder to keep what each build drew.
    if (process.env.SHOTS) await writeFile(join(process.env.SHOTS, `web-${label.replace("/", "-")}.png`), shot);
    // A canvas that is only the background is an editor that failed to draw; the Open screen has well over 100.
    const colours = await page.evaluate(async (png) => {
        const img = new Image();
        img.src = `data:image/png;base64,${png}`;
        await img.decode();
        const c = new OffscreenCanvas(img.width, img.height).getContext("2d");
        c.drawImage(img, 0, 0);
        const d = c.getImageData(0, 0, img.width, img.height).data;
        const seen = new Set();
        for (let i = 0; i < d.length; i += 4 * 97) seen.add((d[i] << 16) | (d[i + 1] << 8) | d[i + 2]);
        return seen.size;
    }, shot.toString("base64"));
    if (colours < 100) throw new Error(`${label}: the page painted ${colours} colours; the editor did not draw`);
    if (errors.length) throw new Error(`${label}: errors on the page:\n  ${errors.join("\n  ")}`);
    console.log(`${label}: ready, ${colours} colours, no errors`);
    await page.close();
}

// index.html turns a WebKit 27.2 visitor away before any of the app is fetched, with ?webkit272=ok to get in
// anyway. The runs above prove the gate stays out of Chromium's way and that the editor still works behind it; this
// proves the gate fires, on a phone and on a Mac, and that the way past works and is remembered. The engine does not
// matter here, the gate is plain script, so this runs on Chromium.
const WEBKIT_27_2 = {
    iphone: {
        userAgent:
            "Mozilla/5.0 (iPhone; CPU iPhone OS 27_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/27.2 Mobile/15E148 Safari/604.1",
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        isMobile: true,
    },
    // Chrome on iOS is WKWebView like every other browser there, and breaks the same way on a real device. Its UA
    // keeps the platform token and swaps Safari's Version/ for CriOS/, so only the first branch of the gate sees it.
    chromeIphone: {
        userAgent:
            "Mozilla/5.0 (iPhone; CPU iPhone OS 27_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "CriOS/156.0.8078.4 Mobile/15E148 Safari/604.1",
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        isMobile: true,
    },
    mac: {
        userAgent:
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/27.2 Safari/605.1.15",
        viewport: { width: 1280, height: 800 },
    },
};

async function gate(browser, url) {
    for (const [what, device] of Object.entries(WEBKIT_27_2)) {
        const turned = await browser.newPage(device);
        const loaded = [];
        turned.on("request", (r) => loaded.push(new URL(r.url()).pathname));
        await turned.goto(url);
        await turned.waitForURL(/\/unsupported\/$/, { timeout: READY_MS });
        const app = loaded.filter((n) => /Onlyshots\.js$|\.wasm$/.test(n));
        if (app.length) throw new Error(`gate: ${what} fetched the app before the notice: ${app.join(", ")}`);
        await turned.close();
    }

    const past = await browser.newPage(WEBKIT_27_2.iphone);
    await past.goto(`${url}?webkit272=ok`);
    await past.waitForFunction(() => !document.getElementById("splash"), null, { timeout: READY_MS });
    // The override is remembered, so a refresh stays in the editor instead of bouncing back to the notice.
    await past.goto(url);
    await past.waitForFunction(() => !document.getElementById("splash"), null, { timeout: READY_MS });
    await past.close();
    const where = Object.keys(WEBKIT_27_2).join(", ");
    console.log(`gate: WebKit 27.2 turned away (${where}), ?webkit272=ok gets in and sticks`);
}

const server = await serve(site);
const url = `http://127.0.0.1:${server.address().port}/`;
let failed = false;
for (const [engine, type] of [
    ["chromium", chromium],
    ["webkit", webkit],
]) {
    const browser = await type.launch();
    // Playwright's WebKit is caught by the gate (it reports Version/27.2): let it past to reach the editor.
    const at = engine === "webkit" ? `${url}?webkit272=ok` : url;
    for (const build of ["wasm", "js"]) {
        try {
            await run(browser, at, build, `${engine}/${build}`);
        } catch (e) {
            failed = true;
            console.error(String(e.message ?? e));
        }
    }
    await browser.close();
}
try {
    const browser = await chromium.launch();
    await gate(browser, url);
    await browser.close();
} catch (e) {
    failed = true;
    console.error(String(e.message ?? e));
}
server.close();
process.exit(failed ? 1 : 0);
