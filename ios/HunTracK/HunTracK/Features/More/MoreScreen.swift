import SwiftUI

public struct MoreScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("More")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("Field tools & settings")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)
                    
                    SectionLabel("Navigation & Field Sense")
                    NavigationLink(destination: CompassScreen()) {
                        MoreRow(title: "Compass", subtitle: "Magnetic, true & GPS heading", icon: "safari")
                    }
                    NavigationLink(destination: WindScreen()) {
                        MoreRow(title: "Wind & Scent", subtitle: "Downwind scent plume planning", icon: "wind")
                    }
                    
                    SectionLabel("Field Knowledge")
                    NavigationLink(destination: SpeciesScreen()) {
                        MoreRow(title: "Species Catalog", subtitle: "UK quarry ID & legal seasons", icon: "leaf")
                    }
                    NavigationLink(destination: CallsScreen()) {
                        MoreRow(title: "Animal Calls", subtitle: "Synthetic calls & audio journal", icon: "speaker.wave.3")
                    }
                    
                    SectionLabel("Safety & Permissions")
                    NavigationLink(destination: PermissionsScreen()) {
                        MoreRow(title: "Permissions Wallet", subtitle: "Shooting permissions & boundaries", icon: "doc.text")
                    }
                    NavigationLink(destination: EmergencyScreen()) {
                        MoreRow(title: "Emergency Profile", subtitle: "Public medical ICE profile with lock", icon: "cross.case")
                    }
                    
                    SectionLabel("App")
                    NavigationLink(destination: SettingsScreen()) {
                        MoreRow(title: "Settings", subtitle: "Theme, units, sounds & GPS", icon: "gearshape")
                    }
                }
                .padding(16)
            }
        }
    }
}

public struct MoreRow: View {
    let title: String
    let subtitle: String
    let icon: String
    @ObservedObject private var theme = ThemeManager.shared
    
    public var body: some View {
        GlassPanel(compact: true) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(theme.palette.bronze)
                    .frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundColor(theme.palette.mist)
            }
        }
    }
}
