#!/usr/bin/env python3
"""Publish a signed APK directly to the private backend. No Git commands or GitHub API."""
import argparse,hashlib,io,json,os,re,shutil,subprocess,tarfile
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1]
ORIGIN='https://reader.fadeaway.ltd'
SIGNER='3d036c2956aa9369777eb0966aa61ea6c84870a2c87860cd8e8c020ff8990bb8'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,default=ROOT/'android-replica/touch-build/yuejie.apk')
    parser.add_argument('--key',type=Path,default=ROOT/'root-work/backend-publish-key')
    parser.add_argument('--host',default='107.175.183.197')
    parser.add_argument('--sdk',type=Path,default=Path('C:/Android/Sdk'))
    parser.add_argument('--prepare-only',action='store_true')
    parser.add_argument('--password',action='store_true',help='Prompt for SSH password (requires paramiko); creates no persistent access key')
    parser.add_argument('--saved-connection',action='store_true',help='Use the local Windows DPAPI-encrypted SSH profile')
    args=parser.parse_args();v=json.loads((ROOT/'android-replica/version.json').read_text(encoding='utf-8'));version=v['name'];code=v['code']
    if not re.fullmatch(r'[0-9]{1,9}(?:\.[0-9]{1,9}){0,3}',version):raise ValueError('Invalid version')
    bt=args.sdk/'build-tools/35.0.0';suffix='.bat' if os.name=='nt' else '';java_bin=Path(shutil.which('java')).parent;env=dict(os.environ,JAVA_HOME=str(java_bin.parent))
    signature=subprocess.check_output([str(bt/('apksigner'+suffix)),'verify','--print-certs',str(args.apk)],env=env).decode()
    if not re.search(r'certificate SHA-256 digest: '+SIGNER+r'\b',signature):raise ValueError('Unexpected signing identity')
    badging=subprocess.check_output([str(bt/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','badging',str(args.apk)]).decode('utf-8')
    if f"name='com.example.yuejie' versionCode='{code}' versionName='{version}'" not in badging:raise ValueError('APK package/version mismatch')
    apk=args.apk.read_bytes();digest=hashlib.sha256(apk).hexdigest();name=f'reading-dog-{version}.apk'
    notes=(ROOT/'RELEASE_NOTES.md').read_text(encoding='utf-8').split('\n# ',1)[0]
    metadata={'tag_name':'v'+version,'version_code':code,'body':notes,'published_at':datetime.now(timezone.utc).isoformat(),'assets':[
        {'name':name,'size':len(apk),'digest':'sha256:'+digest,'browser_download_url':f'{ORIGIN}/releases/{version}/{name}'},
        {'name':'SHA256SUMS.txt','browser_download_url':f'{ORIGIN}/releases/{version}/SHA256SUMS.txt'}]}
    out=ROOT/'backend/build';out.mkdir(parents=True,exist_ok=True);bundle=out/'release.tar'
    with tarfile.open(bundle,'w') as tar:
        for filename,data in [('latest.json',(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()),(name,apk),('SHA256SUMS.txt',f'{digest}  {name}\n'.encode())]:
            item=tarfile.TarInfo(filename);item.size=len(data);item.mode=0o644;tar.addfile(item,io.BytesIO(data))
    if not args.prepare_only:
        if args.password or args.saved_connection:
            import getpass,paramiko
            client=paramiko.SSHClient();client.load_system_host_keys()
            def transport(sock,**kwargs):
                t=paramiko.Transport(sock,**kwargs);t.local_version='SSH-2.0-OpenSSH_9.5';return t
            if args.saved_connection:
                from server_connection import load
                saved=load()
                if saved['host']!=args.host:raise ValueError('Saved SSH profile host does not match --host')
                username=saved['username'];password=saved['password']
            else:username='root';password=getpass.getpass('SSH password: ')
            client.connect(args.host,username=username,password=password,look_for_keys=False,allow_agent=False,timeout=30,transport_factory=transport)
            try:
                remote='/opt/reading-dog-backend/incoming-release.tar'
                with client.open_sftp() as s:s.put(str(bundle),remote)
                _,stdout,stderr=client.exec_command('SSH_ORIGINAL_COMMAND=publish python3 /opt/reading-dog-backend/receive-release.py < /opt/reading-dog-backend/incoming-release.tar',timeout=120)
                message=stdout.read().decode();errors=stderr.read().decode()
                if stdout.channel.recv_exit_status():raise RuntimeError(errors)
                print(message.strip())
            finally:client.close()
        else:
            with bundle.open('rb') as source:subprocess.run(['ssh','-T','-i',str(args.key),'-o','IdentitiesOnly=yes','-o','BatchMode=yes',f'root@{args.host}','publish'],stdin=source,check=True)
        import urllib.request
        config=json.loads((ROOT/'android-replica/assets/backend-private.json').read_text())
        def get(path):
            request=urllib.request.Request(ORIGIN+path,headers={'Authorization':'Bearer '+config['token']})
            with urllib.request.urlopen(request,timeout=90) as response:return response.read()
        current=json.loads(get('/api/v1/updates/latest'))
        if current['tag_name']!='v'+version:raise ValueError('Published latest mismatch')
        if hashlib.sha256(get(f'/releases/{version}/{name}')).hexdigest()!=digest:raise ValueError('Published APK hash mismatch')
    print(f'Prepared {version}, code {code}, {len(apk)} bytes, SHA256 {digest}' if args.prepare_only else f'Published and verified {version} on {ORIGIN}')
if __name__=='__main__':main()
