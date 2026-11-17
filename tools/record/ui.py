import re, subprocess, sys, time

def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout

def nodes(xml):
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        text = re.search(r' text="([^"]*)"', n).group(1)
        desc = re.search(r'content-desc="([^"]*)"', n).group(1)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            yield text, desc, (x1 + x2) // 2, (y1 + y2) // 2

def find(label, timeout=60, exact=False):
    end = time.time() + timeout
    while time.time() < end:
        for text, desc, x, y in nodes(dump()):
            for v in (text, desc):
                if v and ((v == label) if exact else (label in v)):
                    return x, y
        time.sleep(2)
    return None

if __name__ == "__main__":
    cmd, label = sys.argv[1], sys.argv[2]
    timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 60
    at = find(label, timeout)
    if at is None:
        print(f"not found: {label}")
        sys.exit(1)
    if cmd == "tap":
        subprocess.run(["adb", "shell", "input", "tap", str(at[0]), str(at[1])])
    print(f"{cmd} {label} at {at}")
