//
//  AuthHelper.swift
//  PuzzleForge
//
//  Centralized auth wrapper. Returns real user identity when signed in,
//  or stable guest defaults when not. Keeps all existing code paths working.
//

import FirebaseAuth

enum AuthHelper {
    /// Always returns a userId — real uid when signed in, stable guest id otherwise.
    static var userId: String {
        Auth.auth().currentUser?.uid ?? guestId
    }

    /// Always returns an email — real email when signed in, empty string otherwise.
    static var userEmail: String {
        Auth.auth().currentUser?.email ?? ""
    }

    /// True when no Firebase user is signed in.
    static var isGuest: Bool {
        Auth.auth().currentUser == nil
    }

    /// Stable guest identifier persisted across sessions.
    private static var guestId: String {
        let key = "puzzleverse_guest_id"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let newId = "guest_\(UUID().uuidString)"
        UserDefaults.standard.set(newId, forKey: key)
        return newId
    }
}
