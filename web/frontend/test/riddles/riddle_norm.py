"""Answer normalisation shared by build_riddles.py and validate_riddles.py.
Mirrors RiddleMatcher.kt (app/src/main/java/com/kreativekoala/riddleverse/RiddleMatcher.kt).
"""
import re, unicodedata

FILLERS = {"a", "an", "the", "it", "its", "is", "are", "they", "theyre", "i", "think", "maybe", "this", "that", "answer"}
STOP = FILLERS | {"what", "am", "i", "you", "me", "my", "of", "to", "and", "in", "on", "at", "for", "with", "but", "or", "no", "not",
                  "have", "has", "can", "do", "does", "your", "we", "us", "our", "who", "how", "when", "than", "so", "as", "by", "from"}


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.replace("&", " and ").replace("'", "").replace("’", "")
    toks = re.sub(r"[^a-z0-9]+", " ", s).split()
    while len(toks) > 1 and toks[0] in FILLERS:
        toks = toks[1:]
    return toks


def variants(t):
    v = {t}
    if len(t) > 4 and t.endswith("ies"):
        v.add(t[:-3] + "y")
    if len(t) > 4 and t.endswith("ves"):
        v.add(t[:-3] + "f"); v.add(t[:-3] + "fe")
    if len(t) > 3 and t.endswith("es"):
        v.add(t[:-2])
    if len(t) > 2 and t.endswith("s") and not t.endswith("ss"):
        v.add(t[:-1])
    return v


def key(s):
    """Canonical key used for duplicate detection: singularised tokens joined."""
    return " ".join(sorted(min(variants(t), key=len) for t in norm(s)))


def contains_phrase(text_toks, ans_toks):
    n = len(ans_toks)
    if n == 0:
        return False
    avs = [variants(t) for t in ans_toks]
    for i in range(len(text_toks) - n + 1):
        if all(variants(text_toks[i + j]) & avs[j] for j in range(n)):
            return True
    return False


def same(a, b):
    """True when the app would treat the two answer strings as the same (token-wise variant intersection)."""
    ta, tb = norm(a), norm(b)
    return len(ta) == len(tb) and len(ta) > 0 and all(variants(x) & variants(y) for x, y in zip(ta, tb))
