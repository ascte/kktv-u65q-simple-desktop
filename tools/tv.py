#!/usr/bin/env python3
"""tv.py - reliable ADB client wrapper for the Konka KKTV U65Q.

Auto-connects to the TV on every invocation (the adb daemon does not
survive between shell sessions). Usage:

  tv.py shell "<command>"            run a shell command on the TV
  tv.py screenshot <local.png> [keys] capture screenshot (optionally press
                                     space-separated keys first, e.g. DPAD_RIGHT)
  tv.py push <local> <remote>        push a file
  tv.py pull <remote> <local>        pull a file
  tv.py devices                      list devices
"""
import subprocess
import sys
import os

ADB = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "platform-tools", "adb.exe")
TV = os.environ.get("TV_ADDR", "10.10.10.27:5555")


def run(args, capture=True, check=True, timeout=120):
    cmd = [ADB, "-s", TV] + args
    p = subprocess.run(cmd, capture_output=capture, text=True,
                       timeout=timeout, errors="replace")
    if check and p.returncode != 0:
        err = (p.stderr or p.stdout or "").strip()
        raise RuntimeError(f"adb {' '.join(args)} failed: {err}")
    return p.stdout


def connect():
    subprocess.run([ADB, "connect", TV], capture_output=True, text=True,
                   timeout=30)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    op = sys.argv[1]
    connect()
    if op == "devices":
        out = run(["devices", "-l"], check=False)
        print(out)
        return 0
    if op == "shell":
        p = subprocess.run([ADB, "-s", TV, "shell", sys.argv[2]],
                           capture_output=True, text=True, timeout=120,
                           errors="replace")
        print(p.stdout, end="")
        if p.stderr.strip():
            print("[stderr] " + p.stderr.strip())
        return 0
    if op == "push":
        run(["push", sys.argv[2], sys.argv[3]], timeout=600)
        return 0
    if op == "pull":
        run(["pull", sys.argv[2], sys.argv[3]], timeout=600)
        return 0
    if op == "screenshot":
        local = sys.argv[2]
        keys = sys.argv[3].split() if len(sys.argv) > 3 else []
        for k in keys:
            run(["shell", "input", "keyevent", k])
        # capture on device, then pull (screencap stdout is binary;
        # /sdcard is not writable by shell on this firmware -> /data/local/tmp)
        run(["shell", "screencap", "-p", "/data/local/tmp/_shot.png"], check=False)
        run(["pull", "/data/local/tmp/_shot.png", local], check=False)
        size = os.path.getsize(local) if os.path.exists(local) else 0
        if size == 0:
            print("SCREENSHOT EMPTY (0 bytes) - app may block capture; "
                  "check dumpsys activity activities | grep topResumedActivity")
            return 1
        print(f"SCREENSHOT OK {local} ({size} bytes)")
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main())
