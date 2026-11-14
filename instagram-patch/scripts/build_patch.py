#!/usr/bin/env python3
"""Apply a reviewed, hash-pinned, same-length native DEX patch. No device mutations."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
from environment import JDK, TOOLS

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def patch_dex(original, patches):
    data = bytearray(original)
    occupied = set()
    for patch in patches:
        offset = patch['offset']
        old, new = bytes.fromhex(patch['before']), bytes.fromhex(patch['after'])
        if not old or len(old) != len(new) or offset < 112 or offset + len(old) > len(data):
            raise ValueError('Invalid patch bounds or size')
        region = set(range(offset, offset + len(old)))
        if occupied & region:
            raise ValueError('Overlapping patches')
        occupied |= region
        if data[offset:offset + len(old)] != old:
            raise ValueError('Patch precondition mismatch: ' + patch['method'])
        data[offset:offset + len(new)] = new
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into('<I', data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)

def signature_entry(name):
    upper = name.upper()
    return upper == 'META-INF/MANIFEST.MF' or (
        upper.startswith('META-INF/') and upper.endswith(('.SF', '.RSA', '.DSA', '.EC')))

def main():
    manifest = json.loads((ROOT / 'patches.json').read_text())
    output = ROOT / 'dist'
    output.mkdir(exist_ok=True)
    (ROOT / 'work').mkdir(exist_ok=True)
    signing = ROOT / 'work' / 'native-test.keystore'
    if not signing.exists():
        subprocess.run([str(JDK/'bin/keytool'), '-genkeypair', '-keystore', str(signing),
                        '-storepass', 'android', '-keypass', 'android', '-alias', 'reelguard-native',
                        '-dname', 'CN=ReelGuard Local Native Test', '-keyalg', 'RSA',
                        '-keysize', '2048', '-validity', '3650'], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for item in manifest['inputs']:
        source = ROOT / 'original' / item['file']
        if digest(source) != item['sha256']:
            raise ValueError('Input SHA-256 mismatch: ' + item['file'])
        unsigned = ROOT/'work'/('unsigned-'+item['file'])
        aligned = ROOT/'work'/('aligned-'+item['file'])
        with zipfile.ZipFile(source) as src, zipfile.ZipFile(unsigned, 'w') as dst:
            for info in src.infolist():
                if signature_entry(info.filename):
                    continue
                data = src.read(info.filename)
                patches = [p for p in manifest['patches'] if item['file']=='base.apk' and p['dex']==info.filename]
                if patches:
                    data = patch_dex(data, patches)
                dst.writestr(info, data)
        subprocess.run([str(TOOLS/'zipalign'), '-P', '16', '-f', '4', str(unsigned), str(aligned)], check=True)
        dest = output/item['file']
        subprocess.run([str(TOOLS/'apksigner'), 'sign', '--ks', str(signing), '--ks-key-alias',
                        'reelguard-native', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
                        '--out', str(dest), str(aligned)], check=True)
        print('BUILT', item['file'], digest(dest), flush=True)
    print('PASS native APK set built')

if __name__ == '__main__':
    main()
