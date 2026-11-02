#!/usr/bin/env python3
"""Validates android/app/src/main/assets/riddles/riddles_v1.json.

Checks: schema, tier counts, ID rules, length limits, ASCII, duplicate riddles / answers (after the same
normalisation the app uses), answer leaked in riddle or hint 1-2, hint 3 consistency, bank-wide
near-duplicate scan. Exit code 1 on any error. Usage: validate_riddles.py [--sample N] [--seed S]
"""
import difflib, json, pathlib, random, re, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from riddle_norm import FILLERS, STOP, norm, variants, same, contains_phrase

PATH = pathlib.Path(__file__).resolve().parent.parent.parent / "android/app/src/main/assets/riddles/riddles_v1.json"
COUNTS = {"easy": 100, "medium": 120, "hard": 80}
LIMITS = {"q": 240, "a": 40, "alt": 40, "hint": 140, "why": 160}
def main():
    argv = sys.argv[1:]
    sample = int(argv[argv.index("--sample") + 1]) if "--sample" in argv else 0
    seed = int(argv[argv.index("--seed") + 1]) if "--seed" in argv else 7
    errs = []
    def err(rid, msg): errs.append("%s: %s" % (rid, msg))

    raw = PATH.read_text(encoding="utf-8")
    if not raw.isascii():
        errs.append("file contains non-ASCII characters")
    data = json.loads(raw)
    if set(data) != {"version", "riddles"} or data["version"] != 1:
        errs.append("bad top-level keys / version")
    R = data["riddles"]
    tiers = {}
    for r in R:
        tiers[r["tier"]] = tiers.get(r["tier"], 0) + 1
    if tiers != COUNTS:
        errs.append("tier counts %s != %s" % (tiers, COUNTS))

    ids, qseen, accepted = set(), {}, []
    for r in R:
        rid = r.get("id", "?")
        if set(r) != {"id", "tier", "q", "a", "alt", "hints", "why"}:
            err(rid, "bad keys %s" % sorted(r)); continue
        if not re.fullmatch(r"[emh]\d{3}", rid) or rid[0] != r["tier"][0]:
            err(rid, "bad id for tier")
        if rid in ids: err(rid, "duplicate id")
        ids.add(rid)
        if not (isinstance(r["q"], str) and 15 <= len(r["q"]) <= LIMITS["q"]): err(rid, "q length %d" % len(r["q"]))
        if not (1 <= len(r["a"]) <= LIMITS["a"]): err(rid, "answer length")
        if not isinstance(r["alt"], list) or len(r["alt"]) > 3: err(rid, "alt must be a list of at most 3")
        for x in r["alt"]:
            if not (1 <= len(x) <= LIMITS["alt"]): err(rid, "alt length %r" % x)
        if len(r["hints"]) != 3 or any(not h or len(h) > LIMITS["hint"] for h in r["hints"]): err(rid, "hints must be 3 non-empty <=140")
        if not r["why"] or len(r["why"]) > LIMITS["why"] or "\n" in r["why"]: err(rid, "why empty/too long/multiline")
        if not r["q"].rstrip().endswith(("?", ".", "...")): err(rid, "q should end with ? or .")
        # duplicates (normalised)
        qk = " ".join(norm(r["q"]))
        if qk in qseen: err(rid, "duplicate riddle text of %s" % qseen[qk])
        qseen[qk] = rid
        forms = [r["a"]] + r["alt"]
        for i, f in enumerate(forms):
            if not norm(f): err(rid, "answer %r normalises to empty" % f); continue
            for g in forms[:i]:
                if same(f, g): err(rid, "answer/alt %r repeats another form of the same riddle (%r)" % (f, g))
            for other_id, g in accepted:
                if other_id != rid and same(f, g): err(rid, "accepted answer %r also accepted by %s (%r)" % (f, other_id, g))
        accepted.extend((rid, f) for f in forms)
        # leak
        text_toks = {"q": norm(r["q"]), "h1": norm(r["hints"][0]), "h2": norm(r["hints"][1])}
        for f in forms:
            at = norm(f)
            if all(len(t) < 3 for t in at) and not re.fullmatch(r"[0-9]+", f):
                continue  # single letters such as "m": nothing meaningful to leak
            for where, tt in text_toks.items():
                if contains_phrase(tt, at): err(rid, "answer form %r appears in %s" % (f, where))
                if len(at) == 1 and len(at[0]) >= 6 and any(at[0] in t for t in tt): err(rid, "answer form %r is a substring of a word in %s" % (f, where))
        # hint 3 sanity
        h3 = r["hints"][2].lower()
        core = " ".join(norm(r["a"]))
        letter = re.fullmatch(r"(?:the )?letter ([a-z])", r["a"].lower())
        if letter:
            if letter.group(1) not in h3: err(rid, "hint 3 should name the letter")
        elif re.fullmatch(r"[0-9]+", r["a"]):
            if "starts with %s" % r["a"][0] not in h3: err(rid, "hint 3 digit mismatch")
        else:
            c = " ".join(norm(r["a"]))
            if "starts with %s" % c[0] not in h3: err(rid, "hint 3 first letter mismatch")

    # bank-wide near duplicate scan
    sig = []
    for r in R:
        toks = [t for t in norm(r["q"]) if t not in STOP and len(t) > 2]
        sig.append((r["id"], set(toks), " ".join(norm(r["q"]))))
    near = 0
    for i in range(len(sig)):
        for j in range(i + 1, len(sig)):
            a, b = sig[i], sig[j]
            u = a[1] | b[1]
            jac = len(a[1] & b[1]) / len(u) if u else 0
            big = len(a[1]) >= 5 and len(b[1]) >= 5
            if (big and jac >= 0.6) or difflib.SequenceMatcher(None, a[2], b[2]).ratio() >= 0.85:
                err(a[0], "near duplicate of %s (jaccard %.2f)" % (b[0], jac)); near += 1

    print("riddles: %d  tiers: %s  accepted answer forms: %d  near-duplicate pairs: %d" % (len(R), tiers, len(accepted), near))
    if sample:
        rnd = random.Random(seed)
        print("\n--- spot check sample ---")
        for r in rnd.sample(R, sample):
            print("%s [%s] Q: %s\n     A: %s (alt: %s)\n     H: %s\n     W: %s" % (r["id"], r["tier"], r["q"], r["a"], ", ".join(r["alt"]) or "-", " / ".join(r["hints"]), r["why"]))
    if errs:
        print("\nERRORS (%d):" % len(errs))
        print("\n".join(errs))
        sys.exit(1)
    print("OK: no errors")


main()
