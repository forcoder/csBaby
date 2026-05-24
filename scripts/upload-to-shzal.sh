#!/bin/bash
# 上传APK到 shz.al

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "APK not found: $APK_PATH"
    exit 1
fi

echo "Uploading to shz.al..."
# 使用 curl 上传文件到 shz.al
response=$(curl -s -F "file=@${APK_PATH}" https://shz.al)
echo "Response: $response"

# 提取URL
url=$(echo "$response" | grep -o 'https://shz.al/[a-zA-Z0-9]*' | head -1)
if [ -n "$url" ]; then
    echo "Download URL: $url"
else
    echo "Failed to get URL, raw response: $response"
fi
