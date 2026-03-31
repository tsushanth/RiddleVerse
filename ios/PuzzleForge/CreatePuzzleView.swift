//
//  CreatePuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI
import FirebaseAuth

struct CreatePuzzleView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var topic: String = ""
    @State private var numPuzzles: Int = 4
    @State private var selectedFormat: String = "Multiple Choice" // Default to Multiple Choice
    @State private var isSubmitting = false
    @State private var showSuccess = false

    private let formats = ["Multiple Choice", "Q&A", "Fill in the Blank"]
    
    // Expanded sample topics with more variety
    private let sampleTopics = [
        "Space Exploration", "Harry Potter", "Ancient History", "Math Puzzles",
        "Science Trivia", "World Geography", "Movie Classics", "Greek Mythology",
        "Ocean Life", "Dinosaurs", "Art History", "Music Theory", "Sports Facts",
        "Technology", "Cooking & Food", "Marvel Universe", "Nature & Wildlife",
        "Philosophy", "Literature", "Climate Science", "Psychology", "Fashion History",
        "Architecture", "Medical Science", "Astronomy", "Video Games", "Travel Destinations"
    ]

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Header Section
                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [.orange, .pink],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 80, height: 80)
                            
                            Image(systemName: "puzzlepiece.fill")
                                .font(.system(size: 40))
                                .foregroundColor(.white)
                        }
                        
                        Text("Create Custom Puzzle")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)
                        
                        Text("Generate personalized puzzles on any topic you love!")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    
                    VStack(spacing: 20) {
                        // Topic Input Section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "lightbulb.fill")
                                    .foregroundColor(.yellow)
                                Text("Puzzle Topic")
                                    .font(.headline)
                                    .fontWeight(.semibold)
                            }
                            
                            TextField("Enter your topic...", text: $topic)
                                .padding(16)
                                .background(Color(.systemGray6))
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(topic.isEmpty ? Color.clear : Color.orange, lineWidth: 2)
                                )
                        }
                        .padding(.horizontal)
                        
                        // Sample Topics Section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "sparkles")
                                    .foregroundColor(.purple)
                                Text("Popular Topics")
                                    .font(.headline)
                                    .fontWeight(.semibold)
                            }
                            .padding(.horizontal)
                            
                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(spacing: 12) {
                                    ForEach(sampleTopics, id: \.self) { sample in
                                        Button(action: {
                                            topic = sample
                                        }) {
                                            Text(sample)
                                                .font(.subheadline)
                                                .fontWeight(.medium)
                                                .foregroundColor(topic == sample ? .white : .primary)
                                                .padding(.horizontal, 16)
                                                .padding(.vertical, 10)
                                                .background(
                                                    RoundedRectangle(cornerRadius: 20)
                                                        .fill(topic == sample ?
                                                            LinearGradient(colors: [.blue, .purple], startPoint: .leading, endPoint: .trailing) :
                                                            LinearGradient(colors: [Color(.systemGray6)], startPoint: .leading, endPoint: .trailing)
                                                        )
                                                )
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 20)
                                                        .stroke(topic == sample ? Color.clear : Color.gray.opacity(0.3), lineWidth: 1)
                                                )
                                        }
                                        .buttonStyle(PlainButtonStyle())
                                    }
                                }
                                .padding(.horizontal)
                            }
                        }
                        
                        // Number of Puzzles Section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "number.circle.fill")
                                    .foregroundColor(.green)
                                Text("Number of Puzzles")
                                    .font(.headline)
                                    .fontWeight(.semibold)
                            }
                            
                            HStack {
                                Button(action: {
                                    numPuzzles = max(1, numPuzzles - 1)
                                }) {
                                    Image(systemName: "minus.circle.fill")
                                        .font(.title2)
                                        .foregroundColor(numPuzzles > 1 ? .red : .gray)
                                }
                                .disabled(numPuzzles <= 1)
                                
                                Spacer()
                                
                                VStack {
                                    Text("\(numPuzzles)")
                                        .font(.largeTitle)
                                        .fontWeight(.bold)
                                        .foregroundColor(.primary)
                                    
                                    Text("puzzles")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                                
                                Button(action: {
                                    numPuzzles = min(10, numPuzzles + 1)
                                }) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.title2)
                                        .foregroundColor(numPuzzles < 10 ? .green : .gray)
                                }
                                .disabled(numPuzzles >= 10)
                            }
                            .padding(.vertical, 8)
                        }
                        .padding(.horizontal)
                        
                        // Format Selection Section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "questionmark.circle.fill")
                                    .foregroundColor(.blue)
                                Text("Puzzle Format")
                                    .font(.headline)
                                    .fontWeight(.semibold)
                            }
                            .padding(.horizontal)
                            
                            VStack(spacing: 8) {
                                ForEach(formats, id: \.self) { format in
                                    Button(action: {
                                        selectedFormat = format
                                    }) {
                                        HStack {
                                            Image(systemName: selectedFormat == format ? "checkmark.circle.fill" : "circle")
                                                .foregroundColor(selectedFormat == format ? .orange : .gray)
                                                .font(.title3)
                                            
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(format)
                                                    .font(.headline)
                                                    .fontWeight(.semibold)
                                                    .foregroundColor(.primary)
                                                
                                                Text(getFormatDescription(format))
                                                    .font(.caption)
                                                    .foregroundColor(.secondary)
                                            }
                                            
                                            Spacer()
                                        }
                                        .padding(16)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12)
                                                .fill(selectedFormat == format ? Color.orange.opacity(0.1) : Color(.systemGray6))
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(selectedFormat == format ? Color.orange : Color.clear, lineWidth: 2)
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }
                            .padding(.horizontal)
                        }
                        
                        // Generate Button
                        Button(action: generatePuzzle) {
                            HStack {
                                if isSubmitting {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(0.8)
                                    Text("Creating Magic...")
                                        .fontWeight(.semibold)
                                } else {
                                    Image(systemName: "wand.and.stars")
                                        .font(.title3)
                                    Text("Generate Puzzle")
                                        .fontWeight(.semibold)
                                }
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(
                                        topic.trimmingCharacters(in: .whitespaces).isEmpty || isSubmitting ?
                                        LinearGradient(colors: [.gray], startPoint: .leading, endPoint: .trailing) :
                                        LinearGradient(colors: [.orange, .pink], startPoint: .leading, endPoint: .trailing)
                                    )
                            )
                            .shadow(color: .orange.opacity(0.3), radius: 8, x: 0, y: 4)
                        }
                        .disabled(topic.trimmingCharacters(in: .whitespaces).isEmpty || isSubmitting)
                        .padding(.horizontal)
                        .padding(.top, 8)
                    }
                }
                .padding(.bottom, 30)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .foregroundColor(.orange)
                }
            }
        }
        .alert("🎉 Puzzle Creation Started!", isPresented: $showSuccess) {
            Button("Awesome!", role: .cancel) {
                dismiss()
            }
        } message: {
            Text("Your custom puzzle set is being generated! Check back in a few minutes to see your creation.")
        }
    }
    
    private func getFormatDescription(_ format: String) -> String {
        switch format {
        case "Multiple Choice":
            return "Choose from 4 answer options • Great for trivia"
        case "Q&A":
            return "Type your own answer • Perfect for open-ended questions"
        case "Fill in the Blank":
            return "Complete the missing word • Test specific knowledge"
        default:
            return ""
        }
    }

    private func generatePuzzle() {
        guard !isSubmitting else { return }
        guard !topic.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        
        isSubmitting = true

        let payload: [String: Any] = [
            "topic": topic,
            "format": selectedFormat,
            "numPuzzles": numPuzzles,
            "userId": Auth.auth().currentUser?.email ?? "guest_user"
        ]

        guard let url = URL(string: "https://puzzleverseai.com/generate-riddle"),
              let body = try? JSONSerialization.data(withJSONObject: payload) else {
            isSubmitting = false
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body

        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                isSubmitting = false
                
                if let error = error {
                    print("❌ Error creating puzzle: \(error.localizedDescription)")
                    // You could add error handling here
                } else {
                    showSuccess = true
                }
            }
        }.resume()
    }
}
