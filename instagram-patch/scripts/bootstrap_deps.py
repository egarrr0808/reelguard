#!/usr/bin/env python3
"""Fetch version- and SHA-256-pinned build dependencies; no APKs are downloaded."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def main():
    target = ROOT / 'tools/dexlib'
    target.mkdir(parents=True, exist_ok=True)
    for item in json.loads((ROOT / 'dependencies.json').read_text())['artifacts']:
        path = target / item['file']
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']:
            continue
        data = urllib.request.urlopen(item['url'], timeout=60).read()
        if hashlib.sha256(data).hexdigest() != item['sha256']:
            raise ValueError('Dependency SHA-256 mismatch: ' + item['file'])
        temporary = path.with_suffix('.download')
        temporary.write_bytes(data)
        temporary.replace(path)
    print('PASS pinned build dependencies')

if __name__ == '__main__':
    main()
