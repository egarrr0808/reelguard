#!/usr/bin/env python3
"""In-place update only; never uninstall or clear app data."""
import hashlib,json,re,subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'scripts'))
from environment import SDK, TOOLS
ADB=SDK/'platform-tools/adb'
SIGNER=TOOLS/'apksigner'
def run(*args):return subprocess.run([str(x)for x in args],check=True,capture_output=True,text=True).stdout
artifacts=sorted((ROOT/'dist').glob('*.apk'))
assert len(artifacts)==8
certs=[]
for p in artifacts:
    output=run(SIGNER,'verify','--print-certs',p)
    certs.append(re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',output).group(1))
assert len(set(certs))==1,'split certificates differ'
assert 'package:' in run(ADB,'shell','pm','path','com.instagram.android'),'requires existing app'
verification=json.loads((ROOT/'dist/verification.json').read_text())
for p in artifacts:assert hashlib.sha256(p.read_bytes()).hexdigest()==verification['output_sha256'][p.name]
print(run(ADB,'install-multiple','-r',*artifacts),flush=True)
paths=run(ADB,'shell','pm','path','com.instagram.android').splitlines()
hashes={}
for line in paths:
    path=line.removeprefix('package:').strip()
    hashes[Path(path).name]=run(ADB,'shell','sha256sum',path).split()[0]
assert hashes==verification['output_sha256'],'installed hashes differ'
record={'installation':'in-place update -r; no uninstall or data clear','installed_sha256':hashes,'all_hashes_match':True,'certificate_sha256':certs[0],'art_verification_exit':None,'art_runtime_signed_in_tested':False,'accessibility_services':run(ADB,'shell','settings','get','secure','enabled_accessibility_services').strip()}
# Open the existing launch activity; do not inspect user text or account contents.
record['launch']=run(ADB,'shell','am','start','-W','-n','com.instagram.android/com.instagram.mainactivity.InstagramMainActivity')
record['pid']=run(ADB,'shell','pidof','com.instagram.android').strip()
(ROOT/'dist/device-installation.json').write_text(json.dumps(record,indent=2)+'\n')
print('PASS update installed; 8 hashes match; app process alive',flush=True)
