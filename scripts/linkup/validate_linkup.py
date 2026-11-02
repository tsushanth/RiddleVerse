#!/usr/bin/env python3
"""Validates the Linkup puzzle bank (android/app/src/main/assets/linkup/puzzles.json).

Errors (exit 1): not 16 unique words; not 4 groups of 4; tiers not exactly {1,2,3,4}; word in more than one group;
word equals a category title; word not 3-10 letters A-Z; duplicate puzzle ids / identical 16-word sets / identical
groups anywhere in the bank; ambiguity entries that name unknown words or tiers, or that target the word's own
group; more or fewer than ONE valid partition once the declared ambiguity list is taken into account (a puzzle must
have a unique solution); words on the content blocklist. Warnings: puzzle with no declared red herring.
Usage: validate_linkup.py [path/to/puzzles.json]
"""
import json, os, re, sys, itertools

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets", "linkup", "puzzles.json")
WORD_RE = re.compile(r"^[A-Z]{3,10}$")
# Family-friendly guard: reject anything that is a slur/profanity/sexual term or a brand-style proper noun we flagged.
BLOCK = set("""ASS ARSE DAMN HELL CRAP SEX SEXY PORN NUDE NAKED RAPE DRUG DRUGS COCAINE HEROIN WHORE SLUT BASTARD BITCH
DICK COCK PISS SHIT FUCK TITS BOOB BOOBS PENIS VAGINA KILL MURDER SUICIDE NAZI HITLER GUN GUNS BOMB TERROR""".split())



ANIMALS = "CAT DOG RAT BAT ANT APE COW EEL OWL HEN PIG RAM BEAR LAMB FOX ASP LION ELK EMU GNU YAK TOAD".split()
COLOURS = "RED TAN TEAL GOLD BLUE LIME JADE PLUM AQUA ROSE GREY PINK".split()
NUMBERS = "ONE TWO TEN SIX NINE EIGHT FOUR FIVE".split()
BODY = "EAR ARM LEG HIP LIP CHIN SHIN HAIR HEAD NAIL GUM SKIN FOOT HAND NECK HEART".split()
METALS = "TIN IRON LEAD GOLD ZINC".split()
ROMAN, TOPROW = set("IVXLCDM"), set("QWERTYUIOP")


def pairs(w):
    return sum(1 for a, b in zip(w, w[1:]) if a == b)


def wordplay_errors(title, words):
    """Mechanically checks titles whose rule can be tested by code. Returns a list of offending words."""
    t = title.lower()
    bad = []
    for w in words:
        ok = True
        if t.startswith("palindrom"): ok = w == w[::-1]
        elif t.startswith("anagrams"): ok = sorted(w) == sorted(words[0])
        elif t.startswith("only roman"): ok = set(w) <= ROMAN
        elif t.startswith("typed on the top row"): ok = set(w) <= TOPROW
        elif t.startswith("letters in alphabetical"): ok = list(w) == sorted(w)
        elif t == "silent k": ok = w.startswith("KN")
        elif t == "silent w": ok = w.startswith("WR")
        elif t == "silent g": ok = "GN" in w
        elif t == "silent b": ok = "MB" in w or "BT" in w
        elif t == "silent p": ok = w.startswith("PS") or w.startswith("PN")
        elif t.startswith("start with a silent letter"): ok = w[:2] in ("KN", "GN", "WR", "PS")
        elif t.startswith("hide an animal"): ok = any(a in w and a != w for a in ANIMALS)
        elif t.startswith("hide a colour"): ok = any(a in w and a != w for a in COLOURS)
        elif t.startswith("hide a number"): ok = any(a in w and a != w for a in NUMBERS)
        elif t.startswith("hide a body part"): ok = any(a in w and a != w for a in BODY)
        elif t.startswith("start with a metal"): ok = any(w.startswith(a) and a != w for a in METALS)
        elif t.startswith("start with a body part"): ok = any(w.startswith(a) and a != w for a in BODY)
        elif t.startswith("two pairs of double"): ok = pairs(w) >= 2
        elif t.startswith("spell a word backwards"): ok = w != w[::-1]
        if not ok: bad.append(w)
    return bad


