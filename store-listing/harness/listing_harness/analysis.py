"""Window analysis: conversion per window, Beta-Binomial probability to beat the reference window, credible
interval, drift check against the post-control window, difference-in-differences against a control country group,
and a hard guard against peeking (results are produced only for windows whose pre-set length and metrics lag elapsed)."""
from __future__ import annotations

from datetime import date, timedelta

from . import ingest, scheduler as S, stats


class TooEarly(Exception):
    pass


def _counts(rows, start, end, countries=None) -> tuple[stats.Counts, int]:
    v, i, days = ingest.sum_range(rows, start, end, countries)
    return stats.Counts(v, i), days


def reference_window(state: dict, window: dict) -> dict | None:
    """The closest earlier completed window: the thing a candidate is compared against."""
    idx = state["windows"].index(window)
    for w in reversed(state["windows"][:idx]):
        if w["status"] == "complete" and w.get("live_on"):
            return w
    return None


def analyze_window(state: dict, window_id: str, rows: list[dict], today: date, interim: bool = False) -> dict:
    p = S.policy_of(state)
    w = next(x for x in state["windows"] if x["id"] == window_id)
    if w["status"] != "running":
        raise ValueError(f"window {window_id} is {w['status']}, not running")
    start, end = S.measure_range(w, p)
    due = S.data_ready_on(w, p)
    if today < due and not interim:
        raise TooEarly(f"window {window_id} is not readable until {due} (peeking guard). Use --interim for a non-binding look.")
    cur, days = _counts(rows, start, min(end, today))
    out = {"window": window_id, "variant_id": w["variant_id"], "kind": w["kind"], "measure": [str(start), str(end)],
           "visitors": cur.visitors, "installs": cur.installs, "rate": cur.rate if cur.visitors else None,
           "interim": bool(interim and today < due)}
    full_days = (end - start).days + 1
    if not interim or today >= due:
        if days < p.min_data_coverage * full_days:
            out.update(verdict="blocked", note=f"only {days}/{full_days} days of metrics present; ingest the missing days")
            return out
    if cur.visitors < w["required_visitors"] and not out["interim"]:
        out.update(verdict="extend", note=f"{cur.visitors} visitors < {w['required_visitors']} pre-registered; extend one week")
        return out
    if w["kind"] == "baseline":
        out["verdict"] = "reference"
        out.update(_post_control(state, w, rows, p, cur))
        return out
    ref = reference_window(state, w)
    if not ref:
        out.update(verdict="blocked", note="no completed reference window before this one")
        return out
    r_start, r_end = S.measure_range(ref, p)
    base, _ = _counts(rows, r_start, r_end)
    cmp_ = stats.compare(base, cur)
    out.update(reference_window=ref["id"], reference_rate=base.rate, reference_visitors=base.visitors, **cmp_)
    out["verdict"] = "interim" if out["interim"] else stats.verdict(cmp_["p_beat"], p.decision_hi, p.decision_lo)
    out["rel_lift_target"] = p.target_relative_lift
    g = state.get("groups") or {}
    if g.get("treated") and g.get("control"):
        tpre, _ = _counts(rows, r_start, r_end, set(g["treated"]))
        tpost, _ = _counts(rows, start, end, set(g["treated"]))
        cpre, _ = _counts(rows, r_start, r_end, set(g["control"]))
        cpost, _ = _counts(rows, start, end, set(g["control"]))
        if min(tpre.visitors, tpost.visitors, cpre.visitors, cpost.visitors) > 0:
            out["did"] = stats.did(tpre, tpost, cpre, cpost)
            out["did_note"] = "country groups only approximate listing language; treat as a drift check, not proof"
    if out["interim"]:
        out["note"] = "INTERIM LOOK: not a decision. Peeking inflates false positives; wait for the pre-set window."
    return out


def _post_control(state, w, rows, p, cur) -> dict:
    """If this baseline window follows a candidate that followed a baseline, check the two baselines agree (drift)."""
    idx = state["windows"].index(w)
    if idx < 2:
        return {}
    cand, pre = state["windows"][idx - 1], state["windows"][idx - 2]
    if cand["kind"] != "candidate" or pre["kind"] != "baseline" or pre["status"] != "complete":
        return {}
    ps, pe = S.measure_range(pre, p)
    base, _ = _counts(rows, ps, pe)
    c = stats.compare(base, cur)
    drift = c["p_beat"] > 0.9 or c["p_beat"] < 0.1
    return {"post_control_for": cand["id"], "drift_p_post_gt_pre": c["p_beat"], "drift_flag": drift,
            "drift_note": ("the two baseline windows differ: the candidate result is confounded by time, treat as unreliable"
                           if drift else "baseline windows agree; no sign of time drift")}


def attach_post_control(state: dict, result: dict) -> None:
    """Copy a post-control drift check onto the candidate window it belongs to (the verdict itself is not changed)."""
    if result.get("post_control_for"):
        c = next(x for x in state["windows"] if x["id"] == result["post_control_for"])
        if c.get("result"):
            c["result"]["post_control"] = {k: result[k] for k in ("drift_p_post_gt_pre", "drift_flag", "drift_note")}
