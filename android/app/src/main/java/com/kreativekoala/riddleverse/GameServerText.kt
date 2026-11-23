package com.kreativekoala.riddleverse

import org.json.JSONObject

/**
 * Maps the stable codes the backend sends with game build status and errors to localized strings.
 *
 * The server keeps sending English `message` / `detail` / `error` text for older apps; this app ignores it whenever it
 * knows the code. Order for a status line: `messageCode`, then the build `phase`, then (unknown phase only) the server text.
 * For an error: a known `code`, else the server text as before.
 */
object GameServerText {
    private val phaseRes = mapOf(
        "generate" to R.string.gen_phase_generate,
        "validate" to R.string.gen_phase_validate,
        "fix" to R.string.gen_phase_fix,
        "polish" to R.string.gen_phase_polish,
        "verify" to R.string.gen_phase_verify,
        "package" to R.string.gen_phase_package,
    )

    private val messageRes = mapOf(
        "build" to R.string.gen_phase_generate,
        "prepare" to R.string.gen_status_prepare,
        "build_level" to R.string.gen_status_build_level,
        "apply_changes" to R.string.gen_status_apply_changes,
        "check_quality" to R.string.gen_phase_validate,
        "fix_issues" to R.string.gen_phase_fix,
        "no_issues" to R.string.gen_status_no_issues,
        "more_fixes" to R.string.gen_status_more_fixes,
        "polish" to R.string.gen_phase_polish,
        "verify" to R.string.gen_phase_verify,
        "runtime_test" to R.string.gen_status_runtime_test,
        "fix_runtime" to R.string.gen_status_fix_runtime,
        "reverify" to R.string.gen_status_reverify,
        "package" to R.string.gen_phase_package,
        "save_changes" to R.string.gen_status_save_changes,
    )

    private val detailRes = mapOf(
        "creating_files" to R.string.gen_detail_creating_files,
        "running_checks" to R.string.gen_detail_running_checks,
        "found_issues" to R.string.gen_detail_found_issues,
        "critical_remaining" to R.string.gen_detail_critical_remaining,
        "passed_checks" to R.string.gen_detail_passed_checks,
        "improving_feel" to R.string.gen_detail_improving_feel,
        "ensuring_works" to R.string.gen_detail_ensuring_works,
        "testing_browser" to R.string.gen_detail_testing_browser,
        "fixing_test_issues" to R.string.gen_detail_fixing_test_issues,
        "checking_fixes" to R.string.gen_detail_checking_fixes,
        "creating_bundle" to R.string.gen_detail_creating_bundle,
        "committing" to R.string.gen_detail_committing,
        "cloning" to R.string.gen_detail_cloning,
    )

    private val errorRes = mapOf(
        "description_required" to R.string.gen_err_description_required,
        "description_too_long" to R.string.gen_err_description_too_long,
        "free_limit_reached" to R.string.gen_err_free_limit,
        "hourly_limit_reached" to R.string.gen_err_hourly_limit,
        "insufficient_coins" to R.string.gen_err_insufficient_coins,
        "build_server_unavailable" to R.string.gen_err_build_unavailable,
        "build_server_error" to R.string.gen_err_build_unavailable,
        "worker_busy" to R.string.gen_err_build_unavailable,
        "build_connection_lost" to R.string.gen_err_connection_lost,
        "build_stopped" to R.string.gen_err_build_stopped,
        "generation_timeout" to R.string.gen_err_timeout,
        "generation_failed" to R.string.gen_err_generation_failed,
        "server_error" to R.string.gen_err_server,
        "fetch_failed" to R.string.gen_err_server,
        "save_failed" to R.string.gen_err_server,
        "payment_failed" to R.string.gen_err_server,
        "repo_init_failed" to R.string.gen_err_server,
        "git_not_configured" to R.string.gen_err_server,
        "download_failed" to R.string.gen_err_server,
        "revert_failed" to R.string.gen_err_server,
        "delete_failed" to R.string.gen_err_server,
        "unauthorized" to R.string.gen_err_server,
        "game_not_found" to R.string.gen_err_not_found,
        "parent_not_found" to R.string.gen_err_not_found,
        "not_creator" to R.string.gen_err_not_creator,
        "missing_fields" to R.string.gen_err_invalid_request,
        "invalid_level" to R.string.gen_err_invalid_request,
        "previous_level_required" to R.string.gen_err_invalid_request,
        "no_bundle" to R.string.gen_err_invalid_request,
        "no_version_history" to R.string.gen_err_invalid_request,
        "series_not_migrated" to R.string.gen_err_invalid_request,
        "variant_exists" to R.string.gen_err_variant_busy,
        "variant_in_progress" to R.string.gen_err_variant_busy,
        "quota_exhausted" to R.string.gen_err_quota,
        "rate_limited" to R.string.gen_err_rate_limited,
        "creation_busy" to R.string.gen_err_busy_today,
        "auth_required" to R.string.onb_gate_sign_in_to_create, // same text as GuestGate.SIGN_IN_TO_CREATE_RES
        "no_game_files" to R.string.gen_err_no_files,
    )

    /** Codes the app maps to a string (the backend test checks every code it sends is in this list). */
    val knownErrorCodes: Set<String> get() = errorRes.keys
    val knownMessageCodes: Set<String> get() = messageRes.keys
    val knownDetailCodes: Set<String> get() = detailRes.keys

    /** Status headline. Null when there is neither a known code, a known phase nor server text (the UI default shows). */
    fun statusMessage(messageCode: String?, phase: String?, serverText: String): UiText? {
        messageRes[messageCode]?.let { return UiText.Res(it) }
        phaseRes[phase]?.let { return UiText.Res(it) }
        return if (serverText.isEmpty()) null else UiText.Raw(serverText)
    }

    /**
     * Status detail line. A known detail code is localized. For a known phase the English detail is dropped (many carry
     * a game title or a count in English prose) rather than shown in the wrong language; only an unknown phase (a server
     * newer than this app) falls back to the server text.
     */
    fun statusDetail(detailCode: String?, detailArgs: List<Int>, phase: String?, serverText: String): UiText? {
        detailRes[detailCode]?.let { return UiText.Res(it, detailArgs) }
        if (phase in phaseRes) return null
        return if (serverText.isEmpty()) null else UiText.Raw(serverText)
    }

    /** Error text for a server `code`, or null when the code is missing or unknown (the caller then shows the server text). */
    fun errorText(code: String?): UiText? = errorRes[code]?.let { UiText.Res(it) }

    fun detailArgs(json: JSONObject): List<Int> {
        val arr = json.optJSONArray("detailArgs") ?: return emptyList()
        return (0 until arr.length()).map { arr.optInt(it) }
    }
}
