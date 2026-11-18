import json
import sys

d = json.load(open(sys.argv[1]))
print("ok", d.get("ok"), d.get("message"), "offset", d.get("offset"), "parts", d.get("parts"), d.get("starts"), f"{d.get('width')}x{d.get('height')}")
for s in d.get("steps", []):
    print(f"{s['i']:>3} {s['start'] / 1000:7.1f}-{s['end'] / 1000:7.1f} {'ok ' if s['ok'] else 'NO '} {s['do']:<8} {s.get('text', '')[:40]:<40} {s.get('rect', '')}")
