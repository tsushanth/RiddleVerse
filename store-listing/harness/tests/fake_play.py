"""In-memory stand-in for the androidpublisher v3 client (only the methods the harness is allowed to use)."""
from copy import deepcopy


class Req:
    def __init__(self, fn):
        self.fn = fn

    def execute(self):
        return self.fn()


class FakePlay:
    def __init__(self, listings, images=None, corrupt_other_locale=False):
        self.live = {"listings": deepcopy(listings), "images": deepcopy(images or {})}
        self.edits_ = {}
        self.calls = []
        self.committed = 0
        self.n = 0
        self.corrupt = corrupt_other_locale

    def edits(self):
        return Edits(self)


class Edits:
    def __init__(self, p):
        self.p = p

    def _rec(self, name):
        self.p.calls.append(name)

    def insert(self, packageName, body):
        def go():
            self._rec("edits.insert")
            self.p.n += 1
            eid = f"e{self.p.n}"
            self.p.edits_[eid] = deepcopy(self.p.live)
            return {"id": eid}
        return Req(go)

    def delete(self, packageName, editId):
        def go():
            self._rec("edits.delete")
            self.p.edits_.pop(editId)
            return {}
        return Req(go)

    def validate(self, packageName, editId):
        def go():
            self._rec("edits.validate")
            return {"id": editId}
        return Req(go)

    def commit(self, packageName, editId):
        def go():
            self._rec("edits.commit")
            self.p.live = self.p.edits_.pop(editId)
            self.p.committed += 1
            return {"id": editId}
        return Req(go)

    def listings(self):
        return Listings(self.p, self)

    def images(self):
        return Images(self.p, self)


class Listings:
    def __init__(self, p, e):
        self.p, self.e = p, e

    def list(self, packageName, editId):
        def go():
            self.e._rec("edits.listings.list")
            return deepcopy({"listings": list(self.p.edits_[editId]["listings"].values())})
        return Req(go)

    def update(self, packageName, editId, language, body):
        def go():
            self.e._rec("edits.listings.update")
            self.p.edits_[editId]["listings"][language] = deepcopy(body)
            if self.p.corrupt:
                for l, b in self.p.edits_[editId]["listings"].items():
                    if l != language:
                        b["title"] = b["title"] + " (oops)"
            return body
        return Req(go)


class Images:
    def __init__(self, p, e):
        self.p, self.e = p, e

    def list(self, packageName, editId, language, imageType):
        def go():
            self.e._rec("edits.images.list")
            return deepcopy({"images": list(self.p.edits_[editId]["images"].get(language, {}).get(imageType, []))})
        return Req(go)

    def deleteall(self, packageName, editId, language, imageType):
        def go():
            self.e._rec("edits.images.deleteall")
            self.p.edits_[editId]["images"].setdefault(language, {})[imageType] = []
            return {}
        return Req(go)

    def upload(self, packageName, editId, language, imageType, media_body):
        def go():
            self.e._rec("edits.images.upload")
            self.p.edits_[editId]["images"].setdefault(language, {}).setdefault(imageType, []).append(
                {"id": f"img{len(self.p.edits_[editId]['images'][language][imageType])}", "url": "x", "sha256": "n/a"})
            return {}
        return Req(go)
