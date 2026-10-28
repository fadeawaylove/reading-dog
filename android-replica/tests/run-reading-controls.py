"""Run installed checks on a real device; bridge MIUI's restricted gesture injection."""
import re
import subprocess
import sys

command = ["adb", "shell", "am", "instrument", "-w"]
if "--offline" in sys.argv:
    command += ["-e", "phase", "offline"]
command += ["com.example.yuejie.readingcontrolschecks/com.example.yuejie.ReadingControlsChecks"]
process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, encoding="utf-8", errors="replace")
passed = False
for line in process.stdout:
    print(line, end="", flush=True)
    match = re.search(r"slider_swipe_ready: (\d+) (\d+) (\d+) (\d+)", line)
    if match:
        gesture = "input swipe " + " ".join(match.groups()) + " 400"
        subprocess.run(["adb", "shell", "su", "-c", gesture], check=True)
    if "PASS " in line:
        passed = True
sys.exit(0 if process.wait() == 0 and passed else 1)
