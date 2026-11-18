import hashlib
import json
import os
import shutil
import sys
import urllib.error
import urllib.request

plan = json.load(open(sys.argv[1]))
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
key = os.environ["ELEVENLABS_API_KEY"]
voice_id = plan.get("voice_id", "MYw0upsxdtxs1n97djly")
model = plan.get("voice_model", "eleven_multilingual_v2")


def call(url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read()[:300].decode("utf-8", "replace"))
        raise


kit = {
    "w1": ("quick airy whoosh transition, clean modern, short", 0.8),
    "w2": ("smooth soft swish swipe transition", 0.7),
    "w3": ("deep cinematic whoosh pass by, short", 1.0),
    "w4": ("light fast paper flick whoosh", 0.6),
    "p1": ("soft bubbly pop, satisfying ui sound", 0.5),
    "p2": ("tiny soft wooden tick pop, ui", 0.5),
    "zoom": ("short rising swoosh, camera zoom in", 0.8),
    "riser": ("short magical riser with soft sparkle", 1.5),
    "hit": ("soft deep cinematic impact with shimmer tail, logo reveal", 2.0),
}
if plan.get("sfx_source") == "elevenlabs":
    for name, (prompt, sec) in kit.items():
        f = os.path.join(out, f"sfx_{name}.mp3")
        if os.path.exists(f) and os.path.getsize(f) > 2000:
            continue
        try:
            data = call("https://api.elevenlabs.io/v1/sound-generation", {"text": prompt, "duration_seconds": sec, "prompt_influence": 0.55})
            open(f, "wb").write(data)
            print("sfx", name)
        except Exception as e:
            print("sfx fail", name, e)
if not plan.get("voice"):
    print("voice off")
    sys.exit(0)
said = [(k, b["say"]) for k, b in enumerate(plan["beats"], 1) if b.get("say")]
for n, (k, text) in enumerate(said):
    prev = said[n - 1][1] if n else ""
    nxt = said[n + 1][1] if n + 1 < len(said) else ""
    h = hashlib.sha1(f"{voice_id}|{model}|{prev}|{text}|{nxt}".encode()).hexdigest()[:16]
    cached = os.path.join(out, f"voice_{h}.mp3")
    if not os.path.exists(cached):
        body = {"text": text, "model_id": model, "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.35, "use_speaker_boost": True}}
        if prev:
            body["previous_text"] = prev
        if nxt:
            body["next_text"] = nxt
        open(cached, "wb").write(call(f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128", body))
        print("new", k, text)
    else:
        print("cached", k)
    shutil.copy(cached, os.path.join(out, f"beat{k:02d}.mp3"))
