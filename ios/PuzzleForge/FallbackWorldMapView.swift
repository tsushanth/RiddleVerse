import SwiftUI
import SVGView

// MARK: - Enhanced Views with SVG Support

struct EnhancedContinentSelectionView: View {
    let city: GeographyCity
    let showFeedback: Bool
    let feedbackMessage: String
    let questionsAnswered: Int
    let maxQuestions: Int
    let onContinentSelected: (Continent) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions)")
                .font(.headline)
                .foregroundColor(.white)
            
            // City Card (reuse existing)
            CityCardView(city: city)
            
            Text("Which continent is \(city.name) located in?")
                .font(.title2)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // ✅ NEW: Enhanced World Map with SVG
            EnhancedWorldMapView(onContinentSelected: onContinentSelected)
            
            // Feedback
            if showFeedback {
                GFeedbackView(message: feedbackMessage, isCorrect: feedbackMessage.contains("Correct"))
                    .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

struct EnhancedPrecisePlacementView: View {
    let city: GeographyCity
    let continent: Continent
    let selectedGridCell: GridCell?
    let showFeedback: Bool
    let lastResult: PlacementResult?
    let questionsAnswered: Int
    let maxQuestions: Int
    let onGridCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        VStack(spacing: 20) {
            // Progress
            Text("Question \(questionsAnswered + 1) of \(maxQuestions) - Precise Placement")
                .font(.headline)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // City Card (reuse existing)
            CityCardView(city: city)
            
            Text("Click on the grid section where \(city.name) is located in \(continent.rawValue)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // ✅ NEW: Enhanced Grid Map with SVG Background
            EnhancedGridMapView(
                continent: continent,
                selectedCell: selectedGridCell,
                correctCell: showFeedback ? gridSystem.getCorrectGridCell(for: city.id) : nil,
                onCellSelected: onGridCellSelected
            )
            
            // Feedback
            if showFeedback, let result = lastResult {
                GridFeedbackView(result: result)
                    .transition(.scale.combined(with: .opacity))
            }
            
            Spacer()
        }
        .padding()
        .animation(.easeInOut, value: showFeedback)
    }
}

struct EnhancedWorldMapView: View {
    let onContinentSelected: (Continent) -> Void
    @State private var selectedContinent: Continent?
    @State private var hoveredContinent: Continent?
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Tap on a continent")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            // World map container
            ZStack {
                // Ocean background
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.blue.opacity(0.4),
                                Color.blue.opacity(0.6),
                                Color.blue.opacity(0.5)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                    )
                
                // Continents positioned geographically with improved tap areas
                GeometryReader { geometry in
                    let mapWidth = geometry.size.width
                    let mapHeight = geometry.size.height
                    
                    Group {
                        // North America - Larger tap area
                        ContinentTapAreaView(
                            continent: .northAmerica,
                            isSelected: selectedContinent == .northAmerica,
                            isHovered: hoveredContinent == .northAmerica,
                            svgSize: CGSize(width: mapWidth * 0.22, height: mapHeight * 0.35),
                            tapAreaSize: CGSize(width: mapWidth * 0.28, height: mapHeight * 0.42),
                            position: CGPoint(x: mapWidth * 0.18, y: mapHeight * 0.25),
                            onTap: { handleContinentTap(.northAmerica) },
                            onHover: { hoveredContinent = $0 ? .northAmerica : nil }
                        )
                        
                        // South America
                        ContinentTapAreaView(
                            continent: .southAmerica,
                            isSelected: selectedContinent == .southAmerica,
                            isHovered: hoveredContinent == .southAmerica,
                            svgSize: CGSize(width: mapWidth * 0.15, height: mapHeight * 0.4),
                            tapAreaSize: CGSize(width: mapWidth * 0.21, height: mapHeight * 0.47),
                            position: CGPoint(x: mapWidth * 0.22, y: mapHeight * 0.65),
                            onTap: { handleContinentTap(.southAmerica) },
                            onHover: { hoveredContinent = $0 ? .southAmerica : nil }
                        )
                        
                        // Europe
                        ContinentTapAreaView(
                            continent: .europe,
                            isSelected: selectedContinent == .europe,
                            isHovered: hoveredContinent == .europe,
                            svgSize: CGSize(width: mapWidth * 0.18, height: mapHeight * 0.25),
                            tapAreaSize: CGSize(width: mapWidth * 0.24, height: mapHeight * 0.32),
                            position: CGPoint(x: mapWidth * 0.48, y: mapHeight * 0.28),
                            onTap: { handleContinentTap(.europe) },
                            onHover: { hoveredContinent = $0 ? .europe : nil }
                        )
                        
                        // Africa
                        ContinentTapAreaView(
                            continent: .africa,
                            isSelected: selectedContinent == .africa,
                            isHovered: hoveredContinent == .africa,
                            svgSize: CGSize(width: mapWidth * 0.16, height: mapHeight * 0.35),
                            tapAreaSize: CGSize(width: mapWidth * 0.22, height: mapHeight * 0.42),
                            position: CGPoint(x: mapWidth * 0.46, y: mapHeight * 0.6),
                            onTap: { handleContinentTap(.africa) },
                            onHover: { hoveredContinent = $0 ? .africa : nil }
                        )
                        
                        // Asia
                        ContinentTapAreaView(
                            continent: .asia,
                            isSelected: selectedContinent == .asia,
                            isHovered: hoveredContinent == .asia,
                            svgSize: CGSize(width: mapWidth * 0.32, height: mapHeight * 0.3),
                            tapAreaSize: CGSize(width: mapWidth * 0.38, height: mapHeight * 0.37),
                            position: CGPoint(x: mapWidth * 0.72, y: mapHeight * 0.35),
                            onTap: { handleContinentTap(.asia) },
                            onHover: { hoveredContinent = $0 ? .asia : nil }
                        )
                        
                        // Oceania
                        ContinentTapAreaView(
                            continent: .oceania,
                            isSelected: selectedContinent == .oceania,
                            isHovered: hoveredContinent == .oceania,
                            svgSize: CGSize(width: mapWidth * 0.14, height: mapHeight * 0.15),
                            tapAreaSize: CGSize(width: mapWidth * 0.20, height: mapHeight * 0.22),
                            position: CGPoint(x: mapWidth * 0.78, y: mapHeight * 0.75),
                            onTap: { handleContinentTap(.oceania) },
                            onHover: { hoveredContinent = $0 ? .oceania : nil }
                        )
                    }
                }
            }
            .frame(height: 280)
            .clipped()
            
            // ✅ NEW: Continent buttons as backup/alternative
            ContinentButtonsView(
                selectedContinent: selectedContinent,
                onContinentSelected: { handleContinentTap($0) }
            )
        }
        .padding()
    }
    
    private func handleContinentTap(_ continent: Continent) {
        print("🌍 Continent tapped: \(continent.rawValue)")
        
        withAnimation(.easeInOut(duration: 0.25)) {
            selectedContinent = continent
        }
        
        // Haptic feedback
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        
        // Delayed selection to show visual feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            onContinentSelected(continent)
            selectedContinent = nil
            hoveredContinent = nil
        }
    }
}

