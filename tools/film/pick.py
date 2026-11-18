import json
import sys

models = json.load(open(sys.argv[1]))
form = sys.argv[2].upper() if len(sys.argv) > 2 else "PHYSICAL"
prefer = ["husky", "shiba", "akita", "felix", "tangorpro", "lynx", "cheetah", "panther", "bluejay", "raven", "oriole", "redfin"]


def versions(m):
    out = []
    for v in m.get("supportedVersionIds", []):
        try:
            out.append(int(v))
        except ValueError:
            pass
    return sorted(out)


pool = [m for m in models if m.get("form") == form and versions(m) and not any("deprecated" in t for t in m.get("tags", []))]
for m in sorted(pool, key=lambda m: (m.get("manufacturer", ""), m["id"])):
    print(m["id"], m.get("name"), versions(m), m.get("tags", []), file=sys.stderr)
pool.sort(key=lambda m: (prefer.index(m["id"]) if m["id"] in prefer else 99, -max(versions(m))))
if not pool:
    sys.exit("no devices")
best = pool[0]
v = [x for x in versions(best) if x <= 35] or versions(best)
print(f"{best['id']},{max(v)}")
