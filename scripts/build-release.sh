#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
for setting in GRAFIT_KEYSTORE_PATH GRAFIT_STORE_PASSWORD GRAFIT_KEY_ALIAS GRAFIT_KEY_PASSWORD; do
    if [[ -z "${!setting:-}" ]]; then
        echo "Missing release signing setting: $setting" >&2
        exit 1
    fi
done
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
    echo "Commit the source changes before packaging a production release." >&2
    exit 1
fi
./gradlew :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease :app:assembleDebugAndroidTest
python3 scripts/package-release.py