struct ContinentTapAreaView: View {
    let continent: Continent
    let isSelected: Bool
    let isHovered: Bool
    let svgSize: CGSize
    let tapAreaSize: CGSize
    let position: CGPoint
    let onTap: () -> Void
    let onHover: (Bool) -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                // Larger invisible tap area
                Rectangle()
                    .fill(Color.clear)
                    .frame(width: tapAreaSize.width, height: tapAreaSize.height)
                
                // Visual continent
                ContinentSVGView(
                    continent: continent,
                    isSelected: isSelected || isHovered
                )
                .frame(width: svgSize.width, height: svgSize.height)
                
                // ✅ NEW: Continent label with background
                VStack {
                    Spacer()
                    
                    Text(continent.displayName)
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(
                            Capsule()
                                .fill(Color.black.opacity(0.7))
                                .overlay(
                                    Capsule()
                                        .stroke(continent.color, lineWidth: 1)
                                )
                        )
                        .scaleEffect(isSelected || isHovered ? 1.1 : 0.9)
                        .opacity(isSelected || isHovered ? 1.0 : 0.8)
                }
                .frame(width: svgSize.width, height: svgSize.height)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .position(position)
        .onLongPressGesture(minimumDuration: 0, maximumDistance: .infinity, pressing: { pressing in
            onHover(pressing)
        }, perform: {})
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isSelected)
        .animation(.spring(response: 0.2, dampingFraction: 0.9), value: isHovered)
    }
}

