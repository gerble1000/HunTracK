import SwiftUI

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
            .font(.system(size: 11, weight: .bold))
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
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
            Spacer()
            Text(value)
                .font(.system(size: 15, weight: .semibold))
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
                    .font(.system(size: 15, weight: .semibold))
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
