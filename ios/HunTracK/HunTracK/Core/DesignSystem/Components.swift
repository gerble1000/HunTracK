import SwiftUI
import Foundation

extension Notification.Name {
    static let boundaryCaptureFinished = Notification.Name("hunttrack.boundaryCaptureFinished")
}

/// Readable minimum text sizes which also follow the user's Dynamic Type setting.
extension Font {
    static func field(size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) -> Font {
        let style: Font.TextStyle
        switch size {
        case ..<14: style = .subheadline
        case ..<18: style = .body
        case ..<22: style = .title3
        case ..<26: style = .title2
        case ..<31: style = .title
        default: style = .largeTitle
        }
        return .system(style, design: design).weight(weight)
    }
}

/// Shared animal/category artwork for logs, marker pickers and map annotations.
public enum MarkerArtwork {
    public static func photoName(speciesId: String?, category: MarkerCategory = .otherWildlife) -> String? {
        if let speciesId = speciesId,
           let photo = SpeciesImageCatalog.previewPhotoName(speciesId: speciesId) { return photo }
        let fallback: String
        switch category {
        case .deer: fallback = "red_deer"
        case .fox: fallback = "fox"
        case .rabbit: fallback = "rabbit"
        case .bird: fallback = "pheasant"
        case .badger: fallback = "badger"
        default: return nil
        }
        return SpeciesImageCatalog.previewPhotoName(speciesId: fallback)
    }

    public static func symbol(for category: MarkerCategory) -> String {
        switch category {
        case .deer, .fox, .rabbit, .badger, .otherWildlife: return "pawprint.fill"
        case .bird: return "bird.fill"
        case .highSeat, .treeStand: return "tree.fill"
        case .hide, .shelter: return "tent.fill"
        case .cull: return "scope"
        case .feedingArea, .saltLick: return "leaf.fill"
        case .trailCamera: return "camera.fill"
        case .caller: return "speaker.wave.2.fill"
        case .waterSource: return "drop.fill"
        case .parking: return "parkingsign.circle.fill"
        case .gate, .fence, .boundary: return "rectangle.dashed"
        case .dangerArea: return "exclamationmark.triangle.fill"
        case .homing: return "flag.fill"
        case .footprint: return "shoeprints.fill"
        case .blood: return "drop.fill"
        case .hair: return "line.3.horizontal"
        case .droppings: return "circle.grid.2x2.fill"
        case .beddingArea: return "bed.double.fill"
        case .lastSighting: return "eye.fill"
        }
    }
}

public struct AnimalIcon: View {
    var speciesId: String?
    var category: MarkerCategory = .otherWildlife
    var size: CGFloat = 36

    public var body: some View {
        Group {
            if let name = MarkerArtwork.photoName(speciesId: speciesId, category: category) {
                Image(name).resizable().scaledToFill()
            } else {
                Image(systemName: MarkerArtwork.symbol(for: category))
                    .resizable().scaledToFit().padding(6)
                    .foregroundColor(ThemeManager.shared.palette.bronzeBright)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityHidden(true)
    }
}

public struct GlassPanel<Content: View>: View {
    let compact: Bool
    let content: Content
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(compact: Bool = false, @ViewBuilder content: () -> Content) {
        self.compact = compact
        self.content = content()
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: compact ? 6 : 10) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(compact ? 10 : 16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(theme.palette.glass)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(theme.palette.glassBorder, lineWidth: 1)
        )
    }
}

public struct SectionLabel: View {
    let title: String
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ title: String) {
        self.title = title
    }
    
    public var body: some View {
        Text(title.uppercased())
            .font(.field(size: 11, weight: .bold))
            .foregroundColor(theme.palette.bronze)
            .tracking(1.2)
    }
}

public struct MetricRow: View {
    let label: String
    let value: String
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
    }
    
    public var body: some View {
        HStack {
            Text(label)
                .font(.field(size: 14))
                .foregroundColor(theme.palette.mist)
            Spacer()
            Text(value)
                .font(.field(size: 15, weight: .semibold))
                .foregroundColor(theme.palette.cream)
        }
        .padding(.vertical, 2)
    }
}

public struct HuntButton: View {
    let title: String
    let icon: String?
    let action: () -> Void
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }
    
    public var body: some View {
        Button(action: {
            SoundManager.shared.play(.click)
            action()
        }) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                }
                Text(title)
                    .font(.field(size: 15, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(theme.palette.bronze)
            )
            .foregroundColor(theme.palette.charcoal)
        }
    }
}

public struct ScreenBackground<Content: View>: View {
    let content: Content
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    public var body: some View {
        ZStack {
            theme.palette.forestDeep
                .ignoresSafeArea()
            content
        }
    }
}

/// Single source of truth for the version shown in About and Settings, so the
/// displayed build tracks Info.plist instead of drifting from it.
public enum AppVersion {
    public static var short: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1.29"
    }

    public static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
    }

    public static var display: String {
        "\(short) (\(build))"
    }
}