extension Continent {
    var displayName: String {
        switch self {
        case .northAmerica: return "N. America"
        case .southAmerica: return "S. America"
        case .europe: return "Europe"
        case .africa: return "Africa"
        case .asia: return "Asia"
        case .oceania: return "Oceania"
        }
    }
}

struct ContinentButtonsView: View {
    let selectedContinent: Continent?
    let onContinentSelected: (Continent) -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            Text("Or tap a continent name:")
                .font(.caption)
                .foregroundColor(.white.opacity(0.7))
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 8) {
                ForEach(Continent.allCases, id: \.self) { continent in
                    Button(action: { onContinentSelected(continent) }) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(continent.color)
                                .frame(width: 8, height: 8)
                            
                            Text(continent.displayName)
                                .font(.caption)
                                .fontWeight(.medium)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(
                                    selectedContinent == continent
                                        ? continent.color.opacity(0.3)
                                        : Color.white.opacity(0.1)
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(
                                            selectedContinent == continent
                                                ? continent.color
                                                : Color.white.opacity(0.3),
                                            lineWidth: 1
                                        )
                                )
                        )
                        .scaleEffect(selectedContinent == continent ? 1.05 : 1.0)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: selectedContinent)
    }
}

struct ContinentSVGView: View {
    let continent: Continent
    let isSelected: Bool
    
    private var svgFileName: String {
        switch continent {
        case .northAmerica: return "north_america"
        case .southAmerica: return "south_america"
        case .europe: return "europe"
        case .africa: return "africa"
        case .asia: return "asia"
        case .oceania: return "oceania"
        }
    }
    
