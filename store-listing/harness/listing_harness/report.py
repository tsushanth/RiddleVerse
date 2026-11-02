"""Markdown run report: what ran, results, next recommendation."""
from __future__ import annotations

from datetime import date

from . import scheduler as S, stats


def _pct(x, digits=1):
    return "n/a" if x is None else f"{x * 100:.{digits}f}%"


def render(state: dict, cat, today: date, traffic: dict, action: dict) -> str:
    p = S.policy_of(state)
    L = [f"# Listing experiment report ({today})", ""]
    L += ["## Traffic and power", ""]
    if traffic.get("visitors_per_day"):
        L.append(f"Trailing estimate: {traffic['visitors_per_day']:.1f} store-listing visitors/day, baseline conversion "
                 f"{_pct(traffic['baseline_cvr'])} ({traffic['days']} days of data). Source: {traffic.get('source', 'metrics store')}.")
    else:
        L.append("No usable traffic estimate yet (ingest metrics first).")
    plan = action.get("plan", {})
    if plan.get("feasible"):
        L.append(f"Pre-registered window: {plan['min_days']} days (>= {plan['required_visitors']} visitors) to detect a "
                 f"{_pct(p.target_relative_lift, 0)} relative lift at alpha {p.alpha}, power {p.power:.0%}.")
    elif plan.get("reason"):
        L.append(f"**Experiments are not supported at this traffic.** {plan['reason']}")
    L += ["", "## Windows", ""]
    if not state["windows"]:
        L.append("None yet.")
    else:
        L += ["| id | variant | kind | status | live | days | visitors | installs | verdict |", "|---|---|---|---|---|---|---|---|---|"]
        for w in state["windows"]:
            r = w.get("result") or {}
            L.append(f"| {w['id']} | {w['variant_id']} | {w['kind']} | {w['status']} | {w.get('live_on') or '-'} | {w['min_days']} | "
                     f"{r.get('visitors', '-')} | {r.get('installs', '-')} | {r.get('verdict', '-')} |")
    L += ["", "## Results", ""]
    any_res = False
    for w in state["windows"]:
        r = w.get("result")
        if not r or w["kind"] != "candidate":
            continue
        any_res = True
        L.append(f"### {w['id']} {w['variant_id']}: **{r['verdict']}**")
        if "p_beat" in r:
            L.append(f"- conversion {_pct(r['variant_rate'], 2)} vs reference {_pct(r['baseline_rate'], 2)} "
                     f"(window {r.get('reference_window')}, {r.get('reference_visitors')} visitors)")
            L.append(f"- P(variant beats reference) = {r['p_beat']:.3f}; relative lift median {_pct(r['rel_lift_median'])}, "
                     f"95% credible interval [{_pct(r['rel_lift_ci'][0])}, {_pct(r['rel_lift_ci'][1])}]")
        if "did" in r:
            L.append(f"- difference-in-differences vs control countries: median {_pct(r['did']['did_median'], 2)} pts, P(>0) {r['did']['p_positive']:.2f} ({r['did_note']})")
        pc = r.get("post_control")
        if pc:
            L.append(f"- time-drift check: {pc['drift_note']} (P(post>pre baseline) = {pc['drift_p_post_gt_pre']:.2f})")
        if r.get("note"):
            L.append(f"- note: {r['note']}")
        L.append("")
    if not any_res:
        L.append("No completed candidate windows.")
    L += ["", "## Next recommendation", "", f"**{action['action']}**: {action.get('reason', '')}"]
    if action.get("variant_id"):
        L.append(f"- variant: `{action['variant_id']}`")
    if action.get("on_or_after"):
        L.append(f"- not before: {action['on_or_after']}")
    L += ["", f"_Catalog: {len(cat.variants)} variants, {len(cat.ready())} ready._", ""]
    return "\n".join(L)


def power_table(p0: float, vpd=(10, 50, 200), days=(14, 28, 56)) -> str:
    rows = stats.mde_table(p0, list(vpd), list(days))
    L = [f"Minimum detectable relative lift (alpha 0.05 two-sided, power 80%, baseline conversion {p0:.0%})", "",
         "| visitors/day | days | concurrent A/B (Console experiment) | sequential windows (lower bound) |", "|---|---|---|---|"]
    for r in rows:
        L.append(f"| {r['visitors_per_day']} | {r['days']} | {_pct(r['mde_rel_concurrent'], 0)} | {_pct(r['mde_rel_sequential'], 0)} |")
    return "\n".join(L)
