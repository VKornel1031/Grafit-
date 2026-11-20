#!/usr/bin/env bash
# One-time Android 9+ bridge from this workspace's original debug key to the permanent release key.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
for setting in GRAFIT_KEYSTORE_PATH GRAFIT_STORE_PASSWORD GRAFIT_KEY_ALIAS GRAFIT_KEY_PASSWORD ANDROID_HOME; do
    if [[ -z "${!setting:-}" ]]; then
        echo "Missing migration build setting: $setting" >&2
        exit 1
    fi
done
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
    echo "Commit the source before building a signing migration." >&2
    exit 1
fi
./gradlew -PgrafitSigningMigration=true :app:assembleRelease :app:testDebugUnitTest :app:lintRelease :app:assembleDebugAndroidTest
python3 scripts/package-signing-migration.py
