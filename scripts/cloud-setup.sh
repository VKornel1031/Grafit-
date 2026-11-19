#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p /workspace/toolchains
cd /workspace/toolchains
if [[ ! -x jdk-21/bin/javac ]]; then
    url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz'
    curl -fL "$url" -o jdk.tar.gz
    curl -fL "$url.sha256.txt" -o jdk.sha256
    python3 - <<'PY'
import hashlib
assert hashlib.file_digest(open('jdk.tar.gz', 'rb'), 'sha256').hexdigest() == open('jdk.sha256').read().split()[0]
PY
    mkdir -p jdk-21
    tar -xf jdk.tar.gz -C jdk-21 --strip-components=1
fi
if [[ ! -x android/cmdline-tools/latest/bin/sdkmanager ]]; then
    curl -fL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o android-cli.zip
    mkdir -p android/cmdline-tools
    unzip -q android-cli.zip -d android/cmdline-tools
    mv android/cmdline-tools/cmdline-tools android/cmdline-tools/latest
fi
# Retain Java's standard trust anchors and add the managed machine's proxy CAs.
cp jdk-21/lib/security/cacerts cacerts
for cert in /etc/ssl/certs/mitmproxy-ca-cert.pem /etc/ssl/certs/nebula-mitmproxy.pem /etc/ssl/certs/nebula-dns.pem; do
    if [[ -f "$cert" ]]; then
        jdk-21/bin/keytool -importcert -noprompt -keystore cacerts -storepass changeit -alias "grafit-$(basename "$cert")" -file "$cert"
    fi
done
cd /workspace/Grafit-
source scripts/cloud-env.sh
mkdir -p "$ANDROID_USER_HOME"
sdk_proxy=()
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    mapfile -t grafit_proxy < <(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
print(p.hostname)
print(p.port)
PY
)
    sdk_proxy=(--proxy=http "--proxy_host=${grafit_proxy[0]}" "--proxy_port=${grafit_proxy[1]}")
fi
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "${sdk_proxy[@]}" --licenses < <(python3 -c 'print("y\n" * 100)')
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "${sdk_proxy[@]}" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
