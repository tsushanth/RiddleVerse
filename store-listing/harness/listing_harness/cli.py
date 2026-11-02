from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import date, timedelta

from . import analysis, catalog as C, ingest, report, scheduler as S, stager

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def paths(a):
    home = a.home or os.environ.get("LISTING_HARNESS_HOME") or os.path.join(HERE, "runtime")
    return {"home": home, "state": os.path.join(home, "state.json"), "metrics": os.path.join(home, "metrics.csv"),
            "audit": os.path.join(home, "audit.jsonl"), "snapshots": os.path.join(home, "snapshots"),
            "catalog": a.catalog or os.path.join(HERE, "catalog.json")}


def estimate_traffic(rows, today: date, policy: S.Policy, days: int = 28) -> dict:
    end = today - timedelta(days=policy.metrics_lag_days)
    v, i, n = ingest.sum_range(rows, end - timedelta(days=days - 1), end)
    if n < 7 or v == 0 or i == 0:
        return {"visitors_per_day": None, "baseline_cvr": None, "days": n}
    return {"visitors_per_day": v / n, "baseline_cvr": i / v, "days": n, "source": f"{n} days to {end}"}


def _today(a) -> date:
    return date.fromisoformat(a.today) if getattr(a, "today", None) else date.today()


def _traffic(a, P, state, rows):
    t = estimate_traffic(rows, _today(a), S.policy_of(state))
    if getattr(a, "visitors_per_day", None):
        t = {**t, "visitors_per_day": a.visitors_per_day, "source": "command line override"}
    if getattr(a, "cvr", None):
        t = {**t, "baseline_cvr": a.cvr}
    return t


def _pub(a, P, allow_writes: bool):
    sa = os.environ.get("PLAY_SA_JSON")
    if not sa or not os.path.isfile(sa):
        raise SystemExit("set PLAY_SA_JSON to the service-account key file (never commit it)")
    cat = C.load(P["catalog"]) if os.path.exists(P["catalog"]) else None
    pkg = a.package or (cat.app_package if cat else None) or "com.kreativekoala.riddleverse"
    return stager.GuardedPublisher(stager.build_service(sa), pkg, stager.Audit(P["audit"]), allow_writes=allow_writes)


def cmd_power(a, P):
    print(report.power_table(a.cvr, tuple(float(x) for x in a.visitors.split(",")), tuple(int(x) for x in a.days.split(","))))


def cmd_catalog(a, P):
    cat = C.load(P["catalog"])
    r = cat.validate()
    for e in r.errors:
        print("ERROR", e)
    for w in r.warnings:
        print("warn ", w)
    print(f"{len(cat.variants)} variants, {len(cat.ready())} ready, {'OK' if r.ok else 'INVALID'}")
    return 0 if r.ok else 1


def cmd_ingest(a, P):
    store = ingest.Store(P["metrics"])
    rows = []
    if a.csv:
        rows = ingest.parse_file(a.csv)
    else:
        dev, sa = os.environ.get("PLAY_DEVELOPER_ID"), os.environ.get("PLAY_SA_JSON")
        if not dev or not sa:
            print("ingest --gcs is not configured: set PLAY_DEVELOPER_ID (numeric id from the Play Console URL) and PLAY_SA_JSON. "
                  "Until then use --csv with the documented manual format.")
            return 3
        pkg = a.package or "com.kreativekoala.riddleverse"
        bucket = ingest.bucket_name(dev)
        for ym in a.months.split(","):
            buf = ingest.fetch_gcs_object(bucket, ingest.gcs_object_name(pkg, ym, a.breakdown), sa)
            if buf is None:
                print(f"{ym}: no file yet")
                continue
            rows += ingest.parse_rows(ingest.decode(buf))
    n = store.upsert(rows)
    for w in ingest.double_count_guard(store.all()):
        print("WARNING", w)
    store.save()
    print(f"{len(rows)} rows read, {n} new or changed, {len(store.rows)} total")


