//
//  PreferredTopicsView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/3/25.
//
import SwiftUI
import FirebaseAuth
import FirebaseFirestore

struct PreferredTopicsView: View {
    @Environment(\.presentationMode) var presentationMode
    @State private var selectedTopics: [String] = []
    @State private var availableTopics = ["Math", "Science", "History", "Geography", "Sports", "Movies", "Music", "Literature"]
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var isGeneratingPuzzles = false
    @State private var showPuzzleGenerationSuccess = false
    @State private var isSaving = false
    @Environment(\.dismiss) private var dismiss
    
    private let userEmail = Auth.auth().currentUser?.email ?? ""
    
    var body: some View {
        NavigationView {
            Form {
                // Available topics section
                Section(header: Text("Available Topics")) {
                    ForEach(availableTopics, id: \.self) { topic in
                        HStack {
                            Text(topic)
                            Spacer()
                            if selectedTopics.contains(topic) {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.blue)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if selectedTopics.contains(topic) {
                                selectedTopics.removeAll { $0 == topic }
                            } else {
                                selectedTopics.append(topic)
                            }
                        }
                    }
                }
                
                // Selected topics section
                if !selectedTopics.isEmpty {
                    Section(header: Text("Your Preferred Topics")) {
                        ForEach(selectedTopics, id: \.self) { topic in
                            HStack {
                                Text(topic)
                                Spacer()
                                Button {
                                    selectedTopics.removeAll { $0 == topic }
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Preferred Topics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: saveTopicsAndGeneratePuzzles) {
                        HStack {
                            if isSaving || isGeneratingPuzzles {
                                ProgressView()
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: buttonIcon)
                            }
                            
                            Text(buttonText)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(buttonBackground)
                        .cornerRadius(12)
                    }
                    .disabled(isButtonDisabled)
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .overlay {
                if isLoading {
                    ProgressView()
                }
            }
            .alert("Error", isPresented: .constant(errorMessage != nil)) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage ?? "")
            }
            .onAppear {
                loadTopics()
            }
        }
    }
    
    private var buttonText: String {
        if isSaving {
            return "Saving Topics..."
        } else if isGeneratingPuzzles {
            return "Generating Puzzles..."
        } else {
            return "Save & Generate Daily Puzzles"
        }
    }

    private var buttonIcon: String {
        if isSaving || isGeneratingPuzzles {
            return ""
        } else {
            return "checkmark.circle.fill"
        }
    }

    private var isButtonDisabled: Bool {
        return selectedTopics.isEmpty || isSaving || isGeneratingPuzzles
    }

    private var buttonBackground: some View {
        Group {
            if isButtonDisabled {
                Color.gray
            } else {
                LinearGradient(
                    colors: [.blue, .purple],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
        }
    }
    
    private func generateUserPuzzles(email: String) {
        isGeneratingPuzzles = true
        
        guard let encodedEmail = email.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://puzzleverseai.com/generate-user-puzzles-with-notifications?email=\(encodedEmail)") else {
            DispatchQueue.main.async {
                isGeneratingPuzzles = false
                // Handle URL error
            }
            return
        }
        
        print("🔵 Generating daily puzzles for: \(email)")
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                isGeneratingPuzzles = false
                
                if let error = error {
                    print("❌ Failed to generate puzzles: \(error.localizedDescription)")
                    // Handle error
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    print("🔵 Puzzle generation response: \(httpResponse.statusCode)")
                    
                    if httpResponse.statusCode == 200 {
                        print("✅ Daily puzzles generated successfully")
                        showPuzzleGenerationSuccess = true
                        
                        // Track analytics
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("daily_puzzles_generated", parameters: [
                            "email": email,
                            "topic_count": selectedTopics.count
                        ]))
                    } else {
                        print("❌ Puzzle generation failed: \(httpResponse.statusCode)")
                        // Handle server error
                    }
                    
                    // Log response for debugging
                    if let data = data,
                       let responseString = String(data: data, encoding: .utf8) {
                        print("🔵 Generation response: \(responseString)")
                    }
                }
            }
        }.resume()
    }

    
    private func loadTopics() {
        isLoading = true
        Firestore.firestore().collection("user_topics").document(userEmail).getDocument { snapshot, error in
            isLoading = false
            if let error = error {
                errorMessage = "Failed to load topics: \(error.localizedDescription)"
                return
            }
            
            if let topics = snapshot?.get("topics") as? [String] {
                selectedTopics = topics
            }
        }
    }
    
    private func saveTopicsToFirebase(email: String, topics: [String], completion: @escaping (Bool) -> Void) {
            guard let url = URL(string: "https://puzzleverseai.com/save-user-preferences") else {
                completion(false)
                return
            }
            
        let payload: [String: Any] = [
            "email": email,
            "topics": topics
        ]
            
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            
            do {
                request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            } catch {
                print("❌ Failed to encode topics payload: \(error)")
                completion(false)
                return
            }
            
            URLSession.shared.dataTask(with: request) { data, response, error in
                if let error = error {
                    print("❌ Failed to save topics: \(error.localizedDescription)")
                    completion(false)
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    let success = httpResponse.statusCode == 200
                    print(success ? "✅ Topics saved successfully" : "❌ Failed to save topics: \(httpResponse.statusCode)")
                    completion(success)
                } else {
                    completion(false)
                }
            }.resume()
        }
    
    private func saveTopicsAndGeneratePuzzles() {
        guard let userEmail = Auth.auth().currentUser?.email else {
            // Handle not signed in
            return
        }
        
        guard !selectedTopics.isEmpty else {
            // Handle no topics selected
            return
        }
        
        isSaving = true
        
        // Step 1: Save topics (your existing save logic)
        saveTopicsToFirebase(email: userEmail, topics: Array(selectedTopics)) { success in
            DispatchQueue.main.async {
                isSaving = false
                
                if success {
                    // Step 2: Generate puzzles
                    generateUserPuzzles(email: userEmail)
                } else {
                    // Handle save error
                }
            }
        }
    }
}
