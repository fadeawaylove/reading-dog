#!/usr/bin/env python3
"""Publish prepared release refs, then upload the identical local APK to the backup."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
API = 'https://api.github.com/repos/fadeawaylove/reading-dog'

def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'reading-dog-release'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-push', action='store_true', help='Finalize an already pushed release; safe to retry')
    parser.add_argument('--key', type=Path, default=ROOT / 'root-work/download-hosting/publish-key')
    parser.add_argument('--timeout', type=int, default=1800)
    args = parser.parse_args()
    version = json.loads((ROOT / 'android-replica/version.json').read_text(encoding='utf-8-sig'))['name']
    tag = 'v' + version
    name = 'reading-dog-' + version + '.apk'
    apk = (ROOT / 'android-replica/releases' / name).read_bytes()
    digest = hashlib.sha256(apk).hexdigest()
    if not args.key.is_file():
        raise RuntimeError('Publisher SSH key is missing')
    sha = git('rev-parse', tag + '^{commit}')
    # Bind the exact local bytes to the immutable release-assets branch before pushing.
    committed = subprocess.check_output(['git', 'show', 'release-assets/' + tag + ':android-replica/releases/' + name], cwd=ROOT)
    if committed != apk:
        raise RuntimeError('Local APK differs from prepared GitHub release asset')
    if not args.skip_push:
        if git('rev-parse', 'main') != sha:
            raise RuntimeError('Prepared release tag must point to main')
        subprocess.run(['git', 'push', '--atomic', 'origin', 'main', 'refs/heads/release-assets/' + tag, 'refs/tags/' + tag], cwd=ROOT, check=True)
    deadline = time.monotonic() + args.timeout
    while True:
        try:
            release = json.loads(fetch(API + '/releases/tags/' + tag))
            runs = json.loads(fetch(API + '/actions/workflows/validate.yml/runs?event=push&head_sha=' + sha))['workflow_runs']
            runs = [r for r in runs if r['head_branch'] == tag]
            if runs and runs[0]['status'] == 'completed' and runs[0]['conclusion'] != 'success':
                raise RuntimeError('GitHub release workflow failed: ' + runs[0]['html_url'])
            if runs and runs[0]['conclusion'] == 'success' and not release['draft'] and not release['prerelease']:
                break
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        if time.monotonic() >= deadline:
            raise TimeoutError('GitHub publication not complete; rerun with --skip-push')
        print('Waiting for GitHub release verification...', flush=True)
        time.sleep(15)
    assets = {a['name']: a for a in release['assets']}
    if hashlib.sha256(fetch(assets[name]['browser_download_url'])).hexdigest() != digest:
        raise RuntimeError('GitHub APK differs from local APK')
    sums = fetch(assets['SHA256SUMS.txt']['browser_download_url']).decode()
    if not any(line.split() == [digest, name] for line in sums.splitlines()):
        raise RuntimeError('GitHub checksum does not match')
    payload = {'version': version, 'apk': base64.b64encode(apk).decode(), 'sha256': digest,
               'notes': release['body'] or '', 'published_at': release['published_at']}
    result = subprocess.run(['ssh', '-T', '-i', str(args.key), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
                             '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=15',
                             'reading-dog-download@107.175.183.197', 'publish'],
                            input=json.dumps(payload).encode(), capture_output=True, timeout=180, check=True)
    receipt = json.loads(result.stdout)
    expected_url = 'http://magpie.fadeaway.ltd/reading-dog/' + version + '/' + name
    if receipt['sha256'] != digest or receipt['url'] != expected_url:
        raise RuntimeError('Backup upload receipt mismatch')
    if hashlib.sha256(fetch(expected_url)).hexdigest() != digest:
        raise RuntimeError('Backup download differs from local APK')
    print('Both downloads verified: ' + release['html_url'] + '\n' + expected_url + '\nSHA256: ' + digest)

if __name__ == '__main__':
    main()
