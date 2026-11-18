import json
import os
import sys
import urllib.error
import urllib.request

key = os.environ["ELEVENLABS_API_KEY"]
plan = json.load(open(sys.argv[1]))
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
voice_id = plan.get("voice_id", "MYw0upsxdtxs1n97djly")


def call(url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, url.split("?")[0], e.read()[:400].decode("utf-8", "replace"))
        raise


said = [(i, s["say"]) for i, s in enumerate(plan["scenes"], 1) if s.get("say")]
for k, (i, text) in enumerate(said):
    body = {
        "text": text,
        "model_id": plan.get("voice_model", "eleven_multilingual_v2"),
        "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.35, "use_speaker_boost": True},
    }
    if k > 0:
        body["previous_text"] = said[k - 1][1]
    if k + 1 < len(said):
        body["next_text"] = said[k + 1][1]
    open(f"{out}/line{i:02d}.mp3", "wb").write(call(f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128", body))
    print("line", i, text)

kit = {
    "whoosh": ("fast soft cinematic whoosh transition, clean, short", 1.0),
    "pop": ("soft ui pop click, modern app interface, subtle", 0.5),
    "swipe": ("smooth paper page swipe, light, short", 0.8),
    "rise": ("short airy riser, soft sparkle, upbeat", 1.5),
}
for name, (prompt, sec) in kit.items():
    p = f"{out}/sfx_{name}.mp3"
    if os.path.exists(p):
        continue
    try:
        open(p, "wb").write(call("https://api.elevenlabs.io/v1/sound-generation", {"text": prompt, "duration_seconds": sec, "prompt_influence": 0.6}))
        print("sfx", name)
    except Exception as e:
        print("sfx fail", name, e)

music = f"{out}/music.mp3"
if not os.path.exists(music) or plan.get("music"):
    prompt = plan.get("music") or "Upbeat modern lo-fi pop instrumental for a short app promo video, warm keys, soft punchy drums, light bass, positive and cozy, no vocals, steady 105 bpm"
    try:
        open(music, "wb").write(call("https://api.elevenlabs.io/v1/music", {"prompt": prompt, "music_length_ms": int(plan.get("music_ms", 75000))}))
        print("music")
    except Exception as e:
        print("music fail", e)
