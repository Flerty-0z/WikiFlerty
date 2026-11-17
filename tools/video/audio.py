import json, os, sys, urllib.request

key = os.environ["ELEVENLABS_API_KEY"]
out = sys.argv[1]
os.makedirs(out, exist_ok=True)


def call(url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


def voice(lines_file, voice_id):
    lines = [l.strip() for l in open(lines_file, encoding="utf-8") if l.strip()]
    prev = None
    for i, line in enumerate(lines, 1):
        body = {
            "text": line,
            "model_id": os.environ.get("VOICE_MODEL", "eleven_multilingual_v2"),
            "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.35, "use_speaker_boost": True},
        }
        if prev:
            body["previous_text"] = prev
        if i < len(lines):
            body["next_text"] = lines[i]
        open(f"{out}/line{i:02d}.mp3", "wb").write(call(f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_192", body))
        prev = line
        print("voice", i)


def sfx(name, prompt, seconds):
    try:
        data = call("https://api.elevenlabs.io/v1/sound-generation", {"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.6})
        open(f"{out}/sfx_{name}.mp3", "wb").write(data)
        print("sfx", name)
    except Exception as e:
        print("sfx fail", name, e)


def music(prompt, ms):
    try:
        data = call("https://api.elevenlabs.io/v1/music", {"prompt": prompt, "music_length_ms": ms})
        open(f"{out}/music.mp3", "wb").write(data)
        print("music ok")
    except Exception as e:
        print("music fail", e, getattr(e, "read", lambda: b"")()[:300])


if __name__ == "__main__":
    what = sys.argv[2:]
    if "voice" in what:
        voice(os.environ.get("LINES", "tools/video/lines.txt"), os.environ.get("VOICE_ID", "MYw0upsxdtxs1n97djly"))
    if "sfx" in what:
        sfx("whoosh", "fast soft cinematic whoosh transition, clean, short", 1.0)
        sfx("pop", "soft ui pop click, modern app interface, subtle", 0.5)
        sfx("swipe", "smooth paper page swipe, light, short", 0.8)
        sfx("tick", "gentle digital tick, minimal ui sound", 0.4)
        sfx("rise", "short airy riser, soft sparkle, upbeat", 1.5)
    if "music" in what:
        music("Upbeat modern lo-fi pop instrumental for a short app promo video, warm keys, soft punchy drums, light bass, positive and cozy, no vocals, steady 105 bpm", int(os.environ.get("MUSIC_MS", "75000")))
