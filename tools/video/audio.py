import json, os, sys, urllib.request, urllib.error

key = os.environ["ELEVENLABS_API_KEY"]
out = sys.argv[1]
os.makedirs(out, exist_ok=True)


def call(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, url.split("?")[0], e.read()[:500].decode("utf-8", "replace"))
        raise


def info():
    try:
        sub = json.loads(call("https://api.elevenlabs.io/v1/user/subscription"))
        print("tier", sub.get("tier"), "used", sub.get("character_count"), "of", sub.get("character_limit"))
    except Exception as e:
        print("subscription unknown", e)


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
        open(f"{out}/line{i:02d}.mp3", "wb").write(call(f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128", body))
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
    info()
    if "voice" in what:
        voice(os.environ.get("LINES", "tools/video/lines.txt"), os.environ.get("VOICE_ID", "MYw0upsxdtxs1n97djly"))
    if "sfx" in what:
        sfx("whoosh", "fast soft cinematic whoosh transition, clean, short", 1.0)
        sfx("pop", "soft ui pop click, modern app interface, subtle", 0.5)
        sfx("swipe", "smooth paper page swipe, light, short", 0.8)
        sfx("tick", "gentle digital tick, minimal ui sound", 0.5)
        sfx("rise", "short airy riser, soft sparkle, upbeat", 1.5)
    if "promo" in what:
        kit = {
            "w_fast": ("very fast clean whoosh swipe transition, airy, modern promo", 0.6),
            "w_soft": ("soft smooth whoosh, gentle air movement, short transition", 0.9),
            "w_deep": ("deep cinematic swoosh with low body, short, punchy", 1.0),
            "w_tilt": ("short swish of a phone being tilted in air, subtle", 0.5),
            "pop_a": ("soft bubbly ui pop, bright, short", 0.5),
            "pop_b": ("tiny glassy pop click, modern app sticker appearing", 0.5),
            "tap": ("soft finger tap on glass screen, subtle click", 0.5),
            "page": ("quick paper page flip, crisp, short", 0.6),
            "sparkle": ("short magical sparkle shimmer, light and airy", 1.0),
            "impact": ("soft cinematic impact hit with short tail, title reveal, not harsh", 1.6),
            "riser": ("short upbeat riser build up into a drop, airy synth noise", 2.0),
            "zoom": ("smooth zoom in swoosh, rising pitch, short", 0.7),
        }
        for name, (prompt, sec) in kit.items():
            sfx(name, prompt, sec)
        music(os.environ.get("MUSIC_PROMPT", "Energetic modern electronic pop instrumental for a fast-paced mobile app promo video, punchy tight drums, bright plucky synths, warm bass, uplifting and cozy, clear sections with small builds and drops every 20 seconds, no vocals, 120 bpm"), int(os.environ.get("MUSIC_MS", "180000")))
    if "pops" in what:
        sfx("pop_a", "soft bubbly ui pop, bright, short", 0.5)
        sfx("pop_b", "tiny glassy pop click, modern app sticker appearing", 0.5)
        sfx("tap", "soft finger tap on glass screen, subtle click", 0.5)
    if "music" in what:
        music("Upbeat modern lo-fi pop instrumental for a short app promo video, warm keys, soft punchy drums, light bass, positive and cozy, no vocals, steady 105 bpm", int(os.environ.get("MUSIC_MS", "75000")))
