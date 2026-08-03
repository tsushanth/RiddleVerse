import Foundation

/// Host-controlled game session lifecycle for iOS.
/// Owns start, score tracking, timer cap, background end, and finish.
actor GameSessionManager {

    static let shared = GameSessionManager()

    private let apiBase = "https://puzzleverseai.com"
    private let maxSeconds = 300 // 5-minute hard cap

    private(set) var activeSessionId: String? = nil
    private var reportedScore: Int? = nil
    private var timerTask: Task<Void, Never>? = nil
    private var onTimeout: (() -> Void)? = nil

    // MARK: - Start

    /// Creates a session and starts the 5-min timer. Returns session_id, or nil on failure.
    func startSession(gameId: String, userId: String, onTimeout: @escaping () -> Void) async -> String? {
        // End any existing session first
        if let old = activeSessionId {
            await endSession(sessionId: old, score: nil, forcedBy: "new_session")
        }

        guard let endpoint = URL(string: "\(apiBase)/api/sessions/start") else { return nil }

        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 10

        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: [
                "game_id": gameId,
                "user_id": userId,
                "source": "ios"
            ])
            let (data, response) = try await URLSession.shared.data(for: req)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let sessionId = json?["session_id"] as? String else { return nil }

            activeSessionId = sessionId
            reportedScore = nil
            self.onTimeout = onTimeout
            startTimer(sessionId: sessionId)
            return sessionId

        } catch {
            return nil
        }
    }

    // MARK: - Score

    func reportScore(_ score: Int) {
        reportedScore = score
        // Fire-and-forget event (non-blocking)
        guard let sessionId = activeSessionId else { return }
        Task {
            _ = try? await URLSession.shared.data(for: buildRequest(
                path: "/api/sessions/event",
                body: ["session_id": sessionId, "type": "score", "value": score]
            ))
        }
    }

    // MARK: - End

    @discardableResult
    func endSession(sessionId: String, score: Int?, forcedBy: String) async -> [String: Any]? {
        // Clear state immediately to prevent double-end
        if activeSessionId == sessionId {
            activeSessionId = nil
            timerTask?.cancel()
            timerTask = nil
            onTimeout = nil
        }

        var body: [String: Any] = ["session_id": sessionId, "forced_by": forcedBy]
        if let s = score ?? reportedScore { body["score"] = s }
        reportedScore = nil

        do {
            let (data, _) = try await URLSession.shared.data(for: buildRequest(
                path: "/api/sessions/end", body: body
            ))
            return try JSONSerialization.jsonObject(with: data) as? [String: Any]
        } catch {
            return nil
        }
    }

    /// End the active session (if any) with a given reason.
    func endActiveSession(score: Int? = nil, forcedBy: String) async {
        guard let sessionId = activeSessionId else { return }
        await endSession(sessionId: sessionId, score: score, forcedBy: forcedBy)
    }

    // MARK: - Timer

    private func startTimer(sessionId: String) {
        timerTask?.cancel()
        timerTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(300) * 1_000_000_000)
            guard !Task.isCancelled else { return }
            await self?.handleTimeout(sessionId: sessionId)
        }
    }

    private func handleTimeout(sessionId: String) async {
        let cb = onTimeout
        await endSession(sessionId: sessionId, score: nil, forcedBy: "timeout")
        await MainActor.run { cb?() }
    }

    // MARK: - Helpers

    private func buildRequest(path: String, body: [String: Any]) throws -> URLRequest {
        guard let url = URL(string: "\(apiBase)\(path)") else {
            throw URLError(.badURL)
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 15
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return req
    }
}
