#!/usr/bin/env bash
# Source this in managed cloud tasks. Android Studio users can use their own SDK/JDK.
export JAVA_HOME=/workspace/toolchains/jdk-21
export ANDROID_HOME=/workspace/toolchains/android
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    grafit_proxy_options=$(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
if p.hostname and p.port:
    print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port}')
PY
)
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $grafit_proxy_options"
fi
if [[ -f /workspace/toolchains/cacerts ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/workspace/toolchains/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
fi
