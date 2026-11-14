#!/usr/bin/env python3
"""Build daily quota on the reviewed Reels patch; preserve rollback APKs and signing identity."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

QUOTA=Path(__file__).resolve().parents[1]
ROOT=QUOTA.parent
sys.path.insert(0, str(ROOT / 'scripts'))
from environment import JDK, SDK, TOOLS as BT, CP
from verify_patch import main as verify_native_base
def run(*args):
    subprocess.run([str(a) for a in args],check=True,env={**os.environ,'JAVA_HOME':str(JDK)})
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    # Verify pinned original inputs and exact reviewed content, independently of the user's signing key.
    verify_native_base()
    work=QUOTA/'work';dist=QUOTA/'dist';work.mkdir(exist_ok=True);dist.mkdir(exist_ok=True)
    java=list((QUOTA/'src').rglob('*.java'))
    run(JDK/'bin/javac','--release','8','-cp',SDK/'platforms/android-35/android.jar','-d',work/'helper-classes',*java)
    helper=work/'helper-dex';helper.mkdir(exist_ok=True)
    run(BT/'d8','--min-api','28','--lib',SDK/'platforms/android-35/android.jar','--output',helper,*list((work/'helper-classes').rglob('*.class')))
    run(JDK/'bin/javac','-cp',CP,'-d',work,QUOTA/'scripts/QuotaPatch.java',QUOTA/'scripts/VerifyQuota.java')
    run(JDK/'bin/java','-Xmx2g','-cp',str(work)+':'+CP,'QuotaPatch',ROOT/'dist/base.apk',work/'dex')
    unsigned=work/'unsigned.apk'
    changed={p.name:p for p in (work/'dex').glob('*.dex')}
    with zipfile.ZipFile(ROOT/'dist/base.apk') as src,zipfile.ZipFile(unsigned,'w') as dst:
        dexes=[n for n in src.namelist() if __import__('re').fullmatch(r'classes\d*\.dex',n)]
        indices=[int(n[7:-4] or '1') for n in dexes]
        helper_name='classes'+str(max(indices)+1)+'.dex'
        for info in src.infolist():
            upper=info.filename.upper()
            if upper=='META-INF/MANIFEST.MF' or upper.startswith('META-INF/') and upper.endswith(('.SF','.RSA','.DSA','.EC')):continue
            dst.writestr(info,changed[info.filename].read_bytes() if info.filename in changed else src.read(info.filename))
        dst.writestr(helper_name,(helper/'classes.dex').read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    run(JDK/'bin/java','-Xmx2g','-cp',str(work)+':'+CP,'VerifyQuota',ROOT/'dist/base.apk',unsigned)
    # All non-DEX resources and all untouched DEX files remain byte-identical.
    with zipfile.ZipFile(ROOT/'dist/base.apk') as src,zipfile.ZipFile(unsigned) as dst:
        for n in dst.namelist():
            if n!=helper_name and n not in changed and src.read(n)!=dst.read(n):raise ValueError('Unexpected resource change '+n)
    aligned=work/'aligned.apk'
    run(BT/'zipalign','-P','16','-f','4',unsigned,aligned)
    run(BT/'apksigner','sign','--ks',ROOT/'work/native-test.keystore','--ks-key-alias','reelguard-native','--ks-pass','pass:android','--key-pass','pass:android','--out',dist/'base.apk',aligned)
    for p in (ROOT/'dist').glob('split_*.apk'):shutil.copy2(p,dist/p.name)
    hashes={}
    for p in sorted(dist.glob('*.apk')):
        run(BT/'apksigner','verify',p)
        run(BT/'zipalign','-c','-P','16','4',p)
        hashes[p.name]=digest(p)
    (dist/'verification.json').write_text(json.dumps({'quota':20,'input_base_sha256':digest(ROOT/'dist/base.apk'),'output_sha256':hashes,'changed_dex':sorted(changed),'helper_dex':helper_name,'original_methods_preserved':True,'runtime_verified':False},indent=2)+'\n')
    print('PASS quota APK build; install and runtime checks pending',flush=True)
if __name__=='__main__':main()
