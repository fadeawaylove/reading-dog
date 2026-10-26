#!/usr/bin/env python3
"""Forced SSH command: accept one verified release payload, never arbitrary shell commands."""
import base64, hashlib, html, json, os, re, sys, fcntl
from pathlib import Path
ROOT=Path("/srv/reading-dog-download/reading-dog")
BASE="http://magpie.fadeaway.ltd/reading-dog"
LIMIT=100*1024*1024

def atomic(path,data):
    part=path.with_name(path.name+".part")
    part.write_bytes(data);part.chmod(0o644);os.replace(part,path)
def main():
    raw=sys.stdin.buffer.read((LIMIT*4//3)+1024*1024+1)
    if len(raw)>(LIMIT*4//3)+1024*1024:raise ValueError("Payload too large")
    data=json.loads(raw);version=data["version"]
    if not re.fullmatch(r"[0-9]{1,9}(?:\.[0-9]{1,9}){0,3}",version):raise ValueError("Invalid version")
    apk=base64.b64decode(data["apk"],validate=True);digest=hashlib.sha256(apk).hexdigest()
    if not 0<len(apk)<=LIMIT or digest!=data["sha256"]:raise ValueError("Invalid APK checksum or size")
    notes=data.get("notes","")
    if len(notes)>200000:raise ValueError("Notes too large")
    ROOT.mkdir(parents=True,exist_ok=True)
    with (ROOT/".publish.lock").open("w") as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        name="reading-dog-"+version+".apk";directory=ROOT/version;directory.mkdir(exist_ok=True)
        destination=directory/name
        if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest()!=digest:raise ValueError("Existing version is immutable")
        atomic(destination,apk);sums=(digest+"  "+name+"\n").encode();atomic(directory/"SHA256SUMS.txt",sums)
        prefix=BASE+"/"+version+"/"
        metadata={"tag_name":"v"+version,"draft":False,"prerelease":False,"body":notes,"published_at":data["published_at"],"assets":[{"name":name,"size":len(apk),"digest":"sha256:"+digest,"browser_download_url":prefix+name},{"name":"SHA256SUMS.txt","size":len(sums),"browser_download_url":prefix+"SHA256SUMS.txt"}]}
        latest=ROOT/"latest.json"
        if latest.exists():
            current=json.loads(latest.read_text())["tag_name"].removeprefix("v")
            def parts(v):return tuple((list(map(int,v.split(".")))+[0]*4)[:4])
            if parts(version)<parts(current):raise ValueError("Refusing to downgrade latest release")
        document='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>阅读狗备用下载</title><style>body{max-width:680px;margin:60px auto;padding:0 24px;font:18px/1.7 system-ui;background:#faf8f1;color:#222}a{color:#276146}pre{white-space:pre-wrap;font:inherit;font-size:15px}</style><h1>阅读狗 '+html.escape(version)+'</h1><p><a href="'+prefix+name+'">备用下载安装包</a></p><p><a href="https://github.com/fadeawaylove/reading-dog/releases/tag/v'+version+'">GitHub 正式发布</a> · <a href="'+prefix+'SHA256SUMS.txt">SHA-256 校验文件</a></p><pre>'+html.escape(notes)+'</pre></html>'
        atomic(ROOT/"index.html",document.encode());atomic(latest,(json.dumps(metadata,ensure_ascii=False,indent=2)+"\n").encode())
        print(json.dumps({"version":version,"url":prefix+name,"sha256":digest,"size":len(apk)}))
if __name__=="__main__":
    try:main()
    except Exception as error:print(str(error),file=sys.stderr);sys.exit(1)
