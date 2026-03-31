//
//  CustomRiddlesListView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/10/25.
//


import SwiftUI
import FirebaseAuth

struct CustomRiddlesListView: View {
    @StateObject private var viewModel = HomeViewModel()
    @State private var selectedPuzzleId: String?
    @State private var showPuzzleView = false
    @State private var selectedTab: String = "All"
    @State private var expandedTopics: Set<String> = []
    @State private var searchText: String = ""
    
    private var filteredPuzzles: [CustomPuzzle] {
        let filteredList = selectedTab == "My"
        ? viewModel.customPuzzles.filter { $0.creator ==  "Sushanth Tiruvaipati"}
        : viewModel.customPuzzles
        
        return searchText.isEmpty
            ? filteredList
            : filteredList.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }
    
    private var isInChina: Bool {
        if let regionCode = Locale.current.regionCode {
            return regionCode == "CN"
        }
        return false
    }
    
    
    private var groupedPuzzles: [String: [CustomPuzzle]] {
        Dictionary(grouping: filteredPuzzles, by: { $0.name ?? "Uncategorized" })
    }
    
    private var headerView: some View {
        Text("📜 Custom Riddles")
            .font(.largeTitle)
            .bold()
            .padding(.top)
    }

    // Filter Picker View
    private var filterPicker: some View {
        Picker("Filter", selection: $selectedTab) {
            Text("All").tag(Tab.all)
            Text("My").tag(Tab.my)
        }
        .pickerStyle(SegmentedPickerStyle())
        .padding()
    }

    // Search Field View
    private var searchField: some View {
        TextField("🔎 Search Riddles", text: $searchText)
            .textFieldStyle(RoundedBorderTextFieldStyle())
            .padding(.horizontal)
    }

    // Topic Section View
    private func topicSection(_ topic: String) -> some View {
        Section(header:
            HStack {
                Text(topic)
                    .font(.headline)
                Spacer()
                Button(action: { toggleTopic(topic) }) {
                    Image(systemName: expandedTopics.contains(topic) ? "chevron.down" : "chevron.right")
                }
            }
        ) {
            if expandedTopics.contains(topic) {
                ForEach(groupedPuzzles[topic] ?? [], id: \.id) { puzzle in
                    puzzleRow(puzzle)
                }
            }
        }
    }

    // Puzzle Row View
    private func puzzleRow(_ puzzle: CustomPuzzle) -> some View {
        Button(action: { selectPuzzle(puzzle) }) {
            VStack(alignment: .leading) {
                Text(puzzle.name)
                    .font(.headline)
                Text("By \(puzzle.creator) • \(puzzle.creationTimeFormatted)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .onAppear {
            if isLastItem(puzzle, in: groupedPuzzles[puzzle.name] ?? []) {
                viewModel.loadMoreCustomPuzzles()
            }
        }
    }

    // Loading Overlay View
    private var loadingOverlay: some View {
        Group {
            if viewModel.isLoading && viewModel.customPuzzles.isEmpty {
                ProgressView()
            }
        }
    }

    // Puzzle Detail Sheet
    private var puzzleDetailSheet: some View {
        Group {
            if let puzzleId = selectedPuzzleId {
                EmptyView()
            } else {
                EmptyView()
            }
        }
    }

    var body: some View {
        VStack {
            headerView
            filterPicker
            searchField
            
            List {
                ForEach(groupedPuzzles.keys.sorted(), id: \.self) { topic in
                    topicSection(topic)
                }
            }
            .overlay(loadingOverlay)
        }
        .onAppear {
            if viewModel.customPuzzles.isEmpty {
                viewModel.fetchCustomPuzzles()
            }
        }
        .sheet(isPresented: $showPuzzleView, content: {
            puzzleDetailSheet
        })
    }

    // Add this enum if you don't have it already
    enum Tab: String, CaseIterable, Identifiable {
        case all = "All"
        case my = "My"
        var id: String { self.rawValue }
    }


    private func isLastItem(_ puzzle: CustomPuzzle, in puzzles: [CustomPuzzle]) -> Bool {
        guard let last = puzzles.last else { return false }
        return puzzle.id == last.id
    }

    private func toggleTopic(_ topic: String) {
        if expandedTopics.contains(topic) {
            expandedTopics.remove(topic)
        } else {
            expandedTopics.insert(topic)
        }
    }

    private func selectPuzzle(_ puzzle: CustomPuzzle) {
        selectedPuzzleId = puzzle.id
        showPuzzleView = true
    }
}
