import json, os, sys, urllib.request

key = os.environ.get("ELEVENLABS_API_KEY", "")
out = sys.argv[1]
text = sys.argv[2]
os.makedirs(out, exist_ok=True)

def call(url, body=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body else None, headers={"xi-api-key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

voices = json.loads(call("https://api.elevenlabs.io/v1/voices"))["voices"]
with open(f"{out}/voices.txt", "w") as f:
    for v in voices:
        labels = v.get("labels") or {}
        f.write(f'{v["voice_id"]}\t{v["name"]}\t{v.get("category")}\t{labels.get("gender","")}\t{labels.get("accent","")}\t{labels.get("description","")}\t{labels.get("use_case","")}\n')
own = [v for v in voices if v.get("category") != "premade"]
premade = [v for v in voices if v.get("category") == "premade"]
picks = (own + premade)[: int(os.environ.get("VOICE_COUNT", "5"))]
for i, v in enumerate(picks, 1):
    try:
        audio = call(f'https://api.elevenlabs.io/v1/text-to-speech/{v["voice_id"]}?output_format=mp3_44100_128', {
            "text": text,
            "model_id": os.environ.get("VOICE_MODEL", "eleven_multilingual_v2"),
            "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.35, "use_speaker_boost": True},
        })
        name = "".join(c if c.isalnum() else "_" for c in v["name"])[:30]
        open(f"{out}/{i:02d}_{name}.mp3", "wb").write(audio)
        print("ok", v["name"])
    except Exception as e:
        print("fail", v["name"], e)
