#!/usr/bin/env python3
"""Forced SSH command: validate an immutable bundle, atomically promote latest."""
from pathlib import Path
import fcntl,hashlib,io,json,os,re,shutil,sys,tarfile,tempfile,zipfile

def receive():
    os.umask(0o022);root=Path('/opt/reading-dog-backend/data/releases');root.mkdir(parents=True,exist_ok=True)
    if os.environ.get('SSH_ORIGINAL_COMMAND')!='publish':raise ValueError('Only publish is supported')
    with tempfile.TemporaryFile() as stream:
        size=0
        while chunk:=sys.stdin.buffer.read(65536):
            size+=len(chunk)
            if size>110*1024*1024:raise ValueError('Release bundle too large')
            stream.write(chunk)
        stream.seek(0)
        with tarfile.open(fileobj=stream,mode='r:') as tar:
            items=tar.getmembers()
            if len(items)!=3 or any(not x.isfile() or x.size>100*1024*1024 for x in items) or len({x.name for x in items})!=3:raise ValueError('Invalid bundle')
            files={x.name:tar.extractfile(x).read() for x in items}
    meta=json.loads(files['latest.json']);version=meta['tag_name'].removeprefix('v');code=meta['version_code']
    if not re.fullmatch(r'[0-9]{1,9}(?:\.[0-9]{1,9}){0,3}',version) or type(code) is not int or code<=0:raise ValueError('Invalid version')
    name=f'reading-dog-{version}.apk'
    if set(files)!={'latest.json','SHA256SUMS.txt',name}:raise ValueError('Unexpected files')
    data=files[name];digest=hashlib.sha256(data).hexdigest();assets=meta['assets']
    if not data.startswith(b'PK') or not 0<len(data)<=100*1024*1024:raise ValueError('Invalid APK')
    if len(assets)!=2 or assets[0]['name']!=name or assets[0]['size']!=len(data) or assets[0]['digest']!='sha256:'+digest:raise ValueError('APK metadata mismatch')
    base=f'https://reader.fadeaway.ltd/releases/{version}/'
    if assets[0]['browser_download_url']!=base+name or assets[1]['name']!='SHA256SUMS.txt' or assets[1]['browser_download_url']!=base+'SHA256SUMS.txt':raise ValueError('Invalid URLs')
    if files['SHA256SUMS.txt']!=f'{digest}  {name}\n'.encode():raise ValueError('Checksum mismatch')
    with zipfile.ZipFile(io.BytesIO(data)) as apk:
        if apk.testzip() is not None or 'AndroidManifest.xml' not in apk.namelist():raise ValueError('Invalid APK archive')
    with (root/'.publish.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX);latest=root/'latest.json'
        if latest.exists():
            old=json.loads(latest.read_text())
            if code<old['version_code'] or code==old['version_code'] and old['tag_name']!=meta['tag_name']:raise ValueError('Version rollback/conflict')
        target=root/version
        if target.exists():
            existing=json.loads((target/'latest.json').read_text())
            if (target/name).read_bytes()!=data or existing['version_code']!=code:raise ValueError('Version already has different bytes')
            files['latest.json']=(target/'latest.json').read_bytes()
        else:
            staging=Path(tempfile.mkdtemp(prefix='.publish-',dir=root))
            try:
                staging.chmod(0o755)
                for filename,value in files.items():
                    path=staging/filename
                    with path.open('wb') as out:out.write(value);out.flush();os.fsync(out.fileno())
                    path.chmod(0o644)
                os.rename(staging,target)
            finally:
                if staging.exists():shutil.rmtree(staging)
        tmp=root/'.latest-next.json'
        with tmp.open('wb') as out:out.write(files['latest.json']);out.flush();os.fsync(out.fileno())
        os.replace(tmp,latest);directory=os.open(root,os.O_DIRECTORY);os.fsync(directory);os.close(directory)
    print(f'Published {version}; immutable APK and latest metadata ready.')
if __name__=='__main__':
    try:receive()
    except Exception as e:print('Publication rejected: '+str(e),file=sys.stderr);sys.exit(1)
