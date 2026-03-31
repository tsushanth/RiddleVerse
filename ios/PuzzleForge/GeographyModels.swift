import SwiftUI

// MARK: - Geography Models
enum Continent: String, CaseIterable, Codable {
    case northAmerica = "North America"
    case southAmerica = "South America"
    case europe = "Europe"
    case africa = "Africa"
    case asia = "Asia"
    case oceania = "Oceania"
    
    var color: Color {
        switch self {
        case .northAmerica: return Color(red: 0.30, green: 0.69, blue: 0.31)
        case .southAmerica: return Color(red: 1.0, green: 0.60, blue: 0.0)
        case .europe: return Color(red: 0.13, green: 0.59, blue: 0.95)
        case .africa: return Color(red: 0.91, green: 0.12, blue: 0.39)
        case .asia: return Color(red: 0.61, green: 0.15, blue: 0.69)
        case .oceania: return Color(red: 0.0, green: 0.74, blue: 0.83)
        }
    }
    
    var region: String {
        switch self {
        case .northAmerica: return "Northern America"
        case .southAmerica: return "South America"
        case .europe: return "Europe"
        case .africa: return "Africa"
        case .asia: return "Asia"
        case .oceania: return "Oceania"
        }
    }
}

struct GeographyCity {
    let id: String
    let name: String
    let country: String
    let continent: Continent
    let latitude: Double
    let longitude: Double
    let isCapital: Bool
    let flag: String
}

// MARK: - Enhanced Geography Country Model
struct GeographyCountry: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let continent: Continent
    let latitude: Double
    let longitude: Double
    let isLandlocked: Bool
    let flag: String
    let capital: String
    
    // Enhanced properties for world expansion
    let population: Int
    let area: Double // in km²
    let languages: [String]
    let currencies: [String]
    let region: String // Sub-region within continent
    let difficulty: CountryDifficulty
    
    enum CountryDifficulty: String, Codable, CaseIterable {
        case easy = "Easy"
        case medium = "Medium"
        case hard = "Hard"
        case expert = "Expert"
    }
    
    // Convenience initializer for backward compatibility
    init(id: String, name: String, continent: Continent, latitude: Double, longitude: Double, isLandlocked: Bool, flag: String, capital: String) {
        self.id = id
        self.name = name
        self.continent = continent
        self.latitude = latitude
        self.longitude = longitude
        self.isLandlocked = isLandlocked
        self.flag = flag
        self.capital = capital
        
        // Default values for enhanced properties
        self.population = 1000000
        self.area = 100000
        self.languages = ["English"]
        self.currencies = ["USD"]
        self.region = continent.region
        self.difficulty = .medium
    }
    
    // Full initializer for enhanced countries
    init(id: String, name: String, continent: Continent, latitude: Double, longitude: Double, isLandlocked: Bool, flag: String, capital: String, population: Int, area: Double, languages: [String], currencies: [String], region: String, difficulty: CountryDifficulty) {
        self.id = id
        self.name = name
        self.continent = continent
        self.latitude = latitude
        self.longitude = longitude
        self.isLandlocked = isLandlocked
        self.flag = flag
        self.capital = capital
        self.population = population
        self.area = area
        self.languages = languages
        self.currencies = currencies
        self.region = region
        self.difficulty = difficulty
    }
}

struct GridCell: Equatable, Hashable {
    let row: Int
    let col: Int
    
    var id: String { "\(row)_\(col)" }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(row)
        hasher.combine(col)
    }
}

struct PlacementResult {
    let score: Int
    let distance: Double
    let accuracy: String
    let isCorrect: Bool
    let feedback: String
    
    // Convenience initializer for backward compatibility
    init(score: Int, distance: Double, accuracy: String, isCorrect: Bool) {
        self.score = score
        self.distance = distance
        self.accuracy = accuracy
        self.isCorrect = isCorrect
        self.feedback = ""
    }
    
    // Enhanced initializer
    init(score: Int, distance: Double, accuracy: String, isCorrect: Bool, feedback: String) {
        self.score = score
        self.distance = distance
        self.accuracy = accuracy
        self.isCorrect = isCorrect
        self.feedback = feedback
    }
}

enum GameStage {
    case instructions
    case continentSelection
    case precisePlacement
    case completed
}

// MARK: - Geography Database
class GeographyCityDatabase: ObservableObject {
    static let shared = GeographyCityDatabase()
    
    let cities: [GeographyCity] = [
        // NORTH AMERICA
        GeographyCity(id: "new_york", name: "New York", country: "United States", continent: .northAmerica, latitude: 40.7128, longitude: -74.0060, isCapital: false, flag: "🇺🇸"),
        GeographyCity(id: "los_angeles", name: "Los Angeles", country: "United States", continent: .northAmerica, latitude: 34.0522, longitude: -118.2437, isCapital: false, flag: "🇺🇸"),
        GeographyCity(id: "mexico_city", name: "Mexico City", country: "Mexico", continent: .northAmerica, latitude: 19.4326, longitude: -99.1332, isCapital: true, flag: "🇲🇽"),
        GeographyCity(id: "toronto", name: "Toronto", country: "Canada", continent: .northAmerica, latitude: 43.6532, longitude: -79.3832, isCapital: false, flag: "🇨🇦"),
        GeographyCity(id: "chicago", name: "Chicago", country: "United States", continent: .northAmerica, latitude: 41.8781, longitude: -87.6298, isCapital: false, flag: "🇺🇸"),
        
        // SOUTH AMERICA
        GeographyCity(id: "sao_paulo", name: "São Paulo", country: "Brazil", continent: .southAmerica, latitude: -23.5505, longitude: -46.6333, isCapital: false, flag: "🇧🇷"),
        GeographyCity(id: "rio_janeiro", name: "Rio de Janeiro", country: "Brazil", continent: .southAmerica, latitude: -22.9068, longitude: -43.1729, isCapital: false, flag: "🇧🇷"),
        GeographyCity(id: "buenos_aires", name: "Buenos Aires", country: "Argentina", continent: .southAmerica, latitude: -34.6118, longitude: -58.3960, isCapital: true, flag: "🇦🇷"),
        GeographyCity(id: "lima", name: "Lima", country: "Peru", continent: .southAmerica, latitude: -12.0464, longitude: -77.0428, isCapital: true, flag: "🇵🇪"),
        GeographyCity(id: "bogota", name: "Bogotá", country: "Colombia", continent: .southAmerica, latitude: 4.7110, longitude: -74.0721, isCapital: true, flag: "🇨🇴"),
        
        // EUROPE
        GeographyCity(id: "london", name: "London", country: "United Kingdom", continent: .europe, latitude: 51.5074, longitude: -0.1278, isCapital: true, flag: "🇬🇧"),
        GeographyCity(id: "paris", name: "Paris", country: "France", continent: .europe, latitude: 48.8566, longitude: 2.3522, isCapital: true, flag: "🇫🇷"),
        GeographyCity(id: "berlin", name: "Berlin", country: "Germany", continent: .europe, latitude: 52.5200, longitude: 13.4050, isCapital: true, flag: "🇩🇪"),
        GeographyCity(id: "rome", name: "Rome", country: "Italy", continent: .europe, latitude: 41.9028, longitude: 12.4964, isCapital: true, flag: "🇮🇹"),
        GeographyCity(id: "madrid", name: "Madrid", country: "Spain", continent: .europe, latitude: 40.4168, longitude: -3.7038, isCapital: true, flag: "🇪🇸"),
        
        // AFRICA
        GeographyCity(id: "cairo", name: "Cairo", country: "Egypt", continent: .africa, latitude: 30.0444, longitude: 31.2357, isCapital: true, flag: "🇪🇬"),
        GeographyCity(id: "lagos", name: "Lagos", country: "Nigeria", continent: .africa, latitude: 6.5244, longitude: 3.3792, isCapital: false, flag: "🇳🇬"),
        GeographyCity(id: "johannesburg", name: "Johannesburg", country: "South Africa", continent: .africa, latitude: -26.2041, longitude: 28.0473, isCapital: false, flag: "🇿🇦"),
        GeographyCity(id: "casablanca", name: "Casablanca", country: "Morocco", continent: .africa, latitude: 33.5731, longitude: -7.5898, isCapital: false, flag: "🇲🇦"),
        GeographyCity(id: "nairobi", name: "Nairobi", country: "Kenya", continent: .africa, latitude: -1.2864, longitude: 36.8172, isCapital: true, flag: "🇰🇪"),
        
        // ASIA
        GeographyCity(id: "tokyo", name: "Tokyo", country: "Japan", continent: .asia, latitude: 35.6762, longitude: 139.6503, isCapital: true, flag: "🇯🇵"),
        GeographyCity(id: "beijing", name: "Beijing", country: "China", continent: .asia, latitude: 39.9042, longitude: 116.4074, isCapital: true, flag: "🇨🇳"),
        GeographyCity(id: "mumbai", name: "Mumbai", country: "India", continent: .asia, latitude: 19.0760, longitude: 72.8777, isCapital: false, flag: "🇮🇳"),
        GeographyCity(id: "shanghai", name: "Shanghai", country: "China", continent: .asia, latitude: 31.2304, longitude: 121.4737, isCapital: false, flag: "🇨🇳"),
        GeographyCity(id: "delhi", name: "Delhi", country: "India", continent: .asia, latitude: 28.7041, longitude: 77.1025, isCapital: true, flag: "🇮🇳"),
        
        // OCEANIA
        GeographyCity(id: "sydney", name: "Sydney", country: "Australia", continent: .oceania, latitude: -33.8688, longitude: 151.2093, isCapital: false, flag: "🇦🇺"),
        GeographyCity(id: "melbourne", name: "Melbourne", country: "Australia", continent: .oceania, latitude: -37.8136, longitude: 144.9631, isCapital: false, flag: "🇦🇺")
    ]
    
