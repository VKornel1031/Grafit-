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
subprocess.run([str(build_tools / 'zipalign'), '-c', '4', str(apk)], check=True, stdout=subprocess.DEVNULL)
mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
if not mapping.is_file() or mapping.stat().st_size == 0:
    raise SystemExit('The release must be built with R8 optimisation.')
model = re.search(r'^hu\.grafit\.ui\.GrafitViewModel -> (.+):$', mapping.read_text(), re.M)
if not model:
    raise SystemExit('The release mapping does not contain the Android ViewModel.')
descriptor = "Class descriptor  : 'L" + model.group(1).replace('.', '/') + ";'"
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
    raise SystemExit('The minified APK lost the public Application constructor required at startup.')
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
    'debuggable': False, 'minified': True, 'resourcesShrunk': True}
(output / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'Verified signed production APK: {target} ({target.stat().st_size:,} bytes)')
