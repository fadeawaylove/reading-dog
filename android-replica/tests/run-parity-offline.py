from pathlib import Path
import subprocess
A=r"C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
def shell(command):return subprocess.run([A,"shell",command],capture_output=True,text=True,encoding="utf-8",errors="replace")
wifi=shell("settings get global wifi_on").stdout.strip()
mobile=shell("settings get global mobile_data").stdout.strip()
try:
 shell("su -c 'svc wifi disable; svc data disable'")
 run=shell("su -c 'am instrument -w -e phase offline com.example.yuejie.commentchecks/com.example.yuejie.CommentChecks'")
 print(run.stdout,flush=True)
 Path(__file__).with_name("offline-parity.log").write_text(run.stdout+run.stderr,encoding="utf-8")
finally:
 shell("su -c 'svc wifi "+("enable" if wifi=="1" else "disable")+"; svc data "+("enable" if mobile=="1" else "disable")+"'")
 print("Restored Wi-Fi",wifi,"mobile",mobile)
