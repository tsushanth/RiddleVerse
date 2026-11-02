"""Regenerates golden.json from the Android/Python reference normaliser (riddle_norm.py, a verbatim copy of
scripts/riddles/riddle_norm.py on the Android riddle-pack branch). Run: python3 gen_golden.py
The JS port (src/components/puzzles/riddles/riddleMatcher.js) is asserted against this file by riddles.test.mjs."""
import json, os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)
import riddle_norm as N

bank = json.load(open(os.path.join(here, '..', '..', '..', 'shared', 'riddles_v1.json'), encoding='utf-8'))['riddles']
answers = [(r['id'], f) for r in bank for f in [r['a']] + r.get('alt', [])]

EDGE = ["  CLOCK!! ", "Cl-ock", "café", "Café", "naïve", "Zoë", "it's a clock", "It is the clock.", "I think it is a clock",
        "the", "a", "", "   ", "!!!", "R&D", "rock & roll", "O’Brien", "don't", "tea pot", "deck of card", "knives", "leaves",
        "glasses", "cities", "boxes", "buses", "bus", "gas", "this", "that is it", "answer: maybe the moon", "1943", "13",
        "thirteen", "the letter M", "İstanbul", "ß", "ÀÉÎÕÜ", "Ünïcödé", "x" * 3, "a-b-c", "\ttab\nnewline", "日本語", "emoji 🙂 ok",
        # plural-rule boundaries (length thresholds of each suffix rule)
        "eves", "ves", "wolves", "calves", "ties", "pies", "lies", "cities", "yes", "ses", "us", "as", "is", "ss", "class", "bus", "buses",
        "boxes", "foxes", "ox", "oxes", "shelves", "knives", "wives", "lives", "hives"]

def derived(a):
    return [a.upper() + "!!", "the " + a, "it is a " + a, a + "s", a + "es", a.replace(" ", "")]

norm_cases = []
for s in sorted({f for _, f in answers} | set(EDGE)):
    toks = N.norm(s)
    norm_cases.append({"s": s, "tokens": toks, "variants": [sorted(N.variants(t)) for t in toks], "key": N.key(s)})

pairs = []
for i, (rid, f) in enumerate(answers):
    other = answers[(i * 7 + 13) % len(answers)][1]
    for d in derived(f):
        pairs.append({"a": d, "b": f, "same": N.same(d, f)})
    pairs.append({"a": other, "b": f, "same": N.same(other, f)})
for a in EDGE:
    for b in ("clock", "piano", "deck of cards", "the letter M", "13"):
        pairs.append({"a": a, "b": b, "same": N.same(a, b)})

out = {"normCases": norm_cases, "pairs": pairs, "accepted": [f for _, f in answers]}
json.dump(out, open(os.path.join(here, 'golden.json'), 'w', encoding='utf-8'), ensure_ascii=True, separators=(',', ':'))
print(len(norm_cases), "norm cases,", len(pairs), "pairs,", len(answers), "accepted answers")