def count_partitions(fit, limit=3):
    """fit: word -> set(tiers it plausibly fits). Counts ways to split the words into four groups of 4 (one per tier)."""
    words = sorted(fit)
    sols = 0
    cap = {1: 4, 2: 4, 3: 4, 4: 4}

    def rec(i):
        nonlocal sols
        if sols >= limit:
            return
        if i == len(words):
            if all(v == 0 for v in cap.values()):
                sols += 1
            return
        for t in sorted(fit[words[i]]):
            if cap[t] > 0:
                cap[t] -= 1
                rec(i + 1)
                cap[t] += 1
    rec(0)
    return sols


def validate(data):
    errors, warnings = [], []
    puzzles = data.get("puzzles", [])
    ids, wordsets, groupsets = {}, {}, {}
    for pz in puzzles:
        pid = pz.get("id", "?")
        def err(m): errors.append(f"{pid}: {m}")
        if pid in ids: err("duplicate puzzle id")
        ids[pid] = 1
        groups = pz.get("groups", [])
        if len(groups) != 4: err(f"{len(groups)} groups, need 4"); continue
        if sorted(g.get("tier") for g in groups) != [1, 2, 3, 4]: err("tiers must be exactly 1,2,3,4")
        titles = {g["title"].strip().lower() for g in groups}
        if len(titles) != 4: err("duplicate category titles in puzzle")
        home, allw = {}, []
        for g in groups:
            ws = g.get("words", [])
            if len(ws) != 4: err(f"group '{g['title']}' has {len(ws)} words, need 4")
            if not g["title"].strip(): err("empty title")
            for w in ws:
                allw.append(w)
                if not WORD_RE.match(w): err(f"word '{w}' must be 3-10 letters A-Z")
                if w in BLOCK: err(f"word '{w}' blocked")
                if w.lower() in titles: err(f"word '{w}' equals a category title")
                if w in home: err(f"word '{w}' appears in more than one group ('{home[w]}' and tier {g['tier']})")
                home[w] = g["tier"]
            for bw in wordplay_errors(g["title"], ws): err(f"word '{bw}' breaks the rule of '{g['title']}'")
        if len(allw) != 16 or len(set(allw)) != 16: err(f"needs 16 unique words, has {len(set(allw))} unique of {len(allw)}")
        # ambiguity list + uniqueness of the solution
        fit = {w: {t} for w, t in home.items()}
        for a in pz.get("ambiguity", []):
            w = a.get("word")
            if w not in home: err(f"ambiguity word '{w}' is not in the puzzle"); continue
            for t in a.get("fits", []):
                if t not in (1, 2, 3, 4): err(f"ambiguity '{w}' names unknown tier {t}")
                elif t == home[w]: err(f"ambiguity '{w}' targets its own group")
                else: fit[w].add(t)
        if not pz.get("ambiguity"): warnings.append(f"{pid}: no declared red herring")
        if len(home) == 16:
            n = count_partitions(fit)
            if n != 1: err(f"{n} valid partitions under the declared ambiguity list (need exactly 1)")
        key = frozenset(allw)
        if key in wordsets: err(f"identical 16-word set to {wordsets[key]}")
        wordsets[key] = pid
        for g in groups:
            gk = frozenset(g.get("words", []))
            if gk in groupsets: err(f"group '{g['title']}' duplicates a group in {groupsets[gk]}")
            groupsets[gk] = pid
    return errors, warnings, puzzles


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    errors, warnings, puzzles = validate(data)
    tiers_words = sum(len(g["words"]) for p in puzzles for g in p["groups"])
    vocab = {w for p in puzzles for g in p["groups"] for w in g["words"]}
    print(f"puzzles={len(puzzles)} groups={sum(len(p['groups']) for p in puzzles)} tiles={tiers_words} distinct_words={len(vocab)}")
    print(f"red herrings declared={sum(len(p.get('ambiguity', [])) for p in puzzles)}")
    for w in warnings[:20]: print("WARN", w)
    for e in errors: print("ERROR", e)
    print("OK" if not errors else f"FAILED: {len(errors)} errors")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
