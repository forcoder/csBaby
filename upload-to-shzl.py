#!/usr/bin/env python3
"""csBaby - Auto Build and Upload to shz.al"""
import os
import re
import hashlib
import json
import subprocess
import urllib.request
import time

PASSWORD = os.environ.get("SHZAL_PASSWORD", "")
if not PASSWORD:
    raise RuntimeError("SHZAL_PASSWORD environment variable must be set")
EXPIRE_DAYS = "7d"
API_BASE = "https://shz.al"
VERSION_FILE = "gradle.properties"
APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"
VERSION_FNAME = "csBabyLog"
APK_FNAME = "csBabyApk"

def log(msg, level="INFO"):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] {msg}")

def get_version():
    with open(VERSION_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    code_m = re.search(r"APP_VERSION_CODE=(\d+)", content)
    name_m = re.search(r"APP_VERSION_NAME=(.+)", content)
    if not code_m or not name_m:
        raise RuntimeError(f"Cannot read version info from {VERSION_FILE}")
    version_code = int(code_m.group(1))
    version_name = name_m.group(1).strip()
    return version_code, version_name

def save_version(code, name):
    with open(VERSION_FILE, "r", encoding="utf-8") as f:
        lines = f.readlines()
    new_lines = []
    for line in lines:
        if re.match(r"APP_VERSION_CODE=", line):
            new_lines.append(f"APP_VERSION_CODE={code}\n")
        elif re.match(r"APP_VERSION_NAME=", line):
            new_lines.append(f"APP_VERSION_NAME={name}\n")
        else:
            new_lines.append(line)
    with open(VERSION_FILE, "w", encoding="utf-8") as f:
        f.writelines(new_lines)
    log(f"Version saved: {code}/{name}")

def increment_version(code, name):
    new_code = code + 1
    parts = name.strip().split(".")
    patch = int(parts[2]) if len(parts) > 2 else 0
    patch += 1
    new_name = f"{parts[0]}.{parts[1]}.{patch}"
    log(f"Version: {name} ({code}) -> {new_name} ({new_code})")
    return new_code, new_name

def file_md5(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def run_curl(args):
    """Run curl and return stdout"""
    result = subprocess.run(args, shell=True, capture_output=True)
    return result.stdout.decode("utf-8", errors="replace")

def curl_delete(name):
    log(f"DELETE: {name}")
    url = f"{API_BASE}/~{name}:{PASSWORD}"
    result = run_curl(f'curl -s -X DELETE "{url}"')
    log(f"DELETE response: {result[:80]}")

def curl_upload(name, content=None, filepath=None, filename=None):
    curl_delete(name)
    url = API_BASE
    
    if filepath:
        log(f"Uploading file: {filename} ({os.path.getsize(filepath)} bytes)")
        result = run_curl(
            f'curl -s -X POST '
            f'-F "n={name}" '
            f'-F "e={EXPIRE_DAYS}" '
            f'-F "filename={filename}" '
            f'-F "c=@{filepath}" '
            f'-F "s={PASSWORD}" '
            f'"{url}"'
        )
    else:
        log(f"Uploading text: {name}")
        # Write content to temp file
        tmp = filepath + ".tmp" if filepath else "upload_tmp.txt"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(content)
        result = run_curl(
            f'curl -s -X POST '
            f'-F "n={name}" '
            f'-F "e={EXPIRE_DAYS}" '
            f'-F "c=@{tmp}" '
            f'-F "s={PASSWORD}" '
            f'"{url}"'
        )
        if not filepath:
            os.remove(tmp)
    
    log(f"API: {result[:100]}")
    try:
        obj = json.loads(result)
        if "url" in obj:
            log(f"Success: {obj['url']}")
            return obj
    except:
        pass
    return {"url": f"{API_BASE}/~{name}"}

def main():
    log("========== Build and Upload Start ==========")
    
    code, name = get_version()
    log(f"Current: {name} ({code})")
    new_code, new_name = increment_version(code, name)
    save_version(new_code, new_name)
    
    log("Cleaning old build...")
    apk_dir = os.path.dirname(APK_PATH)
    if os.path.exists(apk_dir):
        import shutil
        shutil.rmtree(apk_dir)
    
    log("Building APK...")
    gradle_cmd = "gradlew.bat" if os.path.exists("gradlew.bat") else "gradlew"
    result = subprocess.run(
        [gradle_cmd, "assembleDebug", "--no-daemon", "-q"],
        shell=True, capture_output=True, text=True
    )
    if result.returncode != 0:
        log(f"Build failed: {result.stderr}", "ERROR")
        raise RuntimeError("APK build failed")
    log("APK built", "SUCCESS")
    
    if not os.path.exists(APK_PATH):
        raise RuntimeError(f"APK not found: {APK_PATH}")
    apk_size = os.path.getsize(APK_PATH)
    apk_md5 = file_md5(APK_PATH)
    log(f"APK: {apk_size} bytes, MD5: {apk_md5}")
    
    version_json = {
        "versionCode": new_code,
        "versionName": new_name,
        "releaseDate": time.strftime("%Y-%m-%d"),
        "fileSize": apk_size,
        "md5": apk_md5,
        "downloadUrl": f"{API_BASE}/~{APK_FNAME}",
        "releaseNotes": f"v{new_name} update",
        "forceUpdate": False
    }
    log("[1/2] Uploading version info...")
    curl_upload(VERSION_FNAME, content=json.dumps(version_json, indent=2))
    
    log("[2/2] Uploading APK...")
    curl_upload(APK_FNAME, filepath=APK_PATH, filename="app-debug.apk")
    
    log("========== Complete ==========", "SUCCESS")
    log(f"Version: {API_BASE}/~{VERSION_FNAME}", "SUCCESS")
    log(f"APK: {API_BASE}/~{APK_FNAME}", "SUCCESS")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        log(f"Error: {e}", "ERROR")
        exit(1)
