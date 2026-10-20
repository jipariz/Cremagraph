#!/usr/bin/env bash
#
# Builds the web editor and deploys it to Vercel as the `onlyshots` project.
#
#   tools/deploy-web.sh                production, https://onlyshots.vercel.app
#   tools/deploy-web.sh --preview      a preview URL instead
#   tools/deploy-web.sh --skip-build   upload the distribution already built (CI builds it first)
#
# The build runs here, not on Vercel: the output is a Kotlin/wasm + JS bundle, and a Vercel builder has
# no JDK or Gradle (the project's Ignored Build Step is `exit 0`, so pushes don't start Vercel builds).
# What is uploaded is the static distribution.
#
# Locally it uses the CLI's login (`vercel login`). CI sets VERCEL_TOKEN, VERCEL_ORG_ID and
# VERCEL_PROJECT_ID instead, which also make `vercel link` unnecessary.

set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE="$ROOT/app/webApp/build/vercel"
TARGET="--prod"
BUILD=1
for arg in "$@"; do
    case "$arg" in
        --preview) TARGET="" ;;
        --skip-build) BUILD=0 ;;
        *) echo "error: unknown option $arg" && exit 1 ;;
    esac
done

command -v vercel >/dev/null 2>&1 || {
    echo "error: the Vercel CLI is not installed. Run: npm install --global vercel@latest"
    exit 1
}

# Both web builds in one site: wasm where the browser has WasmGC, the Kotlin/JS build elsewhere.
[ "$BUILD" = 1 ] && "$ROOT/gradlew" :app:webApp:composeCompatibilityBrowserDistribution

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -R "$ROOT/app/webApp/build/dist/composeWebCompatibility/productionExecutable/." "$STAGE/"
# Source maps are for local debugging; there is no reason to publish them.
rm -f "$STAGE"/*.map
cp "$ROOT/app/webApp/vercel.json" "$STAGE/vercel.json"
# The app-link verification files, straight from the sources (a distribution has dropped .json resources before).
mkdir -p "$STAGE/.well-known"
cp "$ROOT/app/webApp/src/webMain/resources/.well-known/"* "$STAGE/.well-known/"

cd "$STAGE"
TOKEN=()
if [ -n "${VERCEL_TOKEN:-}" ]; then
    TOKEN=(--token "$VERCEL_TOKEN")
else
    vercel link --yes --project onlyshots
fi
# shellcheck disable=SC2086
vercel deploy --yes $TARGET "${TOKEN[@]}"
