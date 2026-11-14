#!/usr/bin/env python3
"""Validate the explicit publication allowlist, optionally write a source-only archive."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {'original', 'work', 'dist', 'build', 'tools', '__pycache__', '.git', 'exports'}
FORBIDDEN_SUFFIXES = {'.apk', '.aab', '.apks', '.dex', '.jar', '.class', '.pyc', '.keystore', '.jks', '.pem', '.key', '.idsig', '.log'}
SECRET_PATTERNS = [
    re.compile(r'gh[pousr]_[A-Za-z0-9]{20,}'),
    re.compile(r'github_pat_[A-Za-z0-9_]{20,}'),
    re.compile(r'-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----'),
    re.compile(r'/(?:home|Users)/[A-Za-z0-9_.-]+/'),
]

def public_files():
    names = (ROOT / 'public-files.txt').read_text().splitlines()
    if len(names) != len(set(names)):
        raise ValueError('Duplicate publication entry')
    for name in names:
        path = PurePosixPath(name)
        if not name or path.is_absolute() or '..' in path.parts or path.as_posix() != name:
            raise ValueError('Invalid publication path: ' + name)
        if FORBIDDEN_PARTS.intersection(path.parts) or path.suffix.lower() in FORBIDDEN_SUFFIXES:
            raise ValueError('Private/binary publication path: ' + name)
        if path.name in {'PLAN.md', 'GATES.md', 'VERIFICATION.md', '.env', 'reelguard.json', 'device-installation.json'}:
            raise ValueError('Private working data: ' + name)
        disk = ROOT / name
        if disk.is_symlink() or not disk.is_file() or not disk.resolve().is_relative_to(ROOT):
            raise ValueError('Not an ordinary source file: ' + name)
        data = disk.read_bytes()
        if len(data) > 2_000_000:
            raise ValueError('Oversized source: ' + name)
        text = data.decode('utf-8')
        if any(pattern.search(text) for pattern in SECRET_PATTERNS):
            raise ValueError('Secret or workstation path: ' + name)
        yield name, data

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--archive', action='store_true')
    args = parser.parse_args()
    files = dict(public_files())
    if not {'README.md', 'LICENSE', '.gitignore', 'public-files.txt'} <= files.keys():
        raise ValueError('Missing release metadata')
    if (ROOT / '.git').exists():
        tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
        if set(filter(None, tracked)) - files.keys():
            raise ValueError('Tracked files outside publication allowlist')
    if args.archive:
        build = ROOT / 'build'
        build.mkdir(exist_ok=True)
        archive = build / 'reelguard-source.tar.gz'
        with tarfile.open(archive, 'w:gz') as tar:
            for name, data in sorted(files.items()):
                info = tarfile.TarInfo('reelguard/' + name)
                info.size = len(data)
                info.mode = 0o755 if name.endswith('.sh') else 0o644
                info.mtime = 0
                tar.addfile(info, io.BytesIO(data))
        (build / 'source-manifest.json').write_text(json.dumps({name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())}, indent=2) + '\n')
        (build / 'reelguard-source.tar.gz.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest() + '  reelguard-source.tar.gz\n')
        print('ARCHIVE', archive)
    print('PASS publication allowlist: ' + str(len(files)) + ' source files; no private artifacts')

if __name__ == '__main__':
    main()
