from pathlib import Path
import subprocess,time,json
ADB=r'C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
ROOT=Path(__file__).resolve().parent/'turn-parity'/'app'
ROOT.mkdir(parents=True,exist_ok=True)
for key,label in [('simulation','仿真'),('cover','覆盖'),('slide','平移'),('none','无动画')]:
    remote=f'/data/local/tmp/parity-app-{key}.mp4'
    recording=subprocess.Popen([ADB,'shell',f'screenrecord --time-limit 55 --bit-rate 4000000 {remote}'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(.6)
    run=subprocess.run([ADB,'shell',f"su -c 'am instrument -w -e mode {key} com.example.yuejie.tests/com.example.yuejie.MatchedCapture'"],capture_output=True,timeout=60)
    (ROOT/f'{key}.log').write_bytes(run.stdout+run.stderr)
    # Stop only this recording, allowing screenrecord to finalize its MP4.
    subprocess.run([ADB,'shell',f"su -c 'for p in $(pidof screenrecord); do case $(cat /proc/$p/cmdline) in *parity-app-{key}.mp4*) kill -2 $p;; esac; done'"],capture_output=True)
    try: recording.communicate(timeout=5)
    except subprocess.TimeoutExpired: recording.terminate();recording.communicate()
    subprocess.run([ADB,'pull',remote,str(ROOT/f'{key}.mp4')],capture_output=True)
    print(key,run.stdout.decode('utf-8','replace').strip(),flush=True)
    if b'PASS matched' not in run.stdout:break