def cmd_next(a, P):
    state, cat = S.load_state(P["state"]), C.load(P["catalog"])
    rows = ingest.Store(P["metrics"]).all()
    t = _traffic(a, P, state, rows)
    act = S.next_action(state, cat, _today(a), t["visitors_per_day"], t["baseline_cvr"])
    print(json.dumps({k: v for k, v in act.items() if k != "plan"} | {"plan": act["plan"]}, indent=2, default=str))


def cmd_set_groups(a, P):
    state = S.load_state(P["state"])
    state["groups"] = {"treated": [x for x in a.treated.split(",") if x], "control": [x for x in a.control.split(",") if x]}
    S.save_state(state, P["state"])


def cmd_start_baseline(a, P):
    state, cat = S.load_state(P["state"]), C.load(P["catalog"])
    base = cat.baseline()
    if not base:
        raise SystemExit("catalog has no variant with status baseline (capture the live listing first)")
    state["baseline_variant"] = base.id
    rows = ingest.Store(P["metrics"]).all()
    t = _traffic(a, P, state, rows)
    plan = S.window_plan(S.policy_of(state), t["visitors_per_day"], t["baseline_cvr"])
    if not plan["feasible"]:
        raise SystemExit(plan.get("reason"))
    w = S.open_baseline_window(state, _today(a), plan, base.id)
    S.save_state(state, P["state"])
    print(f"baseline window {w['id']} started {w['live_on']}, {w['min_days']} days")


def cmd_mark_live(a, P):
    state = S.load_state(P["state"])
    w = S.mark_live(state, a.window, date.fromisoformat(a.date), _today(a))
    S.save_state(state, P["state"])
    print(f"{w['id']} live on {w['live_on']}; measurement {S.measure_range(w, S.policy_of(state))}")


def cmd_analyze(a, P):
    state = S.load_state(P["state"])
    rows = ingest.Store(P["metrics"]).all()
    wid = a.window or (S.open_window(state) or {}).get("id")
    if not wid:
        raise SystemExit("no open window")
    try:
        res = analysis.analyze_window(state, wid, rows, _today(a), interim=a.interim)
    except analysis.TooEarly as e:
        print(str(e))
        return 2
    print(json.dumps(res, indent=2, default=str))
    if a.interim or res["verdict"] == "blocked":
        return 0
    if res["kind"] == "baseline" and res["verdict"] == "reference":
        analysis.attach_post_control(state, res)
    cat = C.load(P["catalog"]) if os.path.exists(P["catalog"]) else None
    S.apply_result(state, wid, res, cat)
    S.save_state(state, P["state"])
    if cat:
        C.save(cat, P["catalog"])


def cmd_snapshot(a, P):
    pub = _pub(a, P, False)
    out = os.path.join(P["snapshots"], stager.now().replace(":", ""))
    snap = stager.capture_snapshot(pub, out)
    print(f"snapshot {out}: {len(snap['listings'])} locales, digest {snap['digest']}")
    inexact = [e for l in snap["images"].values() for ents in l.values() for e in ents if e.get("byte_exact") is False]
    if inexact:
        print(f"WARNING: {len(inexact)} downloaded images are not byte-identical to the API sha256 (rollback restores a re-encoded copy)")


def _latest_snapshot(P):
    d = P["snapshots"]
    if not os.path.isdir(d):
        return None
    xs = sorted(x for x in os.listdir(d) if os.path.exists(os.path.join(d, x, "snapshot.json")))
    return os.path.join(d, xs[-1]) if xs else None


def _commit_gate(a):
    if not a.commit:
        return False
    if os.environ.get("LISTING_HARNESS_ALLOW_COMMIT") != "1":
        raise SystemExit("--commit also needs LISTING_HARNESS_ALLOW_COMMIT=1 in the environment (double gate against accidents)")
    return True


