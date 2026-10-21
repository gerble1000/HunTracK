import SwiftUI

public struct AboutScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("About")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("HunTracK")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(theme.palette.bronzeBright)
                    }
                    .padding(.top, 8)
                    
                    GlassPanel {
                        SectionLabel("The app")
                        Text("HunTracK is a field companion for UK hunters and stalkers. It brings your map, wind & scent awareness, compass, weather, species seasons, calls, and safety tools into one pocket kit — built for use outside, not at a desk.")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.cream)
                        Text("Know where you are, which way the wind sends your scent, range a shot, mark downed prey, and keep permissions and emergency details ready when you need them.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 6)
                        
                        Divider().background(theme.palette.slate).padding(.vertical, 8)
                        
                        MetricRow("Version", "1.1.27 (35)")
                        MetricRow("Platform", "iOS")
                    }
                    
                    GlassPanel {
                        SectionLabel("Developer")
                        Text("GER8LE")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Text("Built and engineered HunTracK — app architecture, map tools, field features, and the polish you use each outing.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.cream)
                            .padding(.top, 2)
                    }
                    
                    GlassPanel {
                        SectionLabel("Helping hand")
                        Text("Greg Willing")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Text("Design guidance and field-feature direction from someone who knows the ground. Greg helped shape how HunTracK feels in the hand and what matters when you are out on permission.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.cream)
                            .padding(.top, 2)
                        Text("Thank you for the helping hand.")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.gold)
                            .padding(.top, 6)
                    }
                    
                    GlassPanel {
                        SectionLabel("Support & Community")
                        Text("Join our Facebook group for tips, updates, and community support:")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.cream)
                        
                        if let url = URL(string: "https://www.facebook.com/share/g/19MMYKx2fa") {
                            Link(destination: url) {
                                HStack {
                                    Text("HunTracK Facebook Group")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(theme.palette.bronzeBright)
                                    Image(systemName: "arrow.up.right.square")
                                        .foregroundColor(theme.palette.bronzeBright)
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    
                    GlassPanel {
                        SectionLabel("Your data")
                        Text("HunTracK stores your markers, tracks, calendar, cull log, permissions and emergency profile locally on this device. Nothing is uploaded to a server. There are no accounts and no cloud sync in this version.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.cream)
                    }
                    
                    GlassPanel {
                        SectionLabel("Credits")
                        MetricRow("Weather", "Open-Meteo")
                        MetricRow("Mapping", "Apple Maps")
                        MetricRow("Coordinates", "Ordnance Survey grid")
                        MetricRow("Astronomy", "SunCalc formulae")
                    }
                    
                    GlassPanel {
                        SectionLabel("Please remember")
                        Text("Weather, scent checks, seasons, and activity guidance are indicative only. Always verify current laws, land permission, and safe practice before you hunt.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}
