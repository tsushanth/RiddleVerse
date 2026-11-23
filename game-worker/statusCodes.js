// Stable codes for what the build worker tells the app, so the app can show the text in the user's language.
//
// The worker (and the backend that proxies it) keep sending the English `message`/`detail`/`error` for older apps; each
// event now also carries codes. The app maps a code to a string resource and falls back to the English text only for a
// code it does not know.
//
//   status events: messageCode (e.g. 'no_issues'), detailCode (e.g. 'found_issues') and detailArgs (e.g. [3])
//   error events:  code (e.g. 'generation_failed')

const MESSAGE_CODES = [
    [/^Building your game$/, 'build'],
    [/^Preparing (harder challenge|customization|game files)$/, 'prepare'],
    [/^Generating Level \d+$/, 'build_level'],
    [/^Applying your changes$/, 'apply_changes'],
    [/^Checking (game quality|new level)$/, 'check_quality'],
    [/^Fixing issues$/, 'fix_issues'],
    [/^No issues found$/, 'no_issues'],
    [/^Additional fixes needed$/, 'more_fixes'],
    [/^(Polishing gameplay|Final polish)$/, 'polish'],
    [/^Final verification$/, 'verify'],
    [/^Runtime testing$/, 'runtime_test'],
    [/^Fixing runtime errors$/, 'fix_runtime'],
    [/^Re-verifying$/, 'reverify'],
    [/^Packaging (game|level)$/, 'package'],
    [/^Saving changes$/, 'save_changes'],
];

// [pattern, code, how to read the numeric argument (capture group index) or 0 for none]
const DETAIL_CODES = [
    [/^Creating game files from your description$/, 'creating_files', 0],
    [/^Running automated (quality )?checks$/, 'running_checks', 0],
    [/^Found (\d+) issues? to resolve$/, 'found_issues', 1],
    [/^(\d+) critical issues? remaining$/, 'critical_remaining', 1],
    [/^(\d+) critical issues? found$/, 'found_issues', 1],
    [/^(?:Game|New level) passed (?:all )?quality checks$/, 'passed_checks', 0],
    [/^Improving game feel and completeness$/, 'improving_feel', 0],
    [/^Ensuring (?:everything works|difficulty feels right|customization works well)$/, 'ensuring_works', 0],
    [/^Testing game in headless browser$/, 'testing_browser', 0],
    [/^AI is fixing issues found during testing$/, 'fixing_test_issues', 0],
    [/^Checking if runtime fixes worked$/, 'checking_fixes', 0],
    [/^Creating downloadable bundle$/, 'creating_bundle', 0],
    [/^Committing to version history$/, 'committing', 0],
    [/^Cloning game repository$/, 'cloning', 0],
];

/** Codes for one status event. Unknown text gets no code (the app then falls back to `phase`, then to the text). */
export function statusCodes(message, detail) {
    const out = {};
    const m = MESSAGE_CODES.find(([re]) => re.test(String(message || '')));
    if (m) out.messageCode = m[1];
    for (const [re, code, argIdx] of DETAIL_CODES) {
        const hit = re.exec(String(detail || ''));
        if (hit) {
            out.detailCode = code;
            if (argIdx) out.detailArgs = [Number(hit[argIdx])];
            break;
        }
    }
    return out;
}

const ERROR_CODES = [
    [/^Unauthorized/, 'unauthorized'],
    [/^Game description is required/, 'description_required'],
    [/^Worker busy/, 'worker_busy'],
    [/^AI usage limit reached/, 'quota_exhausted'],
    [/^Failed to (generate|apply tweak|customize game)/, 'generation_failed'],
    [/^No game files were created/, 'no_game_files'],
    [/^Internal worker error/, 'server_error'],
    [/(is required|must be)/, 'missing_fields'],
    [/^Git integration not configured/, 'git_not_configured'],
    [/^Failed to (initialize|clone)/, 'repo_init_failed'],
    [/^Failed to fetch version history/, 'fetch_failed'],
];

/** Stable code for a worker error message, or undefined when it has none (the app then shows its generic text). */
export function errorCode(message) {
    const text = String(message || '');
    const hit = ERROR_CODES.find(([re]) => re.test(text));
    return hit ? hit[1] : undefined;
}
