#!/usr/bin/env python3
import hashlib,json,subprocess
from pathlib import Path
from environment import TOOLS
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'patches.json').read_text())
for item in manifest['inputs']:
    with (root/'original'/item['file']).open('rb') as stream:
        assert hashlib.file_digest(stream,'sha256').hexdigest()==item['sha256'],item['file']
text=subprocess.check_output([str(TOOLS/'aapt'),'dump','badging',str(root/'original/base.apk')],text=True)
assert "versionName='455.0.0.51.72'" in text
assert "versionCode='386113969'" in text
print('PASS original APK hashes and package version')
