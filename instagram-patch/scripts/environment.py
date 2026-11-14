"""Shared host paths. Environment overrides support fresh checkouts and local SDKs."""
import os
from pathlib import Path
import shlex
import shutil

ROOT = Path(__file__).resolve().parents[1]
local_jdk = Path.home() / '.local/opt/temurin-jdk21'
found_javac = shutil.which('javac')
default_jdk = local_jdk if local_jdk.is_dir() else (
    Path(found_javac).resolve().parents[1] if found_javac else Path('/usr/lib/jvm/default-java'))
JDK = Path(os.environ.get('REELGUARD_JDK') or os.environ.get('JAVA_HOME') or default_jdk)
SDK = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home() / 'Android/Sdk')
TOOLS = SDK / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '35.0.0')
DEPS = ROOT / 'tools/dexlib'
CP = os.environ.get('REEL_DEX_CP') or os.pathsep.join(str(DEPS / name) for name in (
    'dexlib2-2.5.2.jar', 'guava-32.1.3-jre.jar', 'failureaccess-1.0.1.jar'))

if __name__ == '__main__':
    for name, value in {'REEL_PATCH_ROOT': ROOT, 'REEL_PATCH_JDK': JDK, 'REEL_DEX_CP': CP}.items():
        print('export ' + name + '=' + shlex.quote(str(value)))
