import glob, json, subprocess, time
import numpy as np, sherpa_onnx
r = sherpa_onnx.OfflineRecognizer.from_transducer(encoder="m/encoder.int8.onnx", decoder="m/decoder.onnx", joiner="m/joiner.onnx", tokens="m/tokens.txt", num_threads=4)
def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"], capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32)
def run(a, chunk, offset):
    words = []
    for st in range(0, len(a), chunk):
        s = r.create_stream()
        s.accept_waveform(16000, a[st:st + chunk])
        r.decode_stream(s)
        res = s.result
        cur, at = "", 0.0
        for tok, ts in zip(res.tokens, res.timestamps):
            t = offset + st / 16000 + ts
            if tok.startswith(" ") or tok.startswith("▁") or not cur:
                if cur:
                    words.append([round(at, 3), cur])
                cur, at = "", t
            cur += tok.lstrip(" ▁")
        if cur:
            words.append([round(at, 3), cur])
    return words
out = {}
for p in sorted(glob.glob("out/*.mp3")):
    a = load(p)
    t = time.time()
    w = run(a, 20 * 16000, 0.0)
    out[p] = {"dur": len(a) / 16000, "words": w}
    print(p, round(len(a) / 16000), "s", len(w), "words", round(time.time() - t, 1), "s")
    print(" ".join(x[1] for x in w[:120]))
    np.save(p + ".npy", a[: 16000 * 1200])
json.dump(out, open("out/words.json", "w"), ensure_ascii=False)
