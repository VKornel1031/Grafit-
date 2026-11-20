#!/usr/bin/env python3
"""Sign an Android 9+ migration APK using the existing release key and public proof-of-rotation."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'app/build/outputs/apk/release/output-metadata.json').read_text())
element, = metadata['elements']
source = root / 'app/build/outputs/apk/release' / element['outputFile']
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(source)], text=True)
if "sdkVersion:'28'" not in badging or 'application-debuggable' in badging or "name='hu.grafit'" not in badging:
    raise SystemExit('Migration APK must require Android 9+ and must not be debuggable.')
output = Path(os.environ.get('GRAFIT_MIGRATION_OUTPUT', root / 'dist')).resolve()
output.mkdir(parents=True, exist_ok=True)
target = output / f"grafit-{element['versionName']}-key-migration.apk"
lineage = root / 'docs/debug-to-release.lineage'
subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', os.environ['GRAFIT_KEYSTORE_PATH'],
    '--ks-key-alias', os.environ['GRAFIT_KEY_ALIAS'], '--ks-pass', 'env:GRAFIT_STORE_PASSWORD',
    '--key-pass', 'env:GRAFIT_KEY_PASSWORD', '--lineage', str(lineage), '--rotation-min-sdk-version', '28',
    '--min-sdk-version', '28', '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'false',
    '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', str(target), str(source)], check=True)
verification = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--min-sdk-version', '28',
    '--verbose', '--print-certs', str(target)], text=True)
signer = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', verification)
expected = (root / 'docs/release-signing.sha256').read_text().strip()
if not signer or signer.group(1).lower() != expected.lower():
    raise SystemExit('Migration APK must retain the permanent production signing certificate.')
history = subprocess.check_output([str(tools / 'apksigner'), 'lineage', '--in', str(target), '--print-certs'], text=True)
old = '85d10f526e36f3ff08217e65c16b3777803acd8834f2751b9109b20f50432595'
old_block = next((part for part in re.split(r'(?=Signer #\d+ in lineage certificate DN:)', history) if old in part), '')
if expected not in history or not all(value in old_block for value in (
        'Has installed data capability: true', 'Has permission capability    : true', 'Has rollback capability      : false')):
    raise SystemExit('The APK does not contain the required original-debug-to-release history.')
config = (root / 'app/build.gradle.kts').read_text()
minified = bool(re.search(r'\bisMinifyEnabled\s*=\s*true\b', config))
resources_shrunk = bool(re.search(r'\bisShrinkResources\s*=\s*true\b', config))
model_name = 'hu.grafit.ui.GrafitViewModel'
if minified:
    mapping = (root / 'app/build/outputs/mapping/release/mapping.txt').read_text()
    match = re.search(r'^hu\.grafit\.ui\.GrafitViewModel -> (.+):$', mapping, re.M)
    if not match:
        raise SystemExit('Optimisation removed the Android ViewModel mapping.')
    model_name = match.group(1)
descriptor = "Class descriptor  : 'L" + model_name.replace('.', '/') + ";'"
constructor_found = False
with zipfile.ZipFile(target) as archive, tempfile.TemporaryDirectory() as temporary:
    for name in archive.namelist():
        if not re.fullmatch(r'classes[0-9]*\.dex', name):
            continue
        dex = Path(temporary) / name
        dex.write_bytes(archive.read(name))
        dump = subprocess.check_output([str(tools / 'dexdump'), str(dex)], text=True, errors='replace')
        start = dump.find(descriptor)
        if start >= 0:
            body = dump[start:].split('Class #', 1)[0]
            constructor_found = bool(re.search(
                r"name\s*:\s*'<init>'\s+type\s*:\s*'\(Landroid/app/Application;\)V'\s+access\s*:.*PUBLIC", body))
            break
if not constructor_found:
    raise SystemExit('The final APK lost the public Application constructor required at startup.')
subprocess.run([str(tools / 'zipalign'), '-c', '4', str(target)], check=True, stdout=subprocess.DEVNULL)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(target) as migrated:
    names = {name for name in original.namelist() if not name.startswith('META-INF/')}
    if names != {name for name in migrated.namelist() if not name.startswith('META-INF/')}:
        raise SystemExit('Signing changed application payload entries.')
    for name in names:
        if original.read(name) != migrated.read(name):
            raise SystemExit('Signing changed application data: ' + name)
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(output / 'SHA256SUMS.txt').write_text(f'{digest}  {target.name}\n')
(output / 'signing-migration.json').write_text(json.dumps({
    'version': element['versionName'], 'versionCode': element['versionCode'], 'package': 'hu.grafit',
    'minSdk': 28, 'currentSignerSha256': expected, 'previousSignerSha256': old,
    'installedDataMigration': True, 'debuggable': False, 'sha256': digest,
    'minified': minified, 'resourcesShrunk': resources_shrunk,
    'applicationPayloadUnchangedBySigning': True,
    'commit': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
}, indent=2) + '\n')
print(f'Verified Android 9+ signing-key migration APK: {target}')
