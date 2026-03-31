package com.kreativekoala.riddleverse

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private const val TAG = "GameWebView"

/**
 * Viewport-units polyfill — standard approach from CSS-Tricks / viewport-units-buggyfill.
 * Detects if CSS viewport units (vh, vw, vmin, vmax) are broken (resolve to 0)
 * and rewrites <style> elements to use correct pixel values from window.innerHeight/innerWidth.
 * Only activates when viewport units are actually broken; no-ops on working WebViews.
 */
private const val VIEWPORT_POLYFILL = """
(function() {
    var vh = window.innerHeight;
    var vw = window.innerWidth;
    if (vh <= 0 || vw <= 0) { console.log('VP_FIX: no dimensions yet'); return; }

    // Probe: does CSS 100vh actually resolve to the real viewport height?
    var t = document.createElement('div');
    t.style.cssText = 'position:fixed;top:0;left:0;height:100vh;visibility:hidden;pointer-events:none;z-index:-9999';
    document.documentElement.appendChild(t);
    var cssVh = t.offsetHeight;
    document.documentElement.removeChild(t);

    console.log('VP_FIX: innerH=' + vh + ' css100vh=' + cssVh);

    if (cssVh > 0 && Math.abs(cssVh - vh) < 2) {
        console.log('VP_FIX: viewport units OK, skipping');
        return;
    }

    console.log('VP_FIX: viewport units broken, applying polyfill');
    var vmin = Math.min(vh, vw);
    var vmax = Math.max(vh, vw);

    // Fix html/body height (overrides 100vh / 100dvh that resolved to 0)
    // Use min-height so content taller than viewport can still scroll
    document.documentElement.style.minHeight = vh + 'px';
    if (document.body) {
        document.body.style.minHeight = vh + 'px';
        // Ensure body is a positioning context so children with
        // position:absolute + inset:0 resolve against body, not the
        // broken 0-height initial containing block (CSS viewport).
        var bp = window.getComputedStyle(document.body).position;
        if (bp === 'static' || bp === '') {
            document.body.style.position = 'relative';
        }
    }

    // Rewrite viewport units in all <style> elements
    function fixCSS(css) {
        return css
            .replace(/(\d+(?:\.\d+)?)dvh/g,  function(m, n) { return (parseFloat(n) * vh / 100) + 'px'; })
            .replace(/(\d+(?:\.\d+)?)svh/g,  function(m, n) { return (parseFloat(n) * vh / 100) + 'px'; })
            .replace(/(\d+(?:\.\d+)?)vh/g,   function(m, n) { return (parseFloat(n) * vh / 100) + 'px'; })
            .replace(/(\d+(?:\.\d+)?)vw/g,   function(m, n) { return (parseFloat(n) * vw / 100) + 'px'; })
            .replace(/(\d+(?:\.\d+)?)vmin/g, function(m, n) { return (parseFloat(n) * vmin / 100) + 'px'; })
            .replace(/(\d+(?:\.\d+)?)vmax/g, function(m, n) { return (parseFloat(n) * vmax / 100) + 'px'; });
    }

    var styles = document.querySelectorAll('style');
    for (var i = 0; i < styles.length; i++) {
        var orig = styles[i].textContent;
        var fixed = fixCSS(orig);
        if (fixed !== orig) styles[i].textContent = fixed;
    }

    // Trigger resize so game JS recalculates positions/sizes
    window.dispatchEvent(new Event('resize'));
})();
"""

// JS error catching script injected at document start
private const val ERROR_CATCHING_SCRIPT = """
<script>
window.onerror = function(msg, url, line, col, error) {
    if (window.AndroidBridge) {
        window.AndroidBridge.reportError(String(msg) + ' (line ' + (line || 0) + ')');
    }
};
window.addEventListener('unhandledrejection', function(e) {
    if (window.AndroidBridge) {
        window.AndroidBridge.reportError('Unhandled promise: ' + String(e.reason));
    }
});
</script>
"""

@Composable
fun GameWebView(
    htmlContent: String? = null,
    bundleDir: String? = null,
    modifier: Modifier = Modifier,
    onScoreReceived: (Int) -> Unit = {},
    onGameOver: (Int) -> Unit = {},
    onGameError: (String) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            var hasReportedError = false

            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false

                settings.allowFileAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true

                setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d(TAG, "JS [${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                            // Detect ERROR level console messages as game errors
                            if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR && !hasReportedError) {
                                hasReportedError = true
                                Handler(Looper.getMainLooper()).post {
                                    onGameError("Game error: ${it.message()}")
                                }
                            }
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme
                        return scheme != "file" && scheme != "about"
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true && !hasReportedError) {
                            hasReportedError = true
                            val desc = error?.description?.toString() ?: "Unknown error"
                            Log.e(TAG, "Main frame error: $desc")
                            Handler(Looper.getMainLooper()).post {
                                onGameError("Game failed to load: $desc")
                            }
                        }
                    }

                    var blankLoaded = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // Step 1: about:blank finished — WebView now has real dimensions.
                        // Load the actual game.
                        if (!blankLoaded && url == "about:blank") {
                            blankLoaded = true
                            if (bundleDir != null) {
                                val indexFile = File(bundleDir, "index.html")
                                try {
                                    var html = indexFile.readText()
                                    html = html.replace("100dvh", "100vh")
                                    html = html.replace("100svh", "100vh")
                                    html = html.replace("overflow: hidden", "overflow: visible")
                                    html = html.replace("overflow:hidden", "overflow:visible")
                                    html = if (html.contains("</head>", ignoreCase = true)) {
                                        html.replaceFirst("</head>", "$ERROR_CATCHING_SCRIPT</head>", ignoreCase = true)
                                    } else {
                                        ERROR_CATCHING_SCRIPT + html
                                    }
                                    indexFile.writeText(html)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to patch HTML", e)
                                }
                                Log.d(TAG, "Loading game (${view?.width}x${view?.height}): file://$bundleDir/index.html")
                                view?.loadUrl("file://$bundleDir/index.html")
                            } else if (htmlContent != null) {
                                view?.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            }
                            return
                        }

                        // Step 2: Game loaded
                        if (blankLoaded && url != null && url != "about:blank") {
                            Log.d(TAG, "Game loaded: $url")
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!hasReportedError) {
                                    view?.evaluateJavascript(
                                        "(document.body.innerText.trim().length > 0 || document.querySelector('canvas') !== null)"
                                    ) { result ->
                                        if (result == "false") {
                                            Log.w(TAG, "Blank screen detected")
                                            hasReportedError = true
                                            onGameError("Game failed to render — the screen appears blank")
                                        }
                                    }
                                }
                            }, 5000)
                        }
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postScore(score: Int) {
                        onScoreReceived(score)
                    }

                    @JavascriptInterface
                    fun gameOver(score: Int) {
                        onGameOver(score)
                    }

                    @JavascriptInterface
                    fun reportError(message: String) {
                        if (!hasReportedError) {
                            hasReportedError = true
                            Log.e(TAG, "JS error reported: $message")
                            Handler(Looper.getMainLooper()).post {
                                onGameError(message)
                            }
                        }
                    }
                }, "AndroidBridge")

                // Load about:blank first so WebView has real viewport dimensions
                // before the game JS initializes canvas/layout
                loadUrl("about:blank")
            }
        }
    )
}
