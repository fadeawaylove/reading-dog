#!/usr/bin/env python3
"""Create a private, revocable browser download link scoped to one published APK."""
import argparse,fcntl,hashlib,json,os,secrets,tempfile
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('version')
    p.add_argument('--data',type=Path,default=Path('/opt/reading-dog-backend/data'))
    args=p.parse_args()
    import re
    if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){0,3}',args.version):raise ValueError('Invalid version')
    name=f'reading-dog-{args.version}.apk'
    if not (args.data/'releases'/args.version/name).is_file():raise ValueError('APK is not published')
    token=secrets.token_hex(24);digest=hashlib.sha256(token.encode('ascii')).hexdigest()
    path=args.data/'install-links.json'
    with (args.data/'.install-links.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        links=json.loads(path.read_text()) if path.exists() else {}
        links[digest]={'version':args.version}
        fd,tmp=tempfile.mkstemp(prefix='.install-links-',dir=args.data)
        try:
            with os.fdopen(fd,'w') as out:json.dump(links,out);out.flush();os.fsync(out.fileno())
            if os.geteuid()==0:os.chown(tmp,10001,10001)
            os.chmod(tmp,0o600);os.replace(tmp,path)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    print(f'https://reader.fadeaway.ltd/install/{token}/{name}')

if __name__=='__main__':main()
