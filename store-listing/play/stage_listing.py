#!/usr/bin/env python3
"""Stage a RiddleVerse Google Play en-US listing variant (Play only).

DEFAULT IS DRY RUN: validates the variant locally and prints the exact Publisher API
calls it would make. Nothing is written to Play unless --commit is passed.
Even with --commit it only commits the edit (live listing changes immediately for
store-listing fields; it does NOT touch tracks/releases).

  python3 stage_listing.py --variant A                 # dry run, offline
  python3 stage_listing.py --variant A --diff-live     # also reads live listing via an edit that is DELETED
  python3 stage_listing.py --variant A --commit        # DO NOT run without owner approval

Service account: ~/Documents/apple_dev/cloud_credentials/summaryai-483115-a27fc5121150.json
Note: Play "store listing experiments" are Console-only (no Publisher API); this script
sets the MAIN listing, so a harness must run variants sequentially and compare, or the owner
creates the experiment in Console from these assets.
"""
import argparse, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PKG = "com.kreativekoala.riddleverse"
LANG = "en-US"
SA = os.path.expanduser("~/Documents/apple_dev/cloud_credentials/summaryai-483115-a27fc5121150.json")
LIMITS = {"title": 30, "short_description": 80, "full_description": 4000}

def load_variant(vid):
    data = json.load(open(os.path.join(HERE, "listing/variants.json")))
    v = data["variants"][vid]
    for k, m in LIMITS.items():
        assert len(v[k]) <= m, f"{k} is {len(v[k])} > {m}"
    man = {a["file"]: a for a in json.load(open(os.path.join(HERE, "manifest.json")))["assets"]}
    shots = [f"screenshots/variant_{vid}/{i:02d}_{n.split('_',1)[1]}.png" for i, (n, _) in enumerate(v["screenshots"], 1)]
    assert 2 <= len(shots) <= 8
    for s in shots + [f"graphics/feature_graphic_variant_{vid}_1024x500.png"]:
        assert os.path.exists(os.path.join(HERE, s)) and s in man, f"missing/untracked asset {s}"
    return v, shots, f"graphics/feature_graphic_variant_{vid}_1024x500.png"

def service():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    creds = service_account.Credentials.from_service_account_file(SA, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", required=True, choices=["A", "B", "C"])
    ap.add_argument("--commit", action="store_true", help="actually write to Play (default: dry run)")
    ap.add_argument("--diff-live", action="store_true", help="read live listing through a throwaway edit (deleted afterwards)")
    a = ap.parse_args()
    v, shots, fg = load_variant(a.variant)

    calls = [
      f"edits.insert(packageName={PKG})",
      f"edits.listings.patch(packageName={PKG}, language={LANG}, body={{title: {v['title']!r} ({len(v['title'])}/30), shortDescription: {v['short_description']!r} ({len(v['short_description'])}/80), fullDescription: <{len(v['full_description'])}/4000 chars>}})",
      f"edits.images.deleteall(language={LANG}, imageType=phoneScreenshots)",
    ] + [f"edits.images.upload(language={LANG}, imageType=phoneScreenshots, media={s})" for s in shots] + [
      f"edits.images.deleteall(language={LANG}, imageType=featureGraphic)",
      f"edits.images.upload(language={LANG}, imageType=featureGraphic, media={fg})",
      "# icon: unchanged (current live icon is already 512x512 and byte-identical to the repo copy)",
      "# tracks/releases: NOT touched",
      f"edits.commit(packageName={PKG})   <-- only with --commit; otherwise: edits.delete (nothing persisted)",
    ]
    print(("COMMIT MODE" if a.commit else "DRY RUN (nothing will be written)") + f" - variant {a.variant}: {v['name']}")
    print("\n".join(calls))

    if a.diff_live or a.commit:
        svc = service()
        eid = svc.edits().insert(packageName=PKG, body={}).execute()["id"]
        try:
            live = svc.edits().listings().get(packageName=PKG, editId=eid, language=LANG).execute()
            for k, lk in (("title", "title"), ("short_description", "shortDescription"), ("full_description", "fullDescription")):
                print(f"\n[{k}] live: {live.get(lk, '')[:100]!r}{'...' if len(live.get(lk,''))>100 else ''}\n{' '*(len(k)+2)} new : {v[k][:100]!r}{'...' if len(v[k])>100 else ''}")
            if not a.commit:
                return
            from googleapiclient.http import MediaFileUpload
            svc.edits().listings().patch(packageName=PKG, editId=eid, language=LANG, body={
                "title": v["title"], "shortDescription": v["short_description"], "fullDescription": v["full_description"]}).execute()
            svc.edits().images().deleteall(packageName=PKG, editId=eid, language=LANG, imageType="phoneScreenshots").execute()
            for s in shots:
                svc.edits().images().upload(packageName=PKG, editId=eid, language=LANG, imageType="phoneScreenshots",
                                            media_body=MediaFileUpload(os.path.join(HERE, s), mimetype="image/png")).execute()
            svc.edits().images().deleteall(packageName=PKG, editId=eid, language=LANG, imageType="featureGraphic").execute()
            svc.edits().images().upload(packageName=PKG, editId=eid, language=LANG, imageType="featureGraphic",
                                        media_body=MediaFileUpload(os.path.join(HERE, fg), mimetype="image/png")).execute()
            print("committed edit", svc.edits().commit(packageName=PKG, editId=eid).execute().get("id"))
            eid = None
        finally:
            if eid:
                svc.edits().delete(packageName=PKG, editId=eid).execute(); print("\nedit deleted (nothing persisted)")

if __name__ == "__main__":
    sys.exit(main())
