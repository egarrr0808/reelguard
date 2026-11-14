#!/usr/bin/env python3
"""Verify APK contents independently of ZIP repacking and Android signing metadata."""
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
from environment import TOOLS

def is_signature(name):
    return name == 'stamp-cert-sha256' or re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))',name.upper()) is not None

def verify_content(original, patched, patches):
    if len(original) != len(patched):
        raise AssertionError('DEX length changed')
    allowed = set(range(8,32))
    for p in patches:
        start = p['offset']
        before, after = bytes.fromhex(p['before']), bytes.fromhex(p['after'])
        assert original[start:start+len(before)] == before, p['method']
        assert patched[start:start+len(after)] == after, p['method']
        allowed.update(range(start,start+len(after)))
    changed = {i for i,(a,b) in enumerate(zip(original,patched)) if a != b}
    assert changed and changed <= allowed, 'Unexpected DEX modification'
    assert patched[12:32] == hashlib.sha1(patched[32:]).digest(), 'Bad DEX SHA-1'
    assert struct.unpack_from('<I',patched,8)[0] == zlib.adler32(patched[12:]) & 0xffffffff, 'Bad DEX Adler32'

def main():
    manifest = json.loads((ROOT/'patches.json').read_text())
    certificates=set()
    checked_entries=0
    for item in manifest['inputs']:
        original=ROOT/'original'/item['file']; patched=ROOT/'dist'/item['file']
        assert hashlib.sha256(original.read_bytes()).hexdigest()==item['sha256']
        out=subprocess.check_output([str(TOOLS/'apksigner'),'verify','--verbose','--print-certs',str(patched)],text=True)
        cert=re.search(r'Signer #1 certificate SHA-256 digest: (\w+)',out)
        assert cert, 'Missing signing certificate'
        certificates.add(cert[1])
        subprocess.run([str(TOOLS/'zipalign'),'-c','-P','16','4',str(patched)],check=True)
        with zipfile.ZipFile(original) as a,zipfile.ZipFile(patched) as b:
            a_names={n for n in a.namelist() if not is_signature(n)}
            b_names={n for n in b.namelist() if not is_signature(n)}
            assert a_names==b_names, 'APK entries changed'
            assert len(b.namelist())==len(set(b.namelist())), 'Duplicate entries'
            for name in a_names:
                left,right=a.read(name),b.read(name)
                entries=[p for p in manifest['patches'] if item['file']=='base.apk' and p['dex']==name]
                if entries: verify_content(left,right,entries)
                else: assert left==right, 'Unexpected change: '+name
                checked_entries+=1
        badging=subprocess.check_output([str(TOOLS/'aapt'),'dump','badging',str(patched)],text=True)
        assert "name='com.instagram.android'" in badging
        assert "versionCode='386113969'" in badging
        print('VERIFIED',item['file'],flush=True)
    assert len(certificates)==1, 'Split signing mismatch'
    summary={'package':manifest['package'],'version_name':manifest['version_name'],
             'splits':len(manifest['inputs']),'patch_sites':len(manifest['patches']),
             'unchanged_or_reviewed_zip_entries':checked_entries,'signer_sha256':next(iter(certificates)),
             'runtime_verified':False,
             'artifacts':[{'file':i['file'],'sha256':hashlib.sha256((ROOT/'dist'/i['file']).read_bytes()).hexdigest()} for i in manifest['inputs']]}
    (ROOT/'dist/verification.json').write_text(json.dumps(summary,indent=2)+'\n')
    print('PASS native signatures, alignment, split metadata and exact-change verification')

if __name__=='__main__': main()
