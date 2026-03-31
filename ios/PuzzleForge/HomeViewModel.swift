//
//  HomeViewModel.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//
import SwiftUI
import Foundation

class HomeViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var customPuzzles: [CustomPuzzle] = []
    
    @Published var dailyTopics: [String] = []
    @Published var currentPage: Int = 1
    @Published var totalPages: Int = 1
    @Published var totalCount: Int?
    
    
    

    func fetchDailyTopics(userEmail: String) {
        guard !userEmail.isEmpty else { return }

        let urlString = "https://puzzleverseai.com/list-user-daily-topics?email=\(userEmail)"
        guard let url = URL(string: urlString) else { return }

        URLSession.shared.dataTask(with: url) { data, _, error in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let topicsArray = json["topics"] as? [String] else {
                print("❌ Failed to parse topics")
                return
            }

            DispatchQueue.main.async {
                self.dailyTopics = topicsArray
            }
        }.resume()
    }


    func fetchCustomPuzzles(offset: Int = 0, limit: Int = 40) {
        guard let url = URL(string: "https://puzzleverseai.com/list-custom-puzzles?limit=\(limit)&offset=\(offset)") else { return }

        URLSession.shared.dataTask(with: url) { data, _, error in
            if let error = error {
                print("❌ Error fetching custom puzzles: \(error.localizedDescription)")
                return
            }

            guard let data = data else {
                print("❌ No data received.")
                return
            }

            // Log the raw response as String for debugging
            if let rawResponse = String(data: data, encoding: .utf8) {
                print("📦 Raw response body: \(rawResponse)")
            }

            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let puzzleArray = json["puzzleData"] as? [[String: Any]] else {
                print("❌ Failed to parse JSON.")
                return
            }

            // Optional: parse page, totalPages, totalCount
            let page = json["page"] as? Int ?? 1
            let totalPages = json["totalPages"] as? Int ?? 1
            let totalCount = json["totalCount"] as? Int ?? 0

            let puzzles = puzzleArray.map { CustomPuzzle(json: $0) }

            DispatchQueue.main.async {
                if offset == 0 {
                    // First page — replace
                    self.customPuzzles = puzzles
                } else {
                    // Loading more — append
                    self.customPuzzles += puzzles
                }

                self.currentPage = page
                self.totalPages = totalPages
                self.totalCount = totalCount
            }
        }.resume()
    }
    
    
    
    func loadMoreCustomPuzzles() {
        guard currentPage < totalPages else { return }
        fetchCustomPuzzles(offset: customPuzzles.count)
    }

}