    var body: some View {
        ZStack {
            // Try to load SVG, fallback to shapes
            if let svgURL = Bundle.main.url(forResource: svgFileName, withExtension: "svg") {
                // ✅ ACTUAL SVG LOADING
                SVGView(contentsOf: svgURL)
                    .foregroundColor(continent.color.opacity(isSelected ? 1.0 : 0.8))
                    .scaleEffect(isSelected ? 1.05 : 1.0)
            } else {
                // Fallback: Better placeholder shapes
                continentPlaceholderShape
                    .foregroundColor(continent.color.opacity(isSelected ? 1.0 : 0.8))
                    .scaleEffect(isSelected ? 1.05 : 1.0)
            }
            
            // ✅ IMPROVED: Better selection highlight
            if isSelected {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.white, lineWidth: 3)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(8)
                    .scaleEffect(1.1)
                    .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true), value: isSelected)
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isSelected)
        .shadow(color: .black.opacity(isSelected ? 0.4 : 0.2), radius: isSelected ? 8 : 4)
    }
    
    @ViewBuilder
    private var continentPlaceholderShape: some View {
        switch continent {
        case .northAmerica:
            // More continent-like shape for North America
            Path { path in
                let rect = CGRect(x: 0, y: 0, width: 100, height: 100)
                path.move(to: CGPoint(x: rect.minX + 20, y: rect.minY + 10))
                path.addCurve(to: CGPoint(x: rect.maxX - 10, y: rect.minY + 30),
                             control1: CGPoint(x: rect.midX, y: rect.minY),
                             control2: CGPoint(x: rect.maxX - 20, y: rect.minY + 15))
                path.addLine(to: CGPoint(x: rect.maxX - 5, y: rect.maxY - 20))
                path.addCurve(to: CGPoint(x: rect.minX + 15, y: rect.maxY - 10),
                             control1: CGPoint(x: rect.maxX - 15, y: rect.maxY - 5),
                             control2: CGPoint(x: rect.midX, y: rect.maxY))
                path.closeSubpath()
            }
            .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
            
        case .southAmerica:
            // Triangle-like shape for South America
            Path { path in
                let rect = CGRect(x: 0, y: 0, width: 60, height: 120)
                path.move(to: CGPoint(x: rect.midX, y: rect.minY + 10))
                path.addLine(to: CGPoint(x: rect.maxX - 5, y: rect.midY))
                path.addLine(to: CGPoint(x: rect.midX + 10, y: rect.maxY - 10))
                path.addLine(to: CGPoint(x: rect.midX - 10, y: rect.maxY - 5))
                path.addLine(to: CGPoint(x: rect.minX + 5, y: rect.midY + 10))
                path.closeSubpath()
            }
            .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
            
        case .europe:
            // Small irregular shape for Europe
            RoundedRectangle(cornerRadius: 15)
                .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                
        case .africa:
            // Africa-like shape
            Path { path in
                let rect = CGRect(x: 0, y: 0, width: 80, height: 120)
                path.move(to: CGPoint(x: rect.midX, y: rect.minY + 5))
                path.addQuadCurve(to: CGPoint(x: rect.maxX - 10, y: rect.midY - 10),
                                 control: CGPoint(x: rect.maxX - 5, y: rect.minY + 30))
                path.addLine(to: CGPoint(x: rect.maxX - 5, y: rect.maxY - 20))
                path.addQuadCurve(to: CGPoint(x: rect.midX, y: rect.maxY - 5),
                                 control: CGPoint(x: rect.maxX - 15, y: rect.maxY))
                path.addQuadCurve(to: CGPoint(x: rect.minX + 10, y: rect.maxY - 20),
                                 control: CGPoint(x: rect.minX + 5, y: rect.maxY))
                path.addLine(to: CGPoint(x: rect.minX + 5, y: rect.midY - 10))
                path.addQuadCurve(to: CGPoint(x: rect.midX, y: rect.minY + 5),
                                 control: CGPoint(x: rect.minX, y: rect.minY + 30))
                path.closeSubpath()
            }
            .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
            
        case .asia:
            // Large irregular shape for Asia
            RoundedRectangle(cornerRadius: 25)
                .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                
        case .oceania:
            // Multiple small circles for islands
            HStack(spacing: 8) {
                Circle()
                    .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
                    .frame(width: 20, height: 20)
                Circle()
                    .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
                    .frame(width: 12, height: 12)
                Circle()
                    .fill(continent.color.opacity(isSelected ? 1.0 : 0.8))
                    .frame(width: 8, height: 8)
            }
        }
    }
}

struct LegendItem: View {
    let color: Color
    let label: String
    let icon: String
    
    var body: some View {
        HStack(spacing: 4) {
            if !icon.isEmpty {
                Image(systemName: icon)
                    .font(.caption2)
                    .foregroundColor(color)
            } else {
                Rectangle()
                    .fill(color.opacity(0.8))
                    .frame(width: 12, height: 12)
                    .cornerRadius(2)
            }
            
            Text(label)
                .font(.caption2)
                .foregroundColor(Color.primary)
        }
    }
}

struct EnhancedGridMapView: View {
    let continent: Continent
    let selectedCell: GridCell?
    let correctCell: GridCell?
    let onCellSelected: (GridCell) -> Void
    
    private let gridSystem = GeographyGridSystem.shared
    
