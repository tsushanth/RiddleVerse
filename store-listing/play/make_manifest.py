#!/usr/bin/env python3
"""Regenerate manifest.json (every asset: file, dimensions, bytes, sha256, purpose, variant)."""
import json, os, hashlib
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
V = json.load(open(os.path.join(HERE, "listing/variants.json")))["variants"]
PURPOSE = {
 "01_create_prompt": "Create tab: prompt typed, 'Describe any game and AI will build it for you' (guest account, dev build 176-qa)",
 "02_ai_game_play": "AI-generated game 'Astro Dodge' being played (built from the prompt in shot 01, ~2 min)",
 "03_home": "For You home: quick actions, favorites, puzzle collections ('51 puzzle types ready')",
 "04_categories": "Quiz Categories: 72 puzzle types, filters Math/English/Memory/Visual/Logic/General, offline badges",
 "05_symmetry_play": "Symmetry puzzle mid-play (copy the pattern), level/hearts/timer header",
 "06_symmetry_win": "Symmetry 'Perfect! +119 pts, Speed Bonus' success state with streak flame",
 "07_daily_reward": "Daily Reward gift dialog over For You home",
 "08_building_game": "Game generation in progress (30%, ~1m40s remaining, Notify Me When Done)",
 "09_ai_game_intro_extra": "Astro Dodge intro card (HOW TO PLAY / LAUNCH MISSION), unused extra",
 "10_game_ready_extra": "'Your game is ready' success card with Play / Create another, unused extra",
}
def entry(path, purpose, variant, kind):
    p = os.path.join(HERE, path); im = Image.open(p)
    return {"file": path, "type": kind, "width": im.width, "height": im.height, "bytes": os.path.getsize(p),
            "sha256": hashlib.sha256(open(p, "rb").read()).hexdigest(), "purpose": purpose, "variant": variant,
            "play_upload_ready": kind in ("phoneScreenshot", "featureGraphic", "icon")}
assets = []
for f in sorted(os.listdir(os.path.join(HERE, "screenshots/original"))):
    k = f[:-4]
    assets.append(entry("screenshots/original/" + f, PURPOSE[k], "all (raw capture, NOT uploadable: 960x2142 is 2.23:1, outside Play's ratio limit)", "raw_capture"))
for vid, v in V.items():
    for i, (name, cap) in enumerate(v["screenshots"], 1):
        e = entry(f"screenshots/variant_{vid}/{i:02d}_{name.split('_',1)[1]}.png", PURPOSE[name], vid, "phoneScreenshot")
        e.update({"position": i, "caption": cap, "source_raw": f"screenshots/original/{name}.png"}); assets.append(e)
    e = entry(f"graphics/feature_graphic_variant_{vid}_1024x500.png", f"Feature graphic, variant {vid}: icon + name + '{v['feature_tagline']}' + create-screen crop", vid, "featureGraphic"); assets.append(e)
assets.append(entry("graphics/icon_512_current.png", "Current live icon (identical bytes to the live Play icon and fastlane icon.jpeg, PNG copy). Reused, no new logo.", "all", "icon"))
j = entry("graphics/icon_512_current.jpeg", "Current live icon as stored in fastlane (sha256 verified equal to Play CDN copy)", "all", "icon"); assets.append(j)
assets.append(entry("graphics/feature_graphic_current_live.jpg", "Snapshot of the CURRENT live feature graphic, for before/after only", "current", "reference"))
json.dump({"package": "com.kreativekoala.riddleverse", "locale": "en-US", "captured": "2026-11-01",
           "device": "Pixel 9 Pro (display 960x2142 @360dpi), dev build com.kreativekoala.riddleverse.dev 176.0-qa, guest account, demo-mode status bar, DND on",
           "play_rules": "phone screenshots: PNG/JPEG, 320-3840 px, ratio <= 16:9 (we use 1080x1920 = 9:16), 2-8 images; feature graphic exactly 1024x500; icon 512x512",
           "assets": assets}, open(os.path.join(HERE, "manifest.json"), "w"), indent=1)
bad = [a for a in assets if a["play_upload_ready"] and a["type"] == "phoneScreenshot" and (a["bytes"] > 8*2**20 or not (320 <= min(a["width"], a["height"]) and max(a["width"], a["height"]) <= 3840 and max(a["width"], a["height"]) <= 2*min(a["width"], a["height"])))]
big = [a["file"] for a in assets if a["bytes"] > 2*2**20]
print(len(assets), "assets; bad:", bad, "over 2MB:", big)
