#!/usr/bin/env bash
# Run locally with GitHub CLI after extracting the private signing-key backup.
set -euo pipefail
if [[ $# -ne 1 ]]; then
    echo "Usage: bash scripts/configure-release-secrets.sh /path/to/extracted-key-backup" >&2
    exit 1
fi
key_directory=$(cd "$1" && pwd)
test -f "$key_directory/grafit-release.p12"
test -s "$key_directory/keystore-password.txt"
python3 - "$key_directory/grafit-release.p12" <<'PY' | gh secret set GRAFIT_KEYSTORE_BASE64 --repo VKornel1031/Grafit-
import base64, pathlib, sys
sys.stdout.write(base64.b64encode(pathlib.Path(sys.argv[1]).read_bytes()).decode())
PY
for secret_name in GRAFIT_STORE_PASSWORD GRAFIT_KEY_PASSWORD; do
    python3 - "$key_directory/keystore-password.txt" <<'PY' | gh secret set "$secret_name" --repo VKornel1031/Grafit-
import pathlib, sys
sys.stdout.write(pathlib.Path(sys.argv[1]).read_text().rstrip('\r\n'))
PY
done
printf '%s' grafit-release | gh secret set GRAFIT_KEY_ALIAS --repo VKornel1031/Grafit-
echo "Release signing secrets configured. Keep the key backup private."
