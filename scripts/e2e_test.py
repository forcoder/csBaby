# -*- coding: utf-8 -*-
"""
E2E Test Suite for csBaby Android App
"""
import subprocess
import sys
import os
import time
import re

# Force UTF-8 output
sys.stdout.reconfigure(encoding='utf-8')

SERVER_URL = "https://csbaby-sync-server-py.onrender.com"
PASS = "[PASS]"
FAIL = "[FAIL]"

results = []

def test(name, passed, detail=""):
    status = PASS if passed else FAIL
    results.append((name, passed))
    msg = f"  {status} {name}"
    if detail:
        msg += f" -- {detail}"
    print(msg)

def adb(cmd, timeout=15):
    try:
        r = subprocess.run(
            f"adb {cmd}", shell=True, capture_output=True, text=True,
            timeout=timeout, encoding='utf-8', errors='replace'
        )
        out = r.stdout or ""
        return out.strip(), r.returncode
    except Exception as e:
        return "", -1

# ========== Test 1: Server Health ==========
print("\n[1/5] Server Health Check")
try:
    import requests
    resp = requests.get(f"{SERVER_URL}/health", timeout=10)
    data = resp.json()
    test("Server health endpoint", data.get("status") == "ok", f"version={data.get('version')}")
except Exception as e:
    test("Server health endpoint", False, str(e))

# ========== Test 2: App Running ==========
print("\n[2/5] App Process Check")
stdout, rc = adb("shell pidof com.csbaby.kefu")
pid = stdout.strip()
test("App is running", pid != "", f"pid={pid}" if pid else "NOT RUNNING")

# ========== Test 3: Logcat Error Check ==========
print("\n[3/5] Logcat Error Scan")
if pid:
    stdout, _ = adb(f"logcat -d -t 200 --pid={pid}")
    if stdout:
        error_lines = [l for l in stdout.split('\n') if any(kw in l.upper() for kw in ['FATAL', 'CRASH'])]
        real_errors = [l for l in error_lines if 'hwui' not in l.lower()]
        test("No crash in logcat", len(real_errors) == 0,
             f"{len(real_errors)} crashes found" if real_errors else "clean")
    else:
        test("No crash in logcat", True, "no logcat output")
else:
    test("No crash in logcat", False, "App not running")

# ========== Test 4: UI Hierarchy Check ==========
print("\n[4/5] UI Hierarchy Check")
adb("shell \"uiautomator dump '/sdcard/uidump.xml'\"")
time.sleep(1)
stdout, _ = adb("shell \"cat '/sdcard/uidump.xml'\"")
has_profile = "我的" in stdout
test("Profile tab visible", has_profile, "found in UI dump")

# ========== Test 5: Navigation to Blacklist ==========
print("\n[5/5] Blacklist Screen Navigation")
# Tap on profile tab (bottom nav, rightmost) - center of [964,2460][1260,2590]
adb("shell input tap 1112 2525")
time.sleep(1)
# Dump UI again to check if profile screen is shown
adb("shell \"uiautomator dump '/sdcard/uidump.xml'\"")
time.sleep(1)
stdout, _ = adb("shell \"cat '/sdcard/uidump.xml'\"")
has_blacklist_entry = "黑名单" in stdout
test("Blacklist entry visible on profile screen", has_blacklist_entry, "found in UI dump")

if has_blacklist_entry:
    # Find and tap the blacklist entry
    match = re.search(r'text="消息黑名单"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', stdout)
    if match:
        x1, y1, x2, y2 = int(match.group(1)), int(match.group(2)), int(match.group(3)), int(match.group(4))
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        adb(f"shell input tap {cx} {cy}")
        time.sleep(1)
        # Check if blacklist screen is now shown
        adb("shell \"uiautomator dump '/sdcard/uidump.xml'\"")
        time.sleep(1)
        stdout2, _ = adb("shell \"cat '/sdcard/uidump.xml'\"")
        on_blacklist_screen = "添加黑名单" in stdout2 or "暂无黑名单" in stdout2
        test("Navigate to blacklist screen", on_blacklist_screen,
             "blacklist screen visible" if on_blacklist_screen else "navigation may have failed")
    else:
        test("Navigate to blacklist screen", False, "could not find bounds in UI dump")
else:
    test("Navigate to blacklist screen", False, "blacklist entry not found on profile screen")

# ========== Summary ==========
print("\n" + "=" * 50)
passed = sum(1 for _, p in results if p)
total = len(results)
print(f"Results: {passed}/{total} passed")
if passed == total:
    print("ALL E2E TESTS PASSED!")
else:
    print("SOME TESTS FAILED:")
    for name, p in results:
        if not p:
            print(f"  {FAIL} {name}")
sys.exit(0 if passed == total else 1)
