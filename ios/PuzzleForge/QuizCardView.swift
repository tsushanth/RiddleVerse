//
//  QuizCardView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//
import SwiftUI

struct QuizCardView: View {
    let title: String
    let subtitle: String
    let imageName: String
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.purple.opacity(0.9))
                    .frame(width: 180, height: 160)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    Spacer()
                    
                    Text("Start Quiz")
                        .font(.caption)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(16)
                }
                .padding()
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}
