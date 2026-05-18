# -*- coding: utf-8 -*-
"""
编译后自动上传脚本
- 读取 APK 的 BuildConfig（versionName / versionCode）
- 上传到 OSS
- 写入 version-info.json
- 追加 upload-history.json
"""
import oss2
import hashlib
import os
import json
import re
from datetime import datetime

# 路径
project_dir = r'D:\workspace\workbuddy\csBaby'
apk_path = os.path.join(project_dir, r'app\build\outputs\apk\debug\app-debug.apk')
config_path = os.path.join(project_dir, 'oss-config.properties')
gradle_props = os.path.join(project_dir, 'gradle.properties')
version_info_path = os.path.join(project_dir, 'version-info.json')
history_path = os.path.join(project_dir, 'upload-history.json')

# 读取 OSS 配置
props = {}
with open(config_path, encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            props[k.strip()] = v.strip()

bucket_name = props['oss.bucket']
access_key_id = props['oss.access_key_id']
access_key_secret = props['oss.access_key_secret']
app_name = props.get('app.name', 'kefu')
apk_folder = props.get('oss.apk_folder', 'apks/')

# 读取 gradle.properties 中的版本号
gradle_ver_code = None
gradle_ver_name = None
with open(gradle_props, encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if line.startswith('APP_VERSION_CODE='):
            gradle_ver_code = int(line.split('=', 1)[1].strip())
        elif line.startswith('APP_VERSION_NAME='):
            gradle_ver_name = line.split('=', 1)[1].strip()

# 从 APK 包内提取 BuildConfig 的 versionName / versionCode
version_name = gradle_ver_name or "1.0.0"
version_code = gradle_ver_code or 1

# 备用：从 APK 文件名解析（debug 包默认格式 app-debug.apk）
# 更可靠的方式是用 aapt 或androguard 解析，但这里直接用 gradle.properties

# 计算 MD5
md5_hash = hashlib.md5()
with open(apk_path, 'rb') as f:
    for chunk in iter(lambda: f.read(8192), b''):
        md5_hash.update(chunk)
file_md5 = md5_hash.hexdigest()
file_size = os.path.getsize(apk_path)

# 生成 Object Key
now = datetime.now()
date_str = now.strftime('%Y-%m-%d')
time_str = now.strftime('%H%M%S')
object_key = '{}{}/v{}_{}/{}/{}_{}.apk'.format(
    apk_folder, app_name,
    version_name, version_code,
    date_str, time_str, file_md5[:8]
)

print('=' * 50)
print('APK: ' + apk_path)
print('Size: {:,} bytes ({:.2f} MB)'.format(file_size, file_size/1024/1024))
print('Version: v{} ({})'.format(version_name, version_code))
print('MD5: ' + file_md5)
print('Object Key: ' + object_key)
print('Bucket: ' + bucket_name)
print('Uploading...')

# OSS endpoint：配置中已包含 bucket 前缀，截取域名部分
full_endpoint = props['oss.endpoint'].replace('https://', '').replace('http://', '')
endpoint = full_endpoint.replace(bucket_name + '.', '')

# 上传
auth = oss2.Auth(access_key_id, access_key_secret)
bucket = oss2.Bucket(auth, endpoint, bucket_name)
result = bucket.put_object_from_file(object_key, apk_path)

if result.status == 200:
    download_url = 'https://' + full_endpoint + '/' + object_key
    print('HTTP 200 - Upload SUCCESS!')
    print('Download URL: ' + download_url)

    # 写入 version-info.json
    version_info = {
        'version_name': version_name,
        'version_code': version_code,
        'build_time': now.strftime('%Y-%m-%d %H:%M:%S'),
        'apk_url': download_url,
        'md5': file_md5,
        'file_size': file_size,
        'object_key': object_key
    }
    with open(version_info_path, 'w', encoding='utf-8') as f:
        json.dump(version_info, f, indent=2, ensure_ascii=False)
    print('Written: ' + version_info_path)

    # 追加 upload-history.json
    history = []
    if os.path.exists(history_path):
        with open(history_path, encoding='utf-8') as f:
            content = f.read().strip()
            if content:
                history = json.loads(content)
                if not isinstance(history, list):
                    history = [history]

    history.append({
        'timestamp': now.strftime('%Y-%m-%d %H:%M:%S'),
        'version_name': version_name,
        'version_code': version_code,
        'apk_url': download_url,
        'md5': file_md5,
        'object_key': object_key
    })

    # 只保留最近 20 条
    history = history[-20:]

    with open(history_path, 'w', encoding='utf-8') as f:
        json.dump(history, f, indent=2, ensure_ascii=False)
    print('Written: ' + history_path)

    print('=' * 50)
    print('ALL DONE!')
else:
    print('FAILED: HTTP ' + str(result.status))
    print(str(result.resp))
    exit(1)