def cmd_stage(a, P):
    commit = _commit_gate(a)
    state, cat = S.load_state(P["state"]), C.load(P["catalog"])
    v = cat.get(a.variant)
    rows = ingest.Store(P["metrics"]).all()
    t = _traffic(a, P, state, rows)
    act = S.next_action(state, cat, _today(a), t["visitors_per_day"], t["baseline_cvr"])
    allowed = act["action"] == "stage" and act.get("variant_id") == v.id
    print(f"scheduler: {act['action']} {act.get('variant_id', '')} - {act.get('reason', '')}")
    if commit and not allowed:
        raise SystemExit("scheduler does not allow staging this variant now; refusing --commit")
    pub = _pub(a, P, commit)
    res = stager.stage(pub, v, a.snapshot or _latest_snapshot(P), cat.base_dir)
    print(json.dumps(res, indent=2))
    if res.get("committed"):
        S.record_staged(state, _today(a), act["plan"], v.id, act["kind"])
        if v.status == "ready":
            v.status = "running"
        S.save_state(state, P["state"])
        C.save(cat, P["catalog"])


def cmd_rollback(a, P):
    commit = _commit_gate(a)
    snap = a.snapshot or _latest_snapshot(P)
    if not snap:
        raise SystemExit("no snapshot found")
    pub = _pub(a, P, commit)
    res = stager.rollback(pub, snap, a.locale)
    print(json.dumps(res, indent=2))
    if res.get("committed"):
        state = S.load_state(P["state"])
        w = S.open_window(state)
        if w:
            S.abort_window(state, w["id"], "rolled back to snapshot")
        state["live_variant"] = state.get("baseline_variant")
        S.save_state(state, P["state"])


def cmd_report(a, P):
    state, cat = S.load_state(P["state"]), C.load(P["catalog"])
    rows = ingest.Store(P["metrics"]).all()
    t = _traffic(a, P, state, rows)
    act = S.next_action(state, cat, _today(a), t["visitors_per_day"], t["baseline_cvr"])
    md = report.render(state, cat, _today(a), t, act)
    if a.out:
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        with open(a.out, "w", encoding="utf-8") as f:
            f.write(md)
        print(a.out)
    else:
        print(md)


def build_parser():
    ap = argparse.ArgumentParser(prog="listing_harness", description="Play listing experiment harness (dry-run by default)")
    ap.add_argument("--home"); ap.add_argument("--catalog"); ap.add_argument("--today", help="YYYY-MM-DD override (tests)")
    ap.add_argument("--package")
    sp = ap.add_subparsers(dest="cmd", required=True)

    def add(name, fn, *args):
        p = sp.add_parser(name)
        p.set_defaults(fn=fn)
        for spec, kw in args:
            p.add_argument(*spec, **kw)
        return p
    tr = [(("--visitors-per-day",), {"type": float}), (("--cvr",), {"type": float})]
    add("power", cmd_power, (("--cvr",), {"type": float, "default": 0.25}), (("--visitors",), {"default": "10,50,200"}), (("--days",), {"default": "14,28,56"}))
    add("catalog", cmd_catalog)
    add("ingest", cmd_ingest, (("--csv",), {}), (("--months",), {"default": date.today().strftime("%Y%m")}), (("--breakdown",), {"default": "country"}))
    add("next", cmd_next, *tr)
    add("set-groups", cmd_set_groups, (("--treated",), {"default": ""}), (("--control",), {"default": ""}))
    add("start-baseline", cmd_start_baseline, *tr)
    add("mark-live", cmd_mark_live, (("window",), {}), (("--date",), {"required": True}))
    add("analyze", cmd_analyze, (("--window",), {}), (("--interim",), {"action": "store_true"}))
    add("snapshot", cmd_snapshot)
    add("stage", cmd_stage, (("--variant",), {"required": True}), (("--snapshot",), {}), (("--commit",), {"action": "store_true"}), *tr)
    add("rollback", cmd_rollback, (("--locale",), {"required": True}), (("--snapshot",), {}), (("--commit",), {"action": "store_true"}))
    add("report", cmd_report, (("--out",), {}), *tr)
    return ap


def main(argv=None) -> int:
    a = build_parser().parse_args(argv)
    try:
        return a.fn(a, paths(a)) or 0
    except (stager.PlanRefused, stager.ScopeViolation, stager.DryRunViolation, ingest.NotConfigured, C.CatalogError, ValueError) as e:
        print(f"refused: {e}", file=sys.stderr)
        return 1
