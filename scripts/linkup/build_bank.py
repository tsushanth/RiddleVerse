#!/usr/bin/env python3
"""Builds android/app/src/main/assets/linkup/puzzles.json from scripts/linkup/src/*.txt.

Source format (puzzles separated by a blank line, '#' starts a comment):

    1 | Title of the easiest group | WORD WORD WORD WORD
    2 | ...
    3 | ...
    4 | Title of the trickiest group | WORD WORD WORD WORD
    x WORD > 3        WORD (listed in its home group) also plausibly fits group 3 (a deliberate red herring)

The leading digit is the tier (1 easy .. 4 tricky); every puzzle has exactly one group per tier.
Run validate_linkup.py afterwards.
"""
import json, sys, glob, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets", "linkup", "puzzles.json")


def parse(text, origin):
    puzzles, cur, line_no = [], None, 0
    def flush():
        nonlocal cur
        if cur is not None:
            puzzles.append(cur)
        cur = None
    for raw in text.splitlines():
        line_no += 1
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            flush()
            continue
        if cur is None:
            cur = {"groups": [], "ambiguity": [], "_src": f"{origin}:{line_no}"}
        if line.startswith("x "):
            body = line[2:].strip()
            word, _, tiers = body.partition(">")
            fits = [int(t) for t in tiers.replace(",", " ").split()]
            cur["ambiguity"].append({"word": word.strip().upper(), "fits": fits})
        else:
            tier, title, words = [p.strip() for p in line.split("|")]
            cur["groups"].append({"tier": int(tier), "title": title, "words": [w.upper() for w in words.split()]})
    flush()
    return puzzles


def main():
    puzzles = []
    for path in sorted(glob.glob(os.path.join(HERE, "src", "*.txt"))):
        with open(path, encoding="utf-8") as f:
            puzzles += parse(f.read(), os.path.basename(path))
    out = []
    for i, p in enumerate(puzzles, 1):
        p["groups"].sort(key=lambda g: g["tier"])
        out.append({"id": f"lu{i:03d}", "groups": p["groups"], "ambiguity": p["ambiguity"]})
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "puzzles": out}, f, ensure_ascii=True, indent=0, separators=(",", ":"))
        f.write("\n")
    print(f"wrote {len(out)} puzzles -> {os.path.normpath(OUT)}")


if __name__ == "__main__":
    sys.exit(main())
