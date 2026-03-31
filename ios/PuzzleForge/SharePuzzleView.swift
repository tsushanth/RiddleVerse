//
//  SharePuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/2/25.
//
import SwiftUI
import FirebaseAuth

struct SharePuzzleView: View {
    @Binding var isPresented: Bool
    @State private var recipientEmail: String = ""
    @State private var recipientName: String = ""
    let puzzleId: String
    let onShare: (String, String, String) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Share Puzzle")
                .font(.headline)
            
            TextField("Recipient Email", text: $recipientEmail)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .disableAutocorrection(true)
            
            TextField("Recipient Name", text: $recipientName)
                .textFieldStyle(RoundedBorderTextFieldStyle())
            
            HStack(spacing: 20) {
                Button("Cancel") {
                    isPresented = false
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.gray.opacity(0.2))
                .cornerRadius(10)
                
                Button("Share") {
                    let sender = Auth.auth().currentUser?.displayName ??
                                 Auth.auth().currentUser?.email ?? "anonymous"
                    onShare(recipientEmail, recipientName, sender)
                    isPresented = false
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)
                .disabled(recipientEmail.isEmpty || !recipientEmail.contains("@"))
            }
        }
        .padding()
        .frame(width: 300)
        .background(Color.white)
        .cornerRadius(20)
        .shadow(radius: 10)
    }
}
