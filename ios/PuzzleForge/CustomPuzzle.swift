//
//  CustomPuzzle.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//
import Foundation

struct CustomPuzzle: Identifiable {
    let id: String
    let name: String
    let format: String
    let creator: String
    let createdAtString: String

    var creationTimeFormatted: String {
        // Parse ISO string
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: createdAtString) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateStyle = .medium
            displayFormatter.timeStyle = .short
            return displayFormatter.string(from: date)
        } else {
            return createdAtString // fallback
        }
    }
    
    static func == (lhs: CustomPuzzle, rhs: CustomPuzzle) -> Bool {
            return lhs.id == rhs.id
    }

    init(json: [String: Any]) {
        self.id = json["id"] as? String ?? UUID().uuidString
        self.name = json["name"] as? String ?? "Unknown"
        self.format = json["format"] as? String ?? "Unknown"
        self.creator = json["creator"] as? String ?? "Anonymous"
        self.createdAtString = json["createdAt"] as? String ?? ""
    }
    
    init(from response: CustomPuzzleSetResponse) {
        self.id = response.puzzleData.puzzleData.puzzles.first?.puzzleId ?? UUID().uuidString
        self.name = response.topic
        self.format = response.format
        self.creator = response.userId
        self.createdAtString = response.createdAt
    }
    
    static func decode(from jsonData: Data) -> CustomPuzzle? {
        let decoder = JSONDecoder()
        
        do {
            let response = try decoder.decode(CustomPuzzleSetResponse.self, from: jsonData)
            return CustomPuzzle(from: response)
        } catch {
            print("🔴 CustomPuzzle decoding failed: \(error)")
            
            // Fallback to dictionary parsing
            if let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
                return CustomPuzzle(json: json)
            }
            
            return nil
        }
    }
}