    var body: some View {
        let dimensions = gridSystem.getGridDimensions(for: continent)
        
        VStack(spacing: 16) {
            Text("Grid: \(dimensions.rows)×\(dimensions.cols)")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
            
            ZStack {
                // SVG continent background for precise placement
                ContinentBackgroundSVG(continent: continent)
                    .frame(width: 300, height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                // Grid overlay
                VStack(spacing: 1) {
                    ForEach(0..<dimensions.rows, id: \.self) { row in
                        HStack(spacing: 1) {
                            ForEach(0..<dimensions.cols, id: \.self) { col in
                                let cell = GridCell(row: row, col: col)
                                
                                Button(action: { onCellSelected(cell) }) {
                                    Rectangle()
                                        .fill(getCellColor(for: cell).opacity(0.7))
                                        .overlay(
                                            Rectangle()
                                                .stroke(Color.white.opacity(0.8), lineWidth: 0.5)
                                        )
                                        .overlay(getCellContent(for: cell))
                                }
                                .disabled(correctCell != nil)
                            }
                        }
                    }
                }
                .frame(width: 300, height: 220)
            }
            
            // Legend
            if correctCell != nil {
                HStack(spacing: 20) {
                    LegendItem(color: .green, label: "Correct", icon: "")
                    LegendItem(color: .orange, label: "Your Choice", icon:"")
                }
            }
        }
    }
    
    private func getCellColor(for cell: GridCell) -> Color {
        if let correctCell = correctCell, cell == correctCell {
            return .green
        } else if let selectedCell = selectedCell, cell == selectedCell {
            return .orange
        } else {
            return .clear
        }
    }
    
    @ViewBuilder
    private func getCellContent(for cell: GridCell) -> some View {
        if let correctCell = correctCell, cell == correctCell {
            Image(systemName: "checkmark.circle.fill")
                .font(.caption)
                .foregroundColor(.white)
        } else if let selectedCell = selectedCell, cell == selectedCell, correctCell != nil && cell != correctCell {
            Image(systemName: "xmark.circle.fill")
                .font(.caption)
                .foregroundColor(.white)
        }
    }
}


struct GridCellView: View {
    let cell: GridCell
    let isSelected: Bool
    let isCorrect: Bool
    let showFeedback: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            Rectangle()
                .fill(getCellColor().opacity(0.6))
                .overlay(
                    Rectangle()
                        .stroke(Color.white.opacity(0.8), lineWidth: 1)
                )
                .overlay(getCellContent())
        }
        .disabled(showFeedback)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
        .animation(.easeInOut(duration: 0.3), value: isCorrect)
    }
    
    private func getCellColor() -> Color {
        if showFeedback {
            if isCorrect {
                return .green
            } else if isSelected {
                return .red
            }
        } else if isSelected {
            return .orange
        }
        return .clear
    }
    
    @ViewBuilder
    private func getCellContent() -> some View {
        if showFeedback {
            if isCorrect {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.white)
                    .background(Circle().fill(Color.green))
            } else if isSelected {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.white)
                    .background(Circle().fill(Color.red))
            }
        } else if isSelected {
            Circle()
                .fill(Color.orange.opacity(0.8))
                .frame(width: 20, height: 20)
                .overlay(
                    Circle()
                        .stroke(Color.white, lineWidth: 2)
                )
        }
    }
}

struct ContinentBackgroundSVG: View {
    let continent: Continent
    
    private var detailSvgFileName: String {
        switch continent {
        case .northAmerica: return "north_america_detailed"
        case .southAmerica: return "south_america_detailed"
        case .europe: return "europe_detailed"
        case .africa: return "africa_detailed"
        case .asia: return "asia_detailed"
        case .oceania: return "oceania_detailed"
        }
    }
    
    var body: some View {
        ZStack {
            // Base continent color
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    LinearGradient(
                        colors: [continent.color.opacity(0.5), continent.color.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            
            // Try to load detailed SVG for grid background
            if let svgURL = Bundle.main.url(forResource: detailSvgFileName, withExtension: "svg") {
                SVGView(contentsOf: svgURL)
                    .foregroundColor(.white.opacity(0.3))
                    .blendMode(.overlay)
            } else if let svgURL = Bundle.main.url(forResource: continent.rawValue.lowercased().replacingOccurrences(of: " ", with: "_"), withExtension: "svg") {
                // Fallback to main continent SVG
                SVGView(contentsOf: svgURL)
                    .foregroundColor(.white.opacity(0.2))
                    .blendMode(.overlay)
            } else {
                // Final fallback
                continent.color.opacity(0.2)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(continent.color.opacity(0.8), lineWidth: 2)
        )
    }
}

// MARK: - SVG Path Helper (for actual SVG implementation)

struct SVGPath: View {
    let pathString: String
    let fillColor: Color
    
    var body: some View {
        // This would use a proper SVG parsing library
        // For now, using placeholder
        Rectangle()
            .fill(fillColor)
    }
}

// MARK: - Usage Example in GeographyCitiesPuzzleView

// Replace the existing ContinentSelectionView with EnhancedContinentSelectionView
// Replace the existing PrecisePlacementView with EnhancedPrecisePlacementView
