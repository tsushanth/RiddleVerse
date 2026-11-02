"""Play listing field and asset validators. Limits from Google Play Console help (checked 2026-11-01):
title 30, short description 80, full description 4000 characters; icon 512x512 32-bit PNG with alpha <= 1024 KB;
feature graphic 1024x500 JPEG or 24-bit PNG without alpha; screenshots JPEG/24-bit PNG, 2..8 per device type,
each side 320..3840 px, longer side <= 2x shorter side."""
from __future__ import annotations

import os
import re
import struct
from dataclasses import dataclass, field

TITLE_MAX, SHORT_MAX, FULL_MAX = 30, 80, 4000
ICON_SIZE, ICON_MAX_BYTES = (512, 512), 1024 * 1024
FEATURE_SIZE = (1024, 500)
SHOT_MIN_SIDE, SHOT_MAX_SIDE, SHOT_MIN_COUNT, SHOT_MAX_COUNT = 320, 3840, 2, 8
SHOT_MAX_BYTES = 8 * 1024 * 1024  # Console upload limit; the help page does not state one, so this is conservative
IMAGE_TYPES = ("icon", "featureGraphic", "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots")
SCREENSHOT_TYPES = ("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots")
LOCALE_RE = re.compile(r"^[a-z]{2,3}(-[A-Za-z0-9]{2,4})?(-[A-Za-z0-9]{2,4})?$")
ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,62}$")

_EMOJI_RE = re.compile("[\U0001F300-\U0001FAFF☀-➿\U0001F000-\U0001F2FF]")


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors

    def merge(self, other: "Report", prefix: str = "") -> None:
        self.errors += [prefix + e for e in other.errors]
        self.warnings += [prefix + w for w in other.warnings]


def check_text(title=None, short=None, full=None) -> Report:
    r = Report()
    if title is not None:
        if not title.strip():
            r.errors.append("title is empty")
        if len(title) > TITLE_MAX:
            r.errors.append(f"title is {len(title)} chars, max {TITLE_MAX}")
        if _EMOJI_RE.search(title):
            r.warnings.append("title contains emoji (Play metadata policy rejects this)")
        if len(title) > 5 and title.upper() == title and any(c.isalpha() for c in title):
            r.warnings.append("title is ALL CAPS (policy)")
        if re.search(r"#1|\bbest\b|\btop\b|\bfree\b|\bnew\b", title, re.I):
            r.warnings.append("title contains a promotional word (#1/best/top/free/new): policy risk")
    if short is not None:
        if not short.strip():
            r.errors.append("short description is empty")
        if len(short) > SHORT_MAX:
            r.errors.append(f"short description is {len(short)} chars, max {SHORT_MAX}")
        if _EMOJI_RE.search(short):
            r.warnings.append("short description contains emoji")
    if full is not None:
        if not full.strip():
            r.errors.append("full description is empty")
        if len(full) > FULL_MAX:
            r.errors.append(f"full description is {len(full)} chars, max {FULL_MAX}")
    return r


def read_image_info(path: str) -> dict:
    """Format, size and alpha of a PNG or JPEG from its bytes (stdlib only)."""
    with open(path, "rb") as f:
        data = f.read()
    size = len(data)
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        w, h, depth, ctype = struct.unpack(">IIBB", data[16:26])
        alpha = ctype in (4, 6)
        pos = 8
        while pos + 8 <= len(data):  # a tRNS chunk gives palette/gray/rgb images transparency too
            ln, tag = struct.unpack(">I4s", data[pos:pos + 8])
            if tag == b"tRNS":
                alpha = True
            if tag in (b"IDAT", b"IEND"):
                break
            pos += 12 + ln
        return {"format": "png", "width": w, "height": h, "alpha": alpha, "bit_depth": depth, "color_type": ctype, "bytes": size}
    if data[:2] == b"\xff\xd8":
        pos = 2
        while pos + 9 < len(data):
            if data[pos] != 0xFF:
                pos += 1
                continue
            marker = data[pos + 1]
            if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
                pos += 2
                continue
            ln = struct.unpack(">H", data[pos + 2:pos + 4])[0]
            if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                h, w = struct.unpack(">HH", data[pos + 5:pos + 9])
                return {"format": "jpeg", "width": w, "height": h, "alpha": False, "bytes": size}
            pos += 2 + ln
        raise ValueError(f"{path}: JPEG without a size marker")
    raise ValueError(f"{path}: not a PNG or JPEG")


def check_image(role: str, path: str) -> Report:
    r = Report()
    if role not in IMAGE_TYPES:
        r.errors.append(f"unknown image role {role!r}")
        return r
    if not os.path.isfile(path):
        r.errors.append(f"{path}: file not found")
        return r
    try:
        i = read_image_info(path)
    except ValueError as e:
        r.errors.append(str(e))
        return r
    w, h = i["width"], i["height"]
    if role == "icon":
        if i["format"] != "png" or not i["alpha"] or i.get("bit_depth") != 8:
            r.errors.append(f"{path}: icon must be a 32-bit PNG with alpha")
        if (w, h) != ICON_SIZE:
            r.errors.append(f"{path}: icon is {w}x{h}, must be 512x512")
        if i["bytes"] > ICON_MAX_BYTES:
            r.errors.append(f"{path}: icon is {i['bytes']} bytes, max {ICON_MAX_BYTES}")
    elif role == "featureGraphic":
        if i["alpha"]:
            r.errors.append(f"{path}: feature graphic must not have alpha")
        if (w, h) != FEATURE_SIZE:
            r.errors.append(f"{path}: feature graphic is {w}x{h}, must be 1024x500")
    else:
        if i["alpha"]:
            r.errors.append(f"{path}: screenshots must be JPEG or 24-bit PNG without alpha")
        if min(w, h) < SHOT_MIN_SIDE or max(w, h) > SHOT_MAX_SIDE:
            r.errors.append(f"{path}: {w}x{h} outside {SHOT_MIN_SIDE}..{SHOT_MAX_SIDE} px per side")
        if max(w, h) > 2 * min(w, h):
            r.errors.append(f"{path}: longer side is more than 2x the shorter side ({w}x{h})")
        if i["bytes"] > SHOT_MAX_BYTES:
            r.errors.append(f"{path}: {i['bytes']} bytes exceeds {SHOT_MAX_BYTES}")
        if role == "phoneScreenshots" and min(w, h) < 1080:
            r.warnings.append(f"{path}: phone screenshots are recommended at >= 1080 px on the short side")
    return r


def check_assets(assets: dict, base_dir: str = ".") -> Report:
    """assets: {role: path | [paths]} with paths relative to base_dir."""
    r = Report()
    for role, val in (assets or {}).items():
        paths = val if isinstance(val, list) else [val]
        if role in SCREENSHOT_TYPES and not (SHOT_MIN_COUNT <= len(paths) <= SHOT_MAX_COUNT):
            r.errors.append(f"{role}: {len(paths)} screenshots, need {SHOT_MIN_COUNT}..{SHOT_MAX_COUNT}")
        if role not in SCREENSHOT_TYPES and len(paths) != 1:
            r.errors.append(f"{role}: exactly one file required")
        for p in paths:
            r.merge(check_image(role, p if os.path.isabs(p) else os.path.join(base_dir, p)))
    return r
