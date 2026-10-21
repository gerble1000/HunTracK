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
                        Text("Tap any row to open")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)
                    
                    SectionLabel("Navigation")
                    NavigationLink(destination: CompassScreen()) {
                        MoreRow(title: "Compass", subtitle: "Magnetic, true & GPS heading", icon: "safari")
                    }
                    NavigationLink(destination: WindScreen()) {
                        MoreRow(title: "Wind & Scent", subtitle: "Downwind scent plume planning", icon: "wind")
                    }
                    
                    SectionLabel("Field knowledge")
                    NavigationLink(destination: SpeciesScreen()) {
                        MoreRow(title: "Species", subtitle: "UK quarry ID & legal seasons", icon: "leaf")
                    }
                    NavigationLink(destination: CallsScreen()) {
                        MoreRow(title: "Calls", subtitle: "Library, recorder & journal", icon: "speaker.wave.3")
                    }
                    
                    SectionLabel("Planning")
                    NavigationLink(destination: CalendarScreen()) {
                        MoreRow(title: "Calendar", subtitle: "Day, month & year field diary", icon: "calendar")
                    }
                    NavigationLink(destination: CullScreen()) {
                        MoreRow(title: "Cull log", subtitle: "Bag table, OS Grid & CSV export", icon: "list.bullet.rectangle")
                    }
                    NavigationLink(destination: TidesScreen()) {
                        MoreRow(title: "Tides", subtitle: "UK high & low water forecast", icon: "water.waves")
                    }
                    
                    SectionLabel("Safety & access")
                    NavigationLink(destination: PermissionsScreen()) {
                        MoreRow(title: "Permissions", subtitle: "Wallet & boundary areas", icon: "doc.text")
                    }
                    NavigationLink(destination: EmergencyScreen()) {
                        MoreRow(title: "Emergency", subtitle: "Public medical profile", icon: "cross.case")
                    }
                    
                    SectionLabel("App")
                    NavigationLink(destination: SettingsScreen()) {
                        MoreRow(title: "Settings", subtitle: "Units, theme, sound, maps", icon: "gearshape")
                    }
                    NavigationLink(destination: AboutScreen()) {
                        MoreRow(title: "About", subtitle: "App story, developer & helping hand", icon: "info.circle")
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
