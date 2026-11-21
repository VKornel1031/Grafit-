#!/usr/bin/env python3
"""Verify the production identity and stage only public distribution files."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'app/build/outputs/apk/release/output-metadata.json').read_text())
element, = metadata['elements']
version = element['versionName']
tag = os.environ.get('GRAFIT_RELEASE_TAG', f'v{version}')
if tag != f'v{version}':
    raise SystemExit('The release tag must match the APK versionName.')
if subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain'], text=True).strip():
    raise SystemExit('Release source must be committed and clean.')
apk = root / 'app/build/outputs/apk/release' / element['outputFile']
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
build_tools = sdk / 'build-tools/35.0.0'
verification = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
signer = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', verification)
expected = (root / 'docs/release-signing.sha256').read_text().strip().lower()
if not signer or signer.group(1).lower() != expected:
    raise SystemExit('The APK is not signed with the permanent Grafit release key.')
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
if 'application-debuggable' in badging or "name='hu.grafit'" not in badging:
    raise SystemExit('The production APK must use hu.grafit and must not be debuggable.')
permissions = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'permissions', str(apk)], text=True)
requested = set(re.findall(r"uses-permission: name='([^']+)'", permissions))
# Keep this explicit: notification/background permissions are part of the app,
# while installer, exact-alarm, location, camera, and microphone access are not.
allowed = {
    'android.permission.INTERNET',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.RECEIVE_BOOT_COMPLETED',
    'android.permission.POST_PROMOTED_NOTIFICATIONS',
    'hu.grafit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}
if not requested <= allowed:
    raise SystemExit('The release requests permissions outside its minimal permission policy: ' + ', '.join(sorted(requested - allowed)))
subprocess.run([str(build_tools / 'zipalign'), '-c', '4', str(apk)], check=True, stdout=subprocess.DEVNULL)
mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
build_config = (root / 'app/build.gradle.kts').read_text()
def build_flag(name):
    match = re.search(r'\b' + name + r'\s*=\s*(true|false)\b', build_config)
    if not match:
        raise SystemExit('Explicit release build setting is missing: ' + name)
    return match.group(1) == 'true'
minified = build_flag('isMinifyEnabled')
resources_shrunk = build_flag('isShrinkResources')
model_name = 'hu.grafit.ui.GrafitViewModel'
if minified:
    if not mapping.is_file() or mapping.stat().st_size == 0:
        raise SystemExit('The minified release must have a matching R8 mapping.')
    model = re.search(r'^hu\.grafit\.ui\.GrafitViewModel -> (.+):$', mapping.read_text(), re.M)
    if not model:
        raise SystemExit('The release mapping does not contain the Android ViewModel.')
    model_name = model.group(1)
descriptor = "Class descriptor  : 'L" + model_name.replace('.', '/') + ";'"
constructor_found = False
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temporary:
    for name in archive.namelist():
        if not re.fullmatch(r'classes[0-9]*\.dex', name):
            continue
        dex = Path(temporary) / name
        dex.write_bytes(archive.read(name))
        dump = subprocess.check_output([str(build_tools / 'dexdump'), str(dex)], text=True, errors='replace')
        start = dump.find(descriptor)
        if start >= 0:
            body = dump[start:].split('Class #', 1)[0]
            constructor_found = bool(re.search(
                r"name\s*:\s*'<init>'\s+type\s*:\s*'\(Landroid/app/Application;\)V'\s+access\s*:.*PUBLIC", body))
            break
if not constructor_found:
    raise SystemExit('The APK lost the public Application constructor required at startup.')
output = Path(os.environ.get('GRAFIT_RELEASE_OUTPUT', root / 'dist')).resolve()
output.mkdir(parents=True, exist_ok=True)
target = output / f'grafit-{version}.apk'
shutil.copy2(apk, target)
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(output / 'SHA256SUMS.txt').write_text(f'{digest}  {target.name}\n')
manifest = {'version': version, 'versionCode': element['versionCode'], 'package': 'hu.grafit',
    'commit': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
    'apk': target.name, 'size': target.stat().st_size, 'sha256': digest,
    'signingCertificateSha256': expected, 'minSdk': 26, 'targetSdk': 35,
    'debuggable': False, 'minified': minified, 'resourcesShrunk': resources_shrunk, 'requestedPermissions': sorted(requested)}
(output / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'Verified signed production APK: {target} ({target.stat().st_size:,} bytes)')
