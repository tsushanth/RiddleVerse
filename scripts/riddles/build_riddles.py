#!/usr/bin/env python3
"""Builds android/app/src/main/assets/riddles/riddles_v1.json from easy.txt / medium.txt / hard.txt.

Source line: riddle | answer | alt1;alt2 | hint1 | hint2 | explanation
The third hint (first letter + size) is generated here so it always matches the answer.
"""
import json, re, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from riddle_norm import same

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent.parent / "android/app/src/main/assets/riddles/riddles_v1.json"
TIERS = [("easy", "e"), ("medium", "m"), ("hard", "h")]
ARTICLES = ("the ", "a ", "an ")


def strip_article(s):
    for a in ARTICLES:
        if s.startswith(a):
            return s[len(a):]
    return s


def hint3(ans):
    m = re.fullmatch(r"(?:the )?letter ([a-z])", ans.lower())
    if m:
        return "It is a single letter of the alphabet, and it is " + m.group(1).upper() + "."
    if re.fullmatch(r"[0-9]+", ans):
        n = len(ans)
        return "It is a number with %d digit%s and it starts with %s." % (n, "" if n == 1 else "s", ans[0])
    core = strip_article(ans.lower())
    words = core.split()
    first = core[0].upper()
    if len(words) > 1:
        return "It is %d words long and it starts with %s." % (len(words), first)
    return "It has %d letters and it starts with %s." % (len(core), first)


def main():
    riddles = []
    dropped = [0]
    for tier, p in TIERS:
        n = 0
        for raw in (HERE / (tier + ".txt")).read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.startswith("#"):
                continue
            parts = [x.strip() for x in raw.split("|")]
            if len(parts) != 6:
                sys.exit("bad field count (%d) in %s: %s" % (len(parts), tier, raw[:80]))
            q, a, alt, h1, h2, why = parts
            n += 1
            # the app already ignores articles and simple plurals, so drop alternates that add nothing
            kept = []
            for x in [x.strip() for x in alt.split(";") if x.strip()]:
                if same(x, a) or any(same(x, k) for k in kept):
                    dropped[0] += 1
                else:
                    kept.append(x)
            riddles.append({
                "id": "%s%03d" % (p, n), "tier": tier, "q": q, "a": a,
                "alt": kept,
                "hints": [h1, h2, hint3(a)], "why": why,
            })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    lines = ['{"version":1,"riddles":[']
    for i, r in enumerate(riddles):
        lines.append(json.dumps(r, ensure_ascii=True, separators=(",", ":")) + ("," if i < len(riddles) - 1 else ""))
    lines.append("]}")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("wrote", OUT, len(riddles), "riddles;", dropped[0], "redundant alternates dropped")


main()