    func getRandomCity() -> GeographyCity {
        return cities.randomElement() ?? cities[0]
    }
    
    func getCitiesByContinent(_ continent: Continent) -> [GeographyCity] {
        return cities.filter { $0.continent == continent }
    }
}

// MARK: - Enhanced Geography Country Database
class GeographyCountryDatabase: ObservableObject {
    static let shared = GeographyCountryDatabase()
    
    @Published private(set) var allCountries: [GeographyCountry] = []
    @Published private(set) var countriesByContinent: [Continent: [GeographyCountry]] = [:]
    @Published private(set) var countriesByDifficulty: [GeographyCountry.CountryDifficulty: [GeographyCountry]] = [:]
    
    // Legacy countries array for backward compatibility
    private(set) var countries: [GeographyCountry] = []
    
    init() {
        loadCountries()
        organizeCountries()
        setupLegacyCountries()
    }
    
    private func loadCountries() {
        allCountries = [
            // NORTH AMERICA (23 countries)
            GeographyCountry(id: "US", name: "United States", continent: .northAmerica, latitude: 39.8283, longitude: -98.5795, isLandlocked: false, flag: "🇺🇸", capital: "Washington, D.C.", population: 331900000, area: 9833517, languages: ["English"], currencies: ["USD"], region: "Northern America", difficulty: .easy),
            GeographyCountry(id: "CA", name: "Canada", continent: .northAmerica, latitude: 56.1304, longitude: -106.3468, isLandlocked: false, flag: "🇨🇦", capital: "Ottawa", population: 38000000, area: 9984670, languages: ["English", "French"], currencies: ["CAD"], region: "Northern America", difficulty: .easy),
            GeographyCountry(id: "MX", name: "Mexico", continent: .northAmerica, latitude: 23.6345, longitude: -102.5528, isLandlocked: false, flag: "🇲🇽", capital: "Mexico City", population: 128900000, area: 1964375, languages: ["Spanish"], currencies: ["MXN"], region: "Central America", difficulty: .easy),
            GeographyCountry(id: "GT", name: "Guatemala", continent: .northAmerica, latitude: 15.7835, longitude: -90.2308, isLandlocked: false, flag: "🇬🇹", capital: "Guatemala City", population: 17600000, area: 108889, languages: ["Spanish"], currencies: ["GTQ"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "BZ", name: "Belize", continent: .northAmerica, latitude: 17.1899, longitude: -88.4976, isLandlocked: false, flag: "🇧🇿", capital: "Belmopan", population: 397000, area: 22966, languages: ["English"], currencies: ["BZD"], region: "Central America", difficulty: .hard),
            GeographyCountry(id: "SV", name: "El Salvador", continent: .northAmerica, latitude: 13.7942, longitude: -88.8965, isLandlocked: false, flag: "🇸🇻", capital: "San Salvador", population: 6486000, area: 21041, languages: ["Spanish"], currencies: ["USD"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "HN", name: "Honduras", continent: .northAmerica, latitude: 15.2000, longitude: -86.2419, isLandlocked: false, flag: "🇭🇳", capital: "Tegucigalpa", population: 9905000, area: 112492, languages: ["Spanish"], currencies: ["HNL"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "NI", name: "Nicaragua", continent: .northAmerica, latitude: 12.8654, longitude: -85.2072, isLandlocked: false, flag: "🇳🇮", capital: "Managua", population: 6625000, area: 130373, languages: ["Spanish"], currencies: ["NIO"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "CR", name: "Costa Rica", continent: .northAmerica, latitude: 9.7489, longitude: -83.7534, isLandlocked: false, flag: "🇨🇷", capital: "San José", population: 5094000, area: 51100, languages: ["Spanish"], currencies: ["CRC"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "PA", name: "Panama", continent: .northAmerica, latitude: 8.5380, longitude: -80.7821, isLandlocked: false, flag: "🇵🇦", capital: "Panama City", population: 4315000, area: 75417, languages: ["Spanish"], currencies: ["PAB", "USD"], region: "Central America", difficulty: .medium),
            GeographyCountry(id: "CU", name: "Cuba", continent: .northAmerica, latitude: 21.5218, longitude: -77.7812, isLandlocked: false, flag: "🇨🇺", capital: "Havana", population: 11327000, area: 109884, languages: ["Spanish"], currencies: ["CUP"], region: "Caribbean", difficulty: .easy),
            GeographyCountry(id: "JM", name: "Jamaica", continent: .northAmerica, latitude: 18.1096, longitude: -77.2975, isLandlocked: false, flag: "🇯🇲", capital: "Kingston", population: 2961000, area: 10991, languages: ["English"], currencies: ["JMD"], region: "Caribbean", difficulty: .medium),
            GeographyCountry(id: "HT", name: "Haiti", continent: .northAmerica, latitude: 18.9712, longitude: -72.2852, isLandlocked: false, flag: "🇭🇹", capital: "Port-au-Prince", population: 11403000, area: 27750, languages: ["French", "Haitian Creole"], currencies: ["HTG"], region: "Caribbean", difficulty: .medium),
            GeographyCountry(id: "DO", name: "Dominican Republic", continent: .northAmerica, latitude: 18.7357, longitude: -70.1627, isLandlocked: false, flag: "🇩🇴", capital: "Santo Domingo", population: 10848000, area: 48671, languages: ["Spanish"], currencies: ["DOP"], region: "Caribbean", difficulty: .medium),
            GeographyCountry(id: "BS", name: "Bahamas", continent: .northAmerica, latitude: 25.0343, longitude: -77.3963, isLandlocked: false, flag: "🇧🇸", capital: "Nassau", population: 393000, area: 13943, languages: ["English"], currencies: ["BSD"], region: "Caribbean", difficulty: .hard),
            GeographyCountry(id: "BB", name: "Barbados", continent: .northAmerica, latitude: 13.1939, longitude: -59.5432, isLandlocked: false, flag: "🇧🇧", capital: "Bridgetown", population: 287000, area: 430, languages: ["English"], currencies: ["BBD"], region: "Caribbean", difficulty: .hard),
            GeographyCountry(id: "TT", name: "Trinidad and Tobago", continent: .northAmerica, latitude: 10.6918, longitude: -61.2225, isLandlocked: false, flag: "🇹🇹", capital: "Port of Spain", population: 1399000, area: 5128, languages: ["English"], currencies: ["TTD"], region: "Caribbean", difficulty: .hard),
            
            // SOUTH AMERICA (12 countries)
            GeographyCountry(id: "BR", name: "Brazil", continent: .southAmerica, latitude: -14.2350, longitude: -51.9253, isLandlocked: false, flag: "🇧🇷", capital: "Brasília", population: 212600000, area: 8514877, languages: ["Portuguese"], currencies: ["BRL"], region: "South America", difficulty: .easy),
            GeographyCountry(id: "AR", name: "Argentina", continent: .southAmerica, latitude: -38.4161, longitude: -63.6167, isLandlocked: false, flag: "🇦🇷", capital: "Buenos Aires", population: 45200000, area: 2780400, languages: ["Spanish"], currencies: ["ARS"], region: "South America", difficulty: .easy),
            GeographyCountry(id: "CO", name: "Colombia", continent: .southAmerica, latitude: 4.7110, longitude: -74.0721, isLandlocked: false, flag: "🇨🇴", capital: "Bogotá", population: 50880000, area: 1141748, languages: ["Spanish"], currencies: ["COP"], region: "South America", difficulty: .easy),
            GeographyCountry(id: "PE", name: "Peru", continent: .southAmerica, latitude: -12.0464, longitude: -77.0428, isLandlocked: false, flag: "🇵🇪", capital: "Lima", population: 32970000, area: 1285216, languages: ["Spanish"], currencies: ["PEN"], region: "South America", difficulty: .easy),
            GeographyCountry(id: "VE", name: "Venezuela", continent: .southAmerica, latitude: 6.4238, longitude: -66.5897, isLandlocked: false, flag: "🇻🇪", capital: "Caracas", population: 28436000, area: 912050, languages: ["Spanish"], currencies: ["VES"], region: "South America", difficulty: .medium),
            GeographyCountry(id: "CL", name: "Chile", continent: .southAmerica, latitude: -35.6751, longitude: -71.5430, isLandlocked: false, flag: "🇨🇱", capital: "Santiago", population: 19116000, area: 756102, languages: ["Spanish"], currencies: ["CLP"], region: "South America", difficulty: .easy),
            GeographyCountry(id: "EC", name: "Ecuador", continent: .southAmerica, latitude: -1.8312, longitude: -78.1834, isLandlocked: false, flag: "🇪🇨", capital: "Quito", population: 17640000, area: 283561, languages: ["Spanish"], currencies: ["USD"], region: "South America", difficulty: .medium),
            GeographyCountry(id: "BO", name: "Bolivia", continent: .southAmerica, latitude: -16.2902, longitude: -63.5887, isLandlocked: true, flag: "🇧🇴", capital: "La Paz", population: 11670000, area: 1098581, languages: ["Spanish"], currencies: ["BOB"], region: "South America", difficulty: .medium),
            GeographyCountry(id: "PY", name: "Paraguay", continent: .southAmerica, latitude: -23.4425, longitude: -58.4438, isLandlocked: true, flag: "🇵🇾", capital: "Asunción", population: 7133000, area: 406752, languages: ["Spanish"], currencies: ["PYG"], region: "South America", difficulty: .medium),
            GeographyCountry(id: "UY", name: "Uruguay", continent: .southAmerica, latitude: -32.5228, longitude: -55.7658, isLandlocked: false, flag: "🇺🇾", capital: "Montevideo", population: 3474000, area: 176215, languages: ["Spanish"], currencies: ["UYU"], region: "South America", difficulty: .medium),
            GeographyCountry(id: "GY", name: "Guyana", continent: .southAmerica, latitude: 4.8604, longitude: -58.9302, isLandlocked: false, flag: "🇬🇾", capital: "Georgetown", population: 787000, area: 214969, languages: ["English"], currencies: ["GYD"], region: "South America", difficulty: .hard),
            GeographyCountry(id: "SR", name: "Suriname", continent: .southAmerica, latitude: 3.9193, longitude: -56.0278, isLandlocked: false, flag: "🇸🇷", capital: "Paramaribo", population: 586000, area: 163820, languages: ["Dutch"], currencies: ["SRD"], region: "South America", difficulty: .hard),
            
            // EUROPE (20 major countries - can be expanded)
            GeographyCountry(id: "RU", name: "Russia", continent: .europe, latitude: 61.5240, longitude: 105.3188, isLandlocked: false, flag: "🇷🇺", capital: "Moscow", population: 145934000, area: 17098242, languages: ["Russian"], currencies: ["RUB"], region: "Eastern Europe", difficulty: .easy),
            GeographyCountry(id: "DE", name: "Germany", continent: .europe, latitude: 51.1657, longitude: 10.4515, isLandlocked: false, flag: "🇩🇪", capital: "Berlin", population: 83784000, area: 357022, languages: ["German"], currencies: ["EUR"], region: "Central Europe", difficulty: .easy),
            GeographyCountry(id: "FR", name: "France", continent: .europe, latitude: 46.6034, longitude: 2.2137, isLandlocked: false, flag: "🇫🇷", capital: "Paris", population: 65274000, area: 551695, languages: ["French"], currencies: ["EUR"], region: "Western Europe", difficulty: .easy),
            GeographyCountry(id: "IT", name: "Italy", continent: .europe, latitude: 41.9028, longitude: 12.4964, isLandlocked: false, flag: "🇮🇹", capital: "Rome", population: 60462000, area: 301340, languages: ["Italian"], currencies: ["EUR"], region: "Southern Europe", difficulty: .easy),
            GeographyCountry(id: "ES", name: "Spain", continent: .europe, latitude: 40.4637, longitude: -3.7492, isLandlocked: false, flag: "🇪🇸", capital: "Madrid", population: 46755000, area: 505990, languages: ["Spanish"], currencies: ["EUR"], region: "Southern Europe", difficulty: .easy),
            GeographyCountry(id: "GB", name: "United Kingdom", continent: .europe, latitude: 55.3781, longitude: -3.4360, isLandlocked: false, flag: "🇬🇧", capital: "London", population: 67886000, area: 243610, languages: ["English"], currencies: ["GBP"], region: "Northern Europe", difficulty: .easy),
            GeographyCountry(id: "PL", name: "Poland", continent: .europe, latitude: 51.9194, longitude: 19.1451, isLandlocked: false, flag: "🇵🇱", capital: "Warsaw", population: 37847000, area: 312696, languages: ["Polish"], currencies: ["PLN"], region: "Central Europe", difficulty: .easy),
            GeographyCountry(id: "UA", name: "Ukraine", continent: .europe, latitude: 48.3794, longitude: 31.1656, isLandlocked: false, flag: "🇺🇦", capital: "Kyiv", population: 44134000, area: 603550, languages: ["Ukrainian"], currencies: ["UAH"], region: "Eastern Europe", difficulty: .medium),
            GeographyCountry(id: "NL", name: "Netherlands", continent: .europe, latitude: 52.1326, longitude: 5.2913, isLandlocked: false, flag: "🇳🇱", capital: "Amsterdam", population: 17134000, area: 41850, languages: ["Dutch"], currencies: ["EUR"], region: "Western Europe", difficulty: .medium),
            GeographyCountry(id: "BE", name: "Belgium", continent: .europe, latitude: 50.5039, longitude: 4.4699, isLandlocked: false, flag: "🇧🇪", capital: "Brussels", population: 11590000, area: 30528, languages: ["Dutch", "French", "German"], currencies: ["EUR"], region: "Western Europe", difficulty: .medium),
            GeographyCountry(id: "GR", name: "Greece", continent: .europe, latitude: 39.0742, longitude: 21.8243, isLandlocked: false, flag: "🇬🇷", capital: "Athens", population: 10423000, area: 131957, languages: ["Greek"], currencies: ["EUR"], region: "Southern Europe", difficulty: .medium),
            GeographyCountry(id: "PT", name: "Portugal", continent: .europe, latitude: 39.3999, longitude: -8.2245, isLandlocked: false, flag: "🇵🇹", capital: "Lisbon", population: 10196000, area: 92090, languages: ["Portuguese"], currencies: ["EUR"], region: "Southern Europe", difficulty: .medium),
            GeographyCountry(id: "CZ", name: "Czech Republic", continent: .europe, latitude: 49.8175, longitude: 15.4730, isLandlocked: true, flag: "🇨🇿", capital: "Prague", population: 10708000, area: 78867, languages: ["Czech"], currencies: ["CZK"], region: "Central Europe", difficulty: .medium),
            GeographyCountry(id: "HU", name: "Hungary", continent: .europe, latitude: 47.1625, longitude: 19.5033, isLandlocked: true, flag: "🇭🇺", capital: "Budapest", population: 9660000, area: 93028, languages: ["Hungarian"], currencies: ["HUF"], region: "Central Europe", difficulty: .medium),
            GeographyCountry(id: "AT", name: "Austria", continent: .europe, latitude: 47.5162, longitude: 14.5501, isLandlocked: true, flag: "🇦🇹", capital: "Vienna", population: 9006000, area: 83871, languages: ["German"], currencies: ["EUR"], region: "Central Europe", difficulty: .medium),
            GeographyCountry(id: "CH", name: "Switzerland", continent: .europe, latitude: 46.8182, longitude: 8.2275, isLandlocked: true, flag: "🇨🇭", capital: "Bern", population: 8655000, area: 41285, languages: ["German", "French", "Italian"], currencies: ["CHF"], region: "Central Europe", difficulty: .medium),
            GeographyCountry(id: "SE", name: "Sweden", continent: .europe, latitude: 60.1282, longitude: 18.6435, isLandlocked: false, flag: "🇸🇪", capital: "Stockholm", population: 10099000, area: 450295, languages: ["Swedish"], currencies: ["SEK"], region: "Northern Europe", difficulty: .medium),
            GeographyCountry(id: "NO", name: "Norway", continent: .europe, latitude: 60.4720, longitude: 8.4689, isLandlocked: false, flag: "🇳🇴", capital: "Oslo", population: 5421000, area: 323802, languages: ["Norwegian"], currencies: ["NOK"], region: "Northern Europe", difficulty: .medium),
            GeographyCountry(id: "DK", name: "Denmark", continent: .europe, latitude: 56.2639, longitude: 9.5018, isLandlocked: false, flag: "🇩🇰", capital: "Copenhagen", population: 5792000, area: 43094, languages: ["Danish"], currencies: ["DKK"], region: "Northern Europe", difficulty: .medium),
            GeographyCountry(id: "FI", name: "Finland", continent: .europe, latitude: 61.9241, longitude: 25.7482, isLandlocked: false, flag: "🇫🇮", capital: "Helsinki", population: 5541000, area: 338424, languages: ["Finnish", "Swedish"], currencies: ["EUR"], region: "Northern Europe", difficulty: .medium),
            
            // AFRICA (20 major countries - can be expanded)
            GeographyCountry(id: "NG", name: "Nigeria", continent: .africa, latitude: 9.0820, longitude: 8.6753, isLandlocked: false, flag: "🇳🇬", capital: "Abuja", population: 206100000, area: 923768, languages: ["English"], currencies: ["NGN"], region: "Western Africa", difficulty: .easy),
            GeographyCountry(id: "ET", name: "Ethiopia", continent: .africa, latitude: 9.1450, longitude: 40.4897, isLandlocked: true, flag: "🇪🇹", capital: "Addis Ababa", population: 115000000, area: 1104300, languages: ["Amharic"], currencies: ["ETB"], region: "Eastern Africa", difficulty: .easy),
            GeographyCountry(id: "EG", name: "Egypt", continent: .africa, latitude: 26.0975, longitude: 31.4637, isLandlocked: false, flag: "🇪🇬", capital: "Cairo", population: 102300000, area: 1001450, languages: ["Arabic"], currencies: ["EGP"], region: "Northern Africa", difficulty: .easy),
            GeographyCountry(id: "ZA", name: "South Africa", continent: .africa, latitude: -30.5595, longitude: 22.9375, isLandlocked: false, flag: "🇿🇦", capital: "Cape Town", population: 59300000, area: 1221037, languages: ["Afrikaans", "English"], currencies: ["ZAR"], region: "Southern Africa", difficulty: .easy),
            GeographyCountry(id: "KE", name: "Kenya", continent: .africa, latitude: -0.0236, longitude: 37.9062, isLandlocked: false, flag: "🇰🇪", capital: "Nairobi", population: 53800000, area: 580367, languages: ["English", "Swahili"], currencies: ["KES"], region: "Eastern Africa", difficulty: .easy),
            GeographyCountry(id: "UG", name: "Uganda", continent: .africa, latitude: 1.3733, longitude: 32.2903, isLandlocked: true, flag: "🇺🇬", capital: "Kampala", population: 45700000, area: 241038, languages: ["English"], currencies: ["UGX"], region: "Eastern Africa", difficulty: .medium),
            GeographyCountry(id: "DZ", name: "Algeria", continent: .africa, latitude: 28.0339, longitude: 1.6596, isLandlocked: false, flag: "🇩🇿", capital: "Algiers", population: 43900000, area: 2381741, languages: ["Arabic"], currencies: ["DZD"], region: "Northern Africa", difficulty: .medium),
            GeographyCountry(id: "SD", name: "Sudan", continent: .africa, latitude: 12.8628, longitude: 30.2176, isLandlocked: false, flag: "🇸🇩", capital: "Khartoum", population: 43850000, area: 1861484, languages: ["Arabic", "English"], currencies: ["SDG"], region: "Northern Africa", difficulty: .medium),
            GeographyCountry(id: "MA", name: "Morocco", continent: .africa, latitude: 31.7917, longitude: -7.0926, isLandlocked: false, flag: "🇲🇦", capital: "Rabat", population: 36900000, area: 446550, languages: ["Arabic"], currencies: ["MAD"], region: "Northern Africa", difficulty: .medium),
            GeographyCountry(id: "AO", name: "Angola", continent: .africa, latitude: -11.2027, longitude: 17.8739, isLandlocked: false, flag: "🇦🇴", capital: "Luanda", population: 32900000, area: 1246700, languages: ["Portuguese"], currencies: ["AOA"], region: "Middle Africa", difficulty: .medium),
            GeographyCountry(id: "GH", name: "Ghana", continent: .africa, latitude: 7.9465, longitude: -1.0232, isLandlocked: false, flag: "🇬🇭", capital: "Accra", population: 31100000, area: 238533, languages: ["English"], currencies: ["GHS"], region: "Western Africa", difficulty: .medium),
            GeographyCountry(id: "MZ", name: "Mozambique", continent: .africa, latitude: -18.6657, longitude: 35.5296, isLandlocked: false, flag: "🇲🇿", capital: "Maputo", population: 31300000, area: 801590, languages: ["Portuguese"], currencies: ["MZN"], region: "Eastern Africa", difficulty: .medium),
            GeographyCountry(id: "MG", name: "Madagascar", continent: .africa, latitude: -18.7669, longitude: 46.8691, isLandlocked: false, flag: "🇲🇬", capital: "Antananarivo", population: 27700000, area: 587041, languages: ["Malagasy", "French"], currencies: ["MGA"], region: "Eastern Africa", difficulty: .medium),
            GeographyCountry(id: "CM", name: "Cameroon", continent: .africa, latitude: 7.3697, longitude: 12.3547, isLandlocked: false, flag: "🇨🇲", capital: "Yaoundé", population: 26500000, area: 475442, languages: ["English", "French"], currencies: ["XAF"], region: "Middle Africa", difficulty: .medium),
            GeographyCountry(id: "CI", name: "Côte d'Ivoire", continent: .africa, latitude: 7.5400, longitude: -5.5471, isLandlocked: false, flag: "🇨🇮", capital: "Yamoussoukro", population: 26400000, area: 322463, languages: ["French"], currencies: ["XOF"], region: "Western Africa", difficulty: .medium),
            GeographyCountry(id: "ZM", name: "Zambia", continent: .africa, latitude: -13.1339, longitude: 27.8493, isLandlocked: true, flag: "🇿🇲", capital: "Lusaka", population: 18400000, area: 752612, languages: ["English"], currencies: ["ZMW"], region: "Eastern Africa", difficulty: .medium),
            GeographyCountry(id: "ZW", name: "Zimbabwe", continent: .africa, latitude: -19.0154, longitude: 29.1549, isLandlocked: true, flag: "🇿🇼", capital: "Harare", population: 14900000, area: 390757, languages: ["English"], currencies: ["ZWL"], region: "Eastern Africa", difficulty: .medium),
            GeographyCountry(id: "TN", name: "Tunisia", continent: .africa, latitude: 33.8869, longitude: 9.5375, isLandlocked: false, flag: "🇹🇳", capital: "Tunis", population: 11800000, area: 163610, languages: ["Arabic"], currencies: ["TND"], region: "Northern Africa", difficulty: .medium),
            GeographyCountry(id: "LY", name: "Libya", continent: .africa, latitude: 26.3351, longitude: 17.2283, isLandlocked: false, flag: "🇱🇾", capital: "Tripoli", population: 6900000, area: 1759540, languages: ["Arabic"], currencies: ["LYD"], region: "Northern Africa", difficulty: .medium),
            GeographyCountry(id: "BW", name: "Botswana", continent: .africa, latitude: -22.3285, longitude: 24.6849, isLandlocked: true, flag: "🇧🇼", capital: "Gaborone", population: 2400000, area: 581730, languages: ["English"], currencies: ["BWP"], region: "Southern Africa", difficulty: .medium),
            
            // ASIA (20 major countries - can be expanded)
            GeographyCountry(id: "CN", name: "China", continent: .asia, latitude: 35.8617, longitude: 104.1954, isLandlocked: false, flag: "🇨🇳", capital: "Beijing", population: 1439300000, area: 9596961, languages: ["Chinese"], currencies: ["CNY"], region: "Eastern Asia", difficulty: .easy),
            GeographyCountry(id: "IN", name: "India", continent: .asia, latitude: 20.5937, longitude: 78.9629, isLandlocked: false, flag: "🇮🇳", capital: "New Delhi", population: 1380000000, area: 3287263, languages: ["Hindi", "English"], currencies: ["INR"], region: "Southern Asia", difficulty: .easy),
            GeographyCountry(id: "ID", name: "Indonesia", continent: .asia, latitude: -0.7893, longitude: 113.9213, isLandlocked: false, flag: "🇮🇩", capital: "Jakarta", population: 273500000, area: 1904569, languages: ["Indonesian"], currencies: ["IDR"], region: "South-Eastern Asia", difficulty: .easy),
            GeographyCountry(id: "PK", name: "Pakistan", continent: .asia, latitude: 30.3753, longitude: 69.3451, isLandlocked: false, flag: "🇵🇰", capital: "Islamabad", population: 220900000, area: 881912, languages: ["Urdu", "English"], currencies: ["PKR"], region: "Southern Asia", difficulty: .easy),
            GeographyCountry(id: "BD", name: "Bangladesh", continent: .asia, latitude: 23.6850, longitude: 90.3563, isLandlocked: false, flag: "🇧🇩", capital: "Dhaka", population: 164700000, area: 148460, languages: ["Bengali"], currencies: ["BDT"], region: "Southern Asia", difficulty: .medium),
            GeographyCountry(id: "JP", name: "Japan", continent: .asia, latitude: 36.2048, longitude: 138.2529, isLandlocked: false, flag: "🇯🇵", capital: "Tokyo", population: 126500000, area: 377915, languages: ["Japanese"], currencies: ["JPY"], region: "Eastern Asia", difficulty: .easy),
            GeographyCountry(id: "PH", name: "Philippines", continent: .asia, latitude: 12.8797, longitude: 121.7740, isLandlocked: false, flag: "🇵🇭", capital: "Manila", population: 109600000, area: 342353, languages: ["Filipino", "English"], currencies: ["PHP"], region: "South-Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "VN", name: "Vietnam", continent: .asia, latitude: 14.0583, longitude: 108.2772, isLandlocked: false, flag: "🇻🇳", capital: "Hanoi", population: 97300000, area: 331212, languages: ["Vietnamese"], currencies: ["VND"], region: "South-Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "TR", name: "Turkey", continent: .asia, latitude: 38.9637, longitude: 35.2433, isLandlocked: false, flag: "🇹🇷", capital: "Ankara", population: 84300000, area: 783562, languages: ["Turkish"], currencies: ["TRY"], region: "Western Asia", difficulty: .easy),
            GeographyCountry(id: "IR", name: "Iran", continent: .asia, latitude: 32.4279, longitude: 53.6880, isLandlocked: false, flag: "🇮🇷", capital: "Tehran", population: 83000000, area: 1648195, languages: ["Persian"], currencies: ["IRR"], region: "Southern Asia", difficulty: .medium),
            GeographyCountry(id: "TH", name: "Thailand", continent: .asia, latitude: 15.8700, longitude: 100.9925, isLandlocked: false, flag: "🇹🇭", capital: "Bangkok", population: 69800000, area: 513120, languages: ["Thai"], currencies: ["THB"], region: "South-Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "MM", name: "Myanmar", continent: .asia, latitude: 21.9162, longitude: 95.9560, isLandlocked: false, flag: "🇲🇲", capital: "Naypyidaw", population: 54400000, area: 676578, languages: ["Burmese"], currencies: ["MMK"], region: "South-Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "KR", name: "South Korea", continent: .asia, latitude: 35.9078, longitude: 127.7669, isLandlocked: false, flag: "🇰🇷", capital: "Seoul", population: 51300000, area: 100210, languages: ["Korean"], currencies: ["KRW"], region: "Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "IQ", name: "Iraq", continent: .asia, latitude: 33.2232, longitude: 43.6793, isLandlocked: false, flag: "🇮🇶", capital: "Baghdad", population: 40200000, area: 438317, languages: ["Arabic", "Kurdish"], currencies: ["IQD"], region: "Western Asia", difficulty: .medium),
            GeographyCountry(id: "AF", name: "Afghanistan", continent: .asia, latitude: 33.9391, longitude: 67.7100, isLandlocked: true, flag: "🇦🇫", capital: "Kabul", population: 38900000, area: 652230, languages: ["Pashto", "Dari"], currencies: ["AFN"], region: "Southern Asia", difficulty: .medium),
            GeographyCountry(id: "SA", name: "Saudi Arabia", continent: .asia, latitude: 23.8859, longitude: 45.0792, isLandlocked: false, flag: "🇸🇦", capital: "Riyadh", population: 34800000, area: 2149690, languages: ["Arabic"], currencies: ["SAR"], region: "Western Asia", difficulty: .medium),
            GeographyCountry(id: "MY", name: "Malaysia", continent: .asia, latitude: 4.2105, longitude: 101.9758, isLandlocked: false, flag: "🇲🇾", capital: "Kuala Lumpur", population: 32400000, area: 330803, languages: ["Malay"], currencies: ["MYR"], region: "South-Eastern Asia", difficulty: .medium),
            GeographyCountry(id: "UZ", name: "Uzbekistan", continent: .asia, latitude: 41.3775, longitude: 64.5853, isLandlocked: true, flag: "🇺🇿", capital: "Tashkent", population: 33500000, area: 447400, languages: ["Uzbek"], currencies: ["UZS"], region: "Central Asia", difficulty: .hard),
            GeographyCountry(id: "KZ", name: "Kazakhstan", continent: .asia, latitude: 48.0196, longitude: 66.9237, isLandlocked: true, flag: "🇰🇿", capital: "Nur-Sultan", population: 18800000, area: 2724900, languages: ["Kazakh", "Russian"], currencies: ["KZT"], region: "Central Asia", difficulty: .medium),
            GeographyCountry(id: "SG", name: "Singapore", continent: .asia, latitude: 1.3521, longitude: 103.8198, isLandlocked: false, flag: "🇸🇬", capital: "Singapore", population: 5850000, area: 719, languages: ["English", "Malay", "Chinese", "Tamil"], currencies: ["SGD"], region: "South-Eastern Asia", difficulty: .medium),
            
            // OCEANIA (3 major countries - can be expanded)
            GeographyCountry(id: "AU", name: "Australia", continent: .oceania, latitude: -25.2744, longitude: 133.7751, isLandlocked: false, flag: "🇦🇺", capital: "Canberra", population: 25500000, area: 7692024, languages: ["English"], currencies: ["AUD"], region: "Australia and New Zealand", difficulty: .easy),
            GeographyCountry(id: "NZ", name: "New Zealand", continent: .oceania, latitude: -40.9006, longitude: 174.8860, isLandlocked: false, flag: "🇳🇿", capital: "Wellington", population: 4822000, area: 268838, languages: ["English"], currencies: ["NZD"], region: "Australia and New Zealand", difficulty: .easy),
            GeographyCountry(id: "PG", name: "Papua New Guinea", continent: .oceania, latitude: -6.3150, longitude: 143.9555, isLandlocked: false, flag: "🇵🇬", capital: "Port Moresby", population: 8947000, area: 462840, languages: ["English"], currencies: ["PGK"], region: "Melanesia", difficulty: .medium),
        ]
    }
    
    private func organizeCountries() {
        // Group countries by continent
        for continent in Continent.allCases {
            countriesByContinent[continent] = allCountries.filter { $0.continent == continent }
        }
        
        // Group countries by difficulty
        for difficulty in GeographyCountry.CountryDifficulty.allCases {
            countriesByDifficulty[difficulty] = allCountries.filter { $0.difficulty == difficulty }
        }
    }
    
    private func setupLegacyCountries() {
        // Setup legacy countries array for backward compatibility
        countries = [
            // Convert new format to legacy format for backward compatibility
            GeographyCountry(id: "united_states", name: "United States", continent: .northAmerica, latitude: 39.8283, longitude: -98.5795, isLandlocked: false, flag: "🇺🇸", capital: "Washington D.C."),
            GeographyCountry(id: "canada", name: "Canada", continent: .northAmerica, latitude: 56.1304, longitude: -106.3468, isLandlocked: false, flag: "🇨🇦", capital: "Ottawa"),
            GeographyCountry(id: "mexico", name: "Mexico", continent: .northAmerica, latitude: 23.6345, longitude: -102.5528, isLandlocked: false, flag: "🇲🇽", capital: "Mexico City"),
            
            GeographyCountry(id: "brazil", name: "Brazil", continent: .southAmerica, latitude: -14.2350, longitude: -51.9253, isLandlocked: false, flag: "🇧🇷", capital: "Brasília"),
            GeographyCountry(id: "argentina", name: "Argentina", continent: .southAmerica, latitude: -38.4161, longitude: -63.6167, isLandlocked: false, flag: "🇦🇷", capital: "Buenos Aires"),
            GeographyCountry(id: "chile", name: "Chile", continent: .southAmerica, latitude: -35.6751, longitude: -71.5430, isLandlocked: false, flag: "🇨🇱", capital: "Santiago"),
            
            GeographyCountry(id: "russia", name: "Russia", continent: .europe, latitude: 61.5240, longitude: 105.3188, isLandlocked: false, flag: "🇷🇺", capital: "Moscow"),
            GeographyCountry(id: "germany", name: "Germany", continent: .europe, latitude: 51.1657, longitude: 10.4515, isLandlocked: false, flag: "🇩🇪", capital: "Berlin"),
            GeographyCountry(id: "france", name: "France", continent: .europe, latitude: 46.6034, longitude: 2.2137, isLandlocked: false, flag: "🇫🇷", capital: "Paris"),
            
            GeographyCountry(id: "nigeria", name: "Nigeria", continent: .africa, latitude: 9.0820, longitude: 8.6753, isLandlocked: false, flag: "🇳🇬", capital: "Abuja"),
            GeographyCountry(id: "egypt", name: "Egypt", continent: .africa, latitude: 26.0975, longitude: 31.4637, isLandlocked: false, flag: "🇪🇬", capital: "Cairo"),
            GeographyCountry(id: "south_africa", name: "South Africa", continent: .africa, latitude: -30.5595, longitude: 22.9375, isLandlocked: false, flag: "🇿🇦", capital: "Cape Town"),
            
            GeographyCountry(id: "china", name: "China", continent: .asia, latitude: 35.8617, longitude: 104.1954, isLandlocked: false, flag: "🇨🇳", capital: "Beijing"),
            GeographyCountry(id: "india", name: "India", continent: .asia, latitude: 20.5937, longitude: 78.9629, isLandlocked: false, flag: "🇮🇳", capital: "New Delhi"),
            GeographyCountry(id: "japan", name: "Japan", continent: .asia, latitude: 36.2048, longitude: 138.2529, isLandlocked: false, flag: "🇯🇵", capital: "Tokyo"),
            
            GeographyCountry(id: "australia", name: "Australia", continent: .oceania, latitude: -25.2744, longitude: 133.7751, isLandlocked: false, flag: "🇦🇺", capital: "Canberra"),
            GeographyCountry(id: "new_zealand", name: "New Zealand", continent: .oceania, latitude: -40.9006, longitude: 174.8860, isLandlocked: false, flag: "🇳🇿", capital: "Wellington")
        ]
    }
    
    // MARK: - Public Methods
    
    func getRandomCountry() -> GeographyCountry {
        return allCountries.randomElement() ?? allCountries[0]
    }
    
    func getRandomCountry(from continent: Continent) -> GeographyCountry? {
        return countriesByContinent[continent]?.randomElement()
    }
    
    func getRandomCountry(difficulty: GeographyCountry.CountryDifficulty) -> GeographyCountry? {
        return countriesByDifficulty[difficulty]?.randomElement()
    }
    
    func getRandomCountries(count: Int) -> [GeographyCountry] {
        return Array(allCountries.shuffled().prefix(count))
    }
    
    func getRandomCountries(from continent: Continent, count: Int) -> [GeographyCountry] {
        guard let countries = countriesByContinent[continent] else { return [] }
        return Array(countries.shuffled().prefix(count))
    }
    
    func getRandomCountries(difficulty: GeographyCountry.CountryDifficulty, count: Int) -> [GeographyCountry] {
        guard let countries = countriesByDifficulty[difficulty] else { return [] }
        return Array(countries.shuffled().prefix(count))
    }
    
    func getCountry(by id: String) -> GeographyCountry? {
        return allCountries.first { $0.id == id }
    }
    
    func getCountries(by continent: Continent) -> [GeographyCountry] {
        return countriesByContinent[continent] ?? []
    }
    
    func getCountries(by difficulty: GeographyCountry.CountryDifficulty) -> [GeographyCountry] {
        return countriesByDifficulty[difficulty] ?? []
    }
    
    func searchCountries(query: String) -> [GeographyCountry] {
        let lowercasedQuery = query.lowercased()
        return allCountries.filter { country in
            country.name.lowercased().contains(lowercasedQuery) ||
            country.capital.lowercased().contains(lowercasedQuery) ||
            country.continent.rawValue.lowercased().contains(lowercasedQuery) ||
            country.languages.contains { $0.lowercased().contains(lowercasedQuery) }
        }
    }
    
    // Statistics methods
    var totalCountries: Int { allCountries.count }
    var totalContinents: Int { Continent.allCases.count }
    
    func getCountryCount(for continent: Continent) -> Int {
        return countriesByContinent[continent]?.count ?? 0
    }
    
    func getCountryCount(for difficulty: GeographyCountry.CountryDifficulty) -> Int {
        return countriesByDifficulty[difficulty]?.count ?? 0
    }
    
    func getLandlockedCountries() -> [GeographyCountry] {
        return allCountries.filter { $0.isLandlocked }
    }
    
    func getIslandNations() -> [GeographyCountry] {
        return allCountries.filter { !$0.isLandlocked && $0.area < 100000 } // Rough approximation
    }
    
    // MARK: - Game mode methods
    
    func getCountriesForGameMode(difficulty: String, questionCount: Int) -> [GeographyCountry] {
        let gameDifficulty: GeographyCountry.CountryDifficulty
        
        switch difficulty.lowercased() {
        case "easy":
            gameDifficulty = .easy
        case "medium":
            gameDifficulty = .medium
        case "hard":
            gameDifficulty = .hard
        case "expert":
            gameDifficulty = .expert
        default:
            gameDifficulty = .easy
        }
        
        // Get countries from the specified difficulty and easier levels
        var availableCountries: [GeographyCountry] = []
        
        switch gameDifficulty {
        case .easy:
            availableCountries = getCountries(by: .easy)
        case .medium:
            availableCountries = getCountries(by: .easy) + getCountries(by: .medium)
        case .hard:
            availableCountries = getCountries(by: .easy) + getCountries(by: .medium) + getCountries(by: .hard)
        case .expert:
            availableCountries = allCountries // All difficulties for expert mode
        }
        
        return Array(availableCountries.shuffled().prefix(questionCount))
    }
    
    func getBalancedCountriesForGame(questionCount: Int) -> [GeographyCountry] {
        // Get a balanced mix from each continent
        var selectedCountries: [GeographyCountry] = []
        let countriesPerContinent = questionCount / Continent.allCases.count
        let remainder = questionCount % Continent.allCases.count
        
        for (index, continent) in Continent.allCases.enumerated() {
            let countriesFromContinent = index < remainder ? countriesPerContinent + 1 : countriesPerContinent
            let continentCountries = getRandomCountries(from: continent, count: countriesFromContinent)
            selectedCountries.append(contentsOf: continentCountries)
        }
        
        return selectedCountries.shuffled()
    }
    
    // Legacy compatibility methods
    func getCountriesByContinent(_ continent: Continent) -> [GeographyCountry] {
        return getCountries(by: continent)
    }
}

// MARK: - Enhanced Grid System for Precise Placement
class GeographyGridSystem {
    static let shared = GeographyGridSystem()
    
    // Grid dimensions for each continent
    private let gridDimensions: [Continent: (rows: Int, cols: Int)] = [
        .northAmerica: (4, 5),
        .southAmerica: (5, 3),
        .europe: (4, 4),
        .africa: (5, 4),
        .asia: (5, 6),
        .oceania: (3, 4)
    ]
    
    // Pre-calculated correct grid cells for cities
    private let cityGridMapping: [String: GridCell] = [
        // NORTH AMERICA
        "new_york": GridCell(row: 1, col: 3),
        "los_angeles": GridCell(row: 2, col: 1),
        "mexico_city": GridCell(row: 2, col: 1),
        "toronto": GridCell(row: 1, col: 2),
        "chicago": GridCell(row: 1, col: 2),
        
        // SOUTH AMERICA
        "sao_paulo": GridCell(row: 2, col: 2),
        "rio_janeiro": GridCell(row: 2, col: 2),
        "buenos_aires": GridCell(row: 3, col: 1),
        "lima": GridCell(row: 1, col: 0),
        "bogota": GridCell(row: 0, col: 0),
        
        // EUROPE
        "london": GridCell(row: 2, col: 1),
        "paris": GridCell(row: 2, col: 1),
        "berlin": GridCell(row: 2, col: 1),
        "rome": GridCell(row: 2, col: 1),
        "madrid": GridCell(row: 2, col: 1),
        
        // AFRICA
        "cairo": GridCell(row: 1, col: 2),
        "lagos": GridCell(row: 2, col: 1),
        "johannesburg": GridCell(row: 3, col: 2),
        "casablanca": GridCell(row: 1, col: 0),
        "nairobi": GridCell(row: 2, col: 3),
        
        // ASIA
        "tokyo": GridCell(row: 2, col: 5),
        "beijing": GridCell(row: 2, col: 4),
        "mumbai": GridCell(row: 2, col: 2),
        "shanghai": GridCell(row: 2, col: 4),
        "delhi": GridCell(row: 2, col: 2),
        
        // OCEANIA
        "sydney": GridCell(row: 1, col: 1),
        "melbourne": GridCell(row: 1, col: 1)
    ]
    
    // Enhanced grid mappings for countries (can span multiple cells)
    private let countryGridMapping: [String: [GridCell]] = [
        // NORTH AMERICA - Legacy IDs
        "united_states": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 1, col: 3), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2), GridCell(row: 2, col: 3)],
        "canada": [GridCell(row: 0, col: 0), GridCell(row: 0, col: 1), GridCell(row: 0, col: 2), GridCell(row: 0, col: 3), GridCell(row: 0, col: 4)],
        "mexico": [GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        
        // NORTH AMERICA - Enhanced IDs
        "US": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 1, col: 3), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2), GridCell(row: 2, col: 3)],
        "CA": [GridCell(row: 0, col: 0), GridCell(row: 0, col: 1), GridCell(row: 0, col: 2), GridCell(row: 0, col: 3), GridCell(row: 0, col: 4)],
        "MX": [GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        "GT": [GridCell(row: 3, col: 2)],
        "BZ": [GridCell(row: 3, col: 2)],
        "SV": [GridCell(row: 3, col: 2)],
        "HN": [GridCell(row: 3, col: 2)],
        "NI": [GridCell(row: 3, col: 2)],
        "CR": [GridCell(row: 3, col: 2)],
        "PA": [GridCell(row: 3, col: 2)],
        "CU": [GridCell(row: 2, col: 3)],
        "JM": [GridCell(row: 2, col: 3)],
        "HT": [GridCell(row: 2, col: 3)],
        "DO": [GridCell(row: 2, col: 3)],
        "BS": [GridCell(row: 2, col: 3)],
        "BB": [GridCell(row: 2, col: 4)],
        "TT": [GridCell(row: 3, col: 3)],
        
        // SOUTH AMERICA - Legacy IDs
        "brazil": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2), GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        "argentina": [GridCell(row: 3, col: 0), GridCell(row: 3, col: 1), GridCell(row: 4, col: 0), GridCell(row: 4, col: 1)],
        "chile": [GridCell(row: 2, col: 1), GridCell(row: 3, col: 1)],
        
        // SOUTH AMERICA - Enhanced IDs
        "BR": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2), GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        "AR": [GridCell(row: 3, col: 0), GridCell(row: 3, col: 1), GridCell(row: 4, col: 0), GridCell(row: 4, col: 1)],
        "CO": [GridCell(row: 0, col: 0), GridCell(row: 0, col: 1), GridCell(row: 1, col: 1)],
        "PE": [GridCell(row: 1, col: 0), GridCell(row: 1, col: 1)],
        "VE": [GridCell(row: 0, col: 1)],
        "CL": [GridCell(row: 2, col: 1), GridCell(row: 3, col: 1), GridCell(row: 4, col: 1)],
        "EC": [GridCell(row: 1, col: 0)],
        "BO": [GridCell(row: 2, col: 1)],
        "PY": [GridCell(row: 2, col: 1)],
        "UY": [GridCell(row: 3, col: 1)],
        "GY": [GridCell(row: 0, col: 1)],
        "SR": [GridCell(row: 0, col: 2)],
        
        // EUROPE - Legacy IDs
        "russia": [GridCell(row: 0, col: 2), GridCell(row: 0, col: 3), GridCell(row: 1, col: 2), GridCell(row: 1, col: 3), GridCell(row: 2, col: 2), GridCell(row: 2, col: 3)],
        "germany": [GridCell(row: 2, col: 1)],
        "france": [GridCell(row: 2, col: 1)],
        
        // EUROPE - Enhanced IDs
        "RU": [GridCell(row: 0, col: 2), GridCell(row: 0, col: 3), GridCell(row: 1, col: 2), GridCell(row: 1, col: 3), GridCell(row: 2, col: 2), GridCell(row: 2, col: 3)],
        "DE": [GridCell(row: 2, col: 1)],
        "FR": [GridCell(row: 2, col: 0), GridCell(row: 2, col: 1)],
        "IT": [GridCell(row: 3, col: 1)],
        "ES": [GridCell(row: 3, col: 0)],
        "GB": [GridCell(row: 1, col: 0)],
        "PL": [GridCell(row: 1, col: 2)],
        "UA": [GridCell(row: 1, col: 2), GridCell(row: 2, col: 2)],
        "NL": [GridCell(row: 1, col: 1)],
        "BE": [GridCell(row: 1, col: 1)],
        "GR": [GridCell(row: 3, col: 2)],
        "PT": [GridCell(row: 3, col: 0)],
        "CZ": [GridCell(row: 2, col: 1)],
        "HU": [GridCell(row: 2, col: 2)],
        "AT": [GridCell(row: 2, col: 1)],
        "CH": [GridCell(row: 2, col: 1)],
        "SE": [GridCell(row: 0, col: 1)],
        "NO": [GridCell(row: 0, col: 1)],
        "DK": [GridCell(row: 1, col: 1)],
        "FI": [GridCell(row: 0, col: 2)],
        
        // AFRICA - Legacy IDs
        "nigeria": [GridCell(row: 2, col: 1)],
        "egypt": [GridCell(row: 1, col: 2), GridCell(row: 1, col: 3)],
        "south_africa": [GridCell(row: 4, col: 1), GridCell(row: 4, col: 2)],
        
        // AFRICA - Enhanced IDs
        "NG": [GridCell(row: 2, col: 1)],
        "ET": [GridCell(row: 2, col: 3)],
        "EG": [GridCell(row: 0, col: 2)],
        "ZA": [GridCell(row: 4, col: 1), GridCell(row: 4, col: 2)],
        "KE": [GridCell(row: 2, col: 3)],
        "UG": [GridCell(row: 2, col: 2)],
        "DZ": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2)],
        "SD": [GridCell(row: 1, col: 2)],
        "MA": [GridCell(row: 1, col: 0)],
        "AO": [GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        "GH": [GridCell(row: 2, col: 1)],
        "MZ": [GridCell(row: 3, col: 3), GridCell(row: 4, col: 3)],
        "MG": [GridCell(row: 3, col: 3),GridCell(row: 3, col: 4),GridCell(row: 4, col: 3)],
        "CM": [GridCell(row: 2, col: 1)],
        "CI": [GridCell(row: 2, col: 0)],
        "ZM": [GridCell(row: 3, col: 2)],
        "ZW": [GridCell(row: 3, col: 2)],
        "TN": [GridCell(row: 0, col: 1)],
        "LY": [GridCell(row: 1, col: 1), GridCell(row: 1, col: 2)],
        "BW": [GridCell(row: 2, col: 2)],
        
        // ASIA - Legacy IDs
        "china": [GridCell(row: 1, col: 3),  GridCell(row: 2, col: 3), GridCell(row: 3, col: 3)],
        "india": [GridCell(row: 2, col: 2), GridCell(row: 3, col: 2), GridCell(row: 3, col: 3)],
        "japan": [GridCell(row: 3, col: 4)],
        
        // ASIA - Enhanced IDs
        "CN": [GridCell(row: 1, col: 3), GridCell(row: 2, col: 3), GridCell(row: 3, col: 3)],
        "IN": [GridCell(row: 2, col: 2), GridCell(row: 3, col: 2), GridCell(row: 3, col: 3)],
        "ID": [GridCell(row: 4, col: 3), GridCell(row: 4, col: 4)],
        "PK": [GridCell(row: 2, col: 1), GridCell(row: 2, col: 2)],
        "BD": [GridCell(row: 2, col: 3)],
        "JP": [GridCell(row: 2, col: 5)],
        "PH": [GridCell(row: 3, col: 4), GridCell(row: 3, col: 5)],
        "VN": [GridCell(row: 3, col: 3)],
        "TR": [GridCell(row: 2, col: 0), GridCell(row: 2, col: 1)],
        "IR": [GridCell(row: 2, col: 1), GridCell(row: 2, col: 2)],
        "TH": [GridCell(row: 3, col: 3)],
        "MM": [GridCell(row: 3, col: 3)],
        "KR": [GridCell(row: 2, col: 4)],
        "IQ": [GridCell(row: 2, col: 1)],
        "AF": [GridCell(row: 2, col: 2)],
        "SA": [GridCell(row: 3, col: 1), GridCell(row: 3, col: 2)],
        "MY": [GridCell(row: 3, col: 3)],
        "UZ": [GridCell(row: 1, col: 2)],
        "KZ": [GridCell(row: 1, col: 2), GridCell(row: 1, col: 3)],
        "SG": [GridCell(row: 4, col: 3)],
        
        // OCEANIA - Legacy IDs
        "australia": [GridCell(row: 1, col: 0), GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 2, col: 0), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2)],
        "new_zealand": [GridCell(row: 2, col: 3)],
        
        // OCEANIA - Enhanced IDs
        "AU": [GridCell(row: 1, col: 0), GridCell(row: 1, col: 1), GridCell(row: 1, col: 2), GridCell(row: 2, col: 0), GridCell(row: 2, col: 1), GridCell(row: 2, col: 2)],
        "NZ": [GridCell(row: 2, col: 2)],
        "PG": [GridCell(row: 1, col: 1)]
    ]
    
    func getGridDimensions(for continent: Continent) -> (rows: Int, cols: Int) {
        return gridDimensions[continent] ?? (4, 4)
    }
    
    func getCorrectGridCell(for cityId: String) -> GridCell? {
        return cityGridMapping[cityId]
    }
    
    func getCorrectGridCells(for countryId: String) -> [GridCell]? {
        // First try exact match
        if let cells = countryGridMapping[countryId] {
            return cells
        }
        
        // If not found, generate based on country characteristics
        guard let country = GeographyCountryDatabase.shared.getCountry(by: countryId) else {
            return nil
        }
        
        // Generate sample grid cells based on country characteristics
        let baseGridCount: Int
        switch country.area {
        case 0..<10000: baseGridCount = 1        // Small countries/islands
        case 10000..<100000: baseGridCount = 2   // Medium countries
        case 100000..<500000: baseGridCount = 4  // Large countries
        case 500000..<2000000: baseGridCount = 6 // Very large countries
        default: baseGridCount = 8               // Massive countries
        }
        
        // Adjust for landlocked status (often more compact)
        let gridCount = country.isLandlocked ? max(1, baseGridCount - 1) : baseGridCount
        
        // Generate grid cells based on continent dimensions
        let dimensions = getGridDimensions(for: country.continent)
        var cells: [GridCell] = []
        
        // Simple algorithm to generate connected grid cells
        let startRow = Int.random(in: 0..<dimensions.rows)
        let startCol = Int.random(in: 0..<dimensions.cols)
        cells.append(GridCell(row: startRow, col: startCol))
        
        for _ in 1..<gridCount {
            if let lastCell = cells.last {
                // Try to add adjacent cell
                let directions = [(0, 1), (1, 0), (0, -1), (-1, 0)] // right, down, left, up
                for (dRow, dCol) in directions.shuffled() {
                    let newRow = lastCell.row + dRow
                    let newCol = lastCell.col + dCol
                    
                    if newRow >= 0 && newRow < dimensions.rows &&
                       newCol >= 0 && newCol < dimensions.cols {
                        let newCell = GridCell(row: newRow, col: newCol)
                        if !cells.contains(newCell) {
                            cells.append(newCell)
                            break
                        }
                    }
                }
            }
        }
        
        return cells
    }
    
    func calculateCityScore(cityId: String, selectedGrid: GridCell) -> PlacementResult {
        guard let correctGrid = getCorrectGridCell(for: cityId) else {
            return PlacementResult(score: 0, distance: 0, accuracy: "City not found", isCorrect: false)
        }
        
        if selectedGrid == correctGrid {
            return PlacementResult(score: 50, distance: 0, accuracy: "Perfect!", isCorrect: true)
        }
        
        let rowDiff = abs(selectedGrid.row - correctGrid.row)
        let colDiff = abs(selectedGrid.col - correctGrid.col)
        let totalDistance = rowDiff + colDiff
        
        let (score, accuracy) = switch totalDistance {
        case 1: (35, "Very Close!")
        case 2: (20, "Close!")
        case 3: (10, "Getting Warm!")
        default: (0, "Try Again!")
        }
        
        return PlacementResult(score: score, distance: Double(totalDistance), accuracy: accuracy, isCorrect: false)
    }
    
    func calculateCountryScore(countryId: String, selectedGrids: [GridCell]) -> PlacementResult {
        guard let correctGrids = getCorrectGridCells(for: countryId), !correctGrids.isEmpty else {
            return PlacementResult(score: 0, distance: 0, accuracy: "Country not found", isCorrect: false, feedback: "Country data not found")
        }
        
        if selectedGrids.isEmpty {
            return PlacementResult(score: 0, distance: 0, accuracy: "No cells selected", isCorrect: false, feedback: "Please select at least one grid cell")
        }
        
        let correctCells = Set(correctGrids)
        let selectedCells = Set(selectedGrids)
        let intersection = correctCells.intersection(selectedCells)
        
        let correctCount = correctCells.count
        let selectedCount = selectedCells.count
        let correctlySelected = intersection.count
        
        // Calculate precision and recall
        let precision = selectedCount > 0 ? Double(correctlySelected) / Double(selectedCount) : 0
        let recall = correctCount > 0 ? Double(correctlySelected) / Double(correctCount) : 0
        let f1Score = (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0
        
        // Score calculation (0-50 points)
        let baseScore = Int(f1Score * 50)
        
        // Bonus for perfect match
        let perfectBonus = (correctlySelected == correctCount && correctlySelected == selectedCount) ? 5 : 0
        
        let finalScore = min(50, baseScore + perfectBonus)
        
        // Generate accuracy message
        let accuracyPercentage = Int(f1Score * 100)
        let accuracy: String
        let feedback: String
        
        switch accuracyPercentage {
        case 90...100:
            accuracy = "Perfect!"
            feedback = "Excellent placement! You got all the grid cells correct."
        case 80...89:
            accuracy = "Excellent!"
            feedback = "Great job! Very accurate placement with minor variations."
        case 70...79:
            accuracy = "Very Good!"
            feedback = "Good placement! Most of your selections were correct."
        case 60...69:
            accuracy = "Good!"
            feedback = "Decent placement! You got the general area right."
        case 40...59:
            accuracy = "Fair"
            feedback = "You're getting closer! Try to be more precise with the placement."
        case 20...39:
            accuracy = "Poor"
            feedback = "Some correct areas, but the country spans different regions."
        default:
            accuracy = "Try Again"
            feedback = "The country is located in different grid sections. Study the map and try again!"
        }
        
        let isCorrect = accuracyPercentage >= 80
        
        return PlacementResult(
            score: finalScore,
            distance: 1.0 - f1Score,
            accuracy: accuracy,
            isCorrect: isCorrect,
            feedback: feedback
        )
    }
}
