import SwiftUI

public enum HuntThemeMode: String, CaseIterable, Codable {
    case woodland = "WOODLAND"
    case night = "NIGHT"
    case hunt = "HUNT"
    
    public var displayName: String {
        switch self {
        case .woodland: return "Woodland"
        case .night: return "Night"
        case .hunt: return "Hunt"
        }
    }
}

public struct HuntPalette {
    public let forestDeep: Color
    public let forestMid: Color
    public let forestGreen: Color
    public let moss: Color
    public let charcoal: Color
    public let ink: Color
    public let slate: Color
    public let slateLight: Color
    public let mist: Color
    public let cream: Color
    public let bronze: Color
    public let bronzeBright: Color
    public let gold: Color
    public let danger: Color
    public let success: Color
    public let glass: Color
    public let glassBorder: Color
    
    public static let woodland = HuntPalette(
        forestDeep: Color(hex: "#071A10"),
        forestMid: Color(hex: "#143726"),
        forestGreen: Color(hex: "#2A6B44"),
        moss: Color(hex: "#3F9A5C"),
        charcoal: Color(hex: "#0E1114"),
        ink: Color(hex: "#14110C"),
        slate: Color(hex: "#334048"),
        slateLight: Color(hex: "#4E5C66"),
        mist: Color(hex: "#C8D2DA"),
        cream: Color(hex: "#F6F1E6"),
        bronze: Color(hex: "#E2B05C"),
        bronzeBright: Color(hex: "#F3C97A"),
        gold: Color(hex: "#F0D48A"),
        danger: Color(hex: "#E05A45"),
        success: Color(hex: "#45B872"),
        glass: Color(hex: "#1B3A28").opacity(0.88),
        glassBorder: Color(hex: "#E2B05C").opacity(0.40)
    )
    
    public static let night = HuntPalette(
        forestDeep: Color(hex: "#030508"),
        forestMid: Color(hex: "#0B1018"),
        forestGreen: Color(hex: "#1A3048"),
        moss: Color(hex: "#3A6A8A"),
        charcoal: Color(hex: "#05070A"),
        ink: Color(hex: "#020304"),
        slate: Color(hex: "#1E2A38"),
        slateLight: Color(hex: "#3A4A5C"),
        mist: Color(hex: "#9AAFC2"),
        cream: Color(hex: "#E4ECF4"),
        bronze: Color(hex: "#7EB0D4"),
        bronzeBright: Color(hex: "#A8D0F0"),
        gold: Color(hex: "#B8C8D8"),
        danger: Color(hex: "#E07060"),
        success: Color(hex: "#4AA888"),
        glass: Color(hex: "#0B1018").opacity(0.88),
        glassBorder: Color(hex: "#7EB0D4").opacity(0.40)
    )
    
    public static let hunt = HuntPalette(
        forestDeep: Color(hex: "#120405"),
        forestMid: Color(hex: "#2A0A0C"),
        forestGreen: Color(hex: "#6B1A1E"),
        moss: Color(hex: "#A02830"),
        charcoal: Color(hex: "#0C0304"),
        ink: Color(hex: "#080202"),
        slate: Color(hex: "#3A1418"),
        slateLight: Color(hex: "#5C2428"),
        mist: Color(hex: "#D8B0B4"),
        cream: Color(hex: "#FFE8E6"),
        bronze: Color(hex: "#E84848"),
        bronzeBright: Color(hex: "#FF6A5C"),
        gold: Color(hex: "#FF8A70"),
        danger: Color(hex: "#FF3A2A"),
        success: Color(hex: "#E07050"),
        glass: Color(hex: "#2A0A0C").opacity(0.88),
        glassBorder: Color(hex: "#E84848").opacity(0.40)
    )
    
    public static func forMode(_ mode: HuntThemeMode) -> HuntPalette {
        switch mode {
        case .woodland: return .woodland
        case .night: return .night
        case .hunt: return .hunt
        }
    }
}

public class ThemeManager: ObservableObject {
    public static let shared = ThemeManager()
    
    @Published public var mode: HuntThemeMode = .woodland {
        didSet {
            palette = HuntPalette.forMode(mode)
        }
    }
    
    @Published public private(set) var palette: HuntPalette = .woodland
    
    private init() {}
    
    public func cycleTheme() {
        switch mode {
        case .woodland: mode = .night
        case .night: mode = .hunt
        case .hunt: mode = .woodland
        }
    }
}

extension Color {
    public init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
