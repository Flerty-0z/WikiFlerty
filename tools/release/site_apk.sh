#!/usr/bin/env bash
set -euo pipefail
version="$1"
dir="$2"
base=https://wikiflerty-books.s3.ru-3.storage.selcloud.ru
put() {
  curl -sS --fail --max-time 600 --aws-sigv4 "aws:amz:ru-3:s3" --user "$S3_ACCESS:$S3_SECRET" \
    -H "x-amz-content-sha256: UNSIGNED-PAYLOAD" -H "Content-Type: $3" -X PUT --data-binary @"$1" "$base/$2" -o /dev/null
}
x64="$dir/WikiFlerty-$version-x64.apk"
x32="$dir/WikiFlerty-$version-x32.apk"
put "$x64" "public/apk/WikiFlerty-x64.apk" application/vnd.android.package-archive
put "$x32" "public/apk/WikiFlerty-x32.apk" application/vnd.android.package-archive
printf '{"version":"%s","x64":%s,"x32":%s,"at":%s}' "$version" "$(stat -c %s "$x64")" "$(stat -c %s "$x32")" "$(date +%s)" > info.json
put info.json "public/apk/info.json" application/json
echo "site apk $version uploaded"
