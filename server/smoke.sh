#!/usr/bin/env bash
set -euo pipefail
host="$1"
cert="$2"
base="https://wikiflerty-sync"
call() {
  curl -sS --max-time 30 --cacert "$cert" --resolve "wikiflerty-sync:443:$host" -H "Content-Type: application/json" ${TOKEN:+-H "Authorization: Bearer $TOKEN"} -d "$2" "$base$1"
}
curl -sS --max-time 15 --cacert "$cert" --resolve "wikiflerty-sync:443:$host" "$base/v1/health" | grep -q '"ok":true'
echo "health ok"
login="smoke_$RANDOM$RANDOM"
out=$(call /v1/register "{\"login\":\"$login\",\"password\":\"smoke-pass-1\",\"sync\":\"SMOKE-TESTS-ABCDE\"}")
TOKEN=$(echo "$out" | jq -r .token)
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || { echo "register failed: $(echo "$out" | jq -r .message)"; exit 1; }
echo "register ok"
printf 'PK\003\004smoke-book-content' > book.epub
size=$(stat -c %s book.epub)
fp=$(sha256sum book.epub | cut -d' ' -f1)
url=$(call /v1/books/upload "{\"fp\":\"$fp\",\"size\":$size,\"name\":\"Smoke\",\"ext\":\"epub\"}" | jq -r .url)
code=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT --data-binary @book.epub "$url")
[ "$code" = 200 ] || { echo "upload failed: $code"; exit 1; }
used=$(call /v1/books/commit "{\"fp\":\"$fp\"}" | jq -r .used)
[ "$used" = "$size" ] || { echo "commit failed"; exit 1; }
echo "upload ok"
url=$(call /v1/books/url "{\"fp\":\"$fp\"}" | jq -r .url)
curl -sS -o back.epub "$url"
cmp -s book.epub back.epub || { echo "download mismatch"; exit 1; }
echo "download ok"
pay=$(call /v1/me '{}' | jq -r .pay)
echo "pay enabled: $pay"
if [ "$pay" = true ]; then
  link=$(call /v1/pay/create '{"plan":"plus"}' | jq -r .url)
  echo "pay link: ${link%%\?*}"
  page=$(curl -sS -L --max-time 30 -A "Mozilla/5.0 (Linux; Android 14)" "$link" || true)
  echo "pay page title: $(echo "$page" | grep -o '<title>[^<]*' | head -1 | sed 's/<title>//')"
  echo "$page" | grep -oiE '(ошибк|неверн|error)[^<"]{0,120}' | head -5 | sed 's/^/pay page: /' || true
fi
call /v1/delete '{"password":"smoke-pass-1"}' | jq -e .ok > /dev/null
echo "cleanup ok"
