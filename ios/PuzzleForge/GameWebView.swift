import SwiftUI
import WebKit

struct GameWebView: UIViewRepresentable {
    var htmlContent: String? = nil
    var bundleDirectory: URL? = nil
    var onScoreReceived: ((Int) -> Void)?
    var onGameOver: ((Int) -> Void)?
    var onGameError: ((String) -> Void)?

    func makeCoordinator() -> Coordinator {
        Coordinator(onScoreReceived: onScoreReceived, onGameOver: onGameOver, onGameError: onGameError)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let controller = WKUserContentController()
        controller.add(context.coordinator, name: "gameScore")
        config.userContentController = controller

        // Inject JS error catching at document start
        let errorScript = WKUserScript(
            source: """
            window.onerror = function(msg, url, line, col, error) {
                webkit.messageHandlers.gameScore.postMessage({
                    error: true,
                    message: String(msg),
                    line: line || 0
                });
            };
            window.addEventListener('unhandledrejection', function(e) {
                webkit.messageHandlers.gameScore.postMessage({
                    error: true,
                    message: 'Unhandled promise: ' + String(e.reason),
                    line: 0
                });
            });
            """,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        )
        controller.addUserScript(errorScript)

        // Allow file access for bundle-based games
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.navigationDelegate = context.coordinator

        loadContent(webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // Only reload if content actually changed
    }

    private func loadContent(_ webView: WKWebView) {
        if let bundleDir = bundleDirectory {
            // Load from extracted bundle directory
            let indexURL = bundleDir.appendingPathComponent("index.html")
            webView.loadFileURL(indexURL, allowingReadAccessTo: bundleDir)
        } else if let html = htmlContent {
            // Legacy: load HTML string directly
            webView.loadHTMLString(html, baseURL: nil)
        }
    }

    class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        var onScoreReceived: ((Int) -> Void)?
        var onGameOver: ((Int) -> Void)?
        var onGameError: ((String) -> Void)?
        private var hasReportedError = false

        init(onScoreReceived: ((Int) -> Void)?, onGameOver: ((Int) -> Void)?, onGameError: ((String) -> Void)?) {
            self.onScoreReceived = onScoreReceived
            self.onGameOver = onGameOver
            self.onGameError = onGameError
        }

        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            if message.name == "gameScore",
               let body = message.body as? [String: Any] {

                // Handle JS error reports
                if let isError = body["error"] as? Bool, isError {
                    let errorMsg = body["message"] as? String ?? "Unknown JS error"
                    let line = body["line"] as? Int ?? 0
                    NSLog("[GameWebView] JS error at line %d: %@", line, errorMsg)
                    if !hasReportedError {
                        hasReportedError = true
                        DispatchQueue.main.async {
                            self.onGameError?(errorMsg)
                        }
                    }
                    return
                }

                // Handle score messages
                let score = body["score"] as? Int ?? 0
                let gameOver = body["gameOver"] as? Bool ?? false

                DispatchQueue.main.async {
                    self.onScoreReceived?(score)
                    if gameOver {
                        self.onGameOver?(score)
                    }
                }
            }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            let scheme = navigationAction.request.url?.scheme
            if navigationAction.navigationType == .other ||
               scheme == "about" || scheme == "file" {
                decisionHandler(.allow)
            } else {
                decisionHandler(.cancel)
            }
        }

        // Navigation failure detection
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            NSLog("[GameWebView] Navigation failed: %@", error.localizedDescription)
            if !hasReportedError {
                hasReportedError = true
                DispatchQueue.main.async {
                    self.onGameError?("Game failed to load: \(error.localizedDescription)")
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            NSLog("[GameWebView] Page load failed: %@", error.localizedDescription)
            if !hasReportedError {
                hasReportedError = true
                DispatchQueue.main.async {
                    self.onGameError?("Game failed to load: \(error.localizedDescription)")
                }
            }
        }

        // Blank screen detection after successful load
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in
                guard let self = self, !self.hasReportedError else { return }
                webView.evaluateJavaScript(
                    "document.body.innerText.trim().length > 0 || document.querySelector('canvas') !== null"
                ) { result, _ in
                    if let hasContent = result as? Bool, !hasContent {
                        NSLog("[GameWebView] Blank screen detected")
                        self.hasReportedError = true
                        DispatchQueue.main.async {
                            self.onGameError?("Game failed to render — the screen appears blank")
                        }
                    }
                }
            }
        }
    }
}
