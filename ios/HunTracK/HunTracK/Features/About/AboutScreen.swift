import SwiftUI

public struct AboutScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("HunTracK")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(theme.palette.bronze)
                        Text("UK field companion")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)
                    
                    GlassPanel {
                        SectionLabel("The app")
                        Text("HunTracK is a UK hunting, wildlife and field navigation companion. It brings maps, weather, OS Grid references, species identification, legal season guidance, permissions records and emergency information into one dark-woodland tool designed to be readable in the field.")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.cream)
                    }
                    
                    GlassPanel {
                        SectionLabel("Helping hand")
                        Text("HunTracK is built to support responsible deer management, conservation and lawful field sports. Effective culling and predation control protect woodland regeneration, crops and livestock, and reduce road traffic collisions involving deer.")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.cream)
                        Text("Always obtain landowner permission, follow the law for your nation, and observe best practice on safety, welfare and carcass handling.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 4)
                    }
                    
                    GlassPanel {
                        SectionLabel("Your data")
                        Text("HunTracK stores your markers, tracks, calendar, cull log, permissions and emergency profile locally on this device. Nothing is uploaded to a server. There are no accounts and no cloud sync in this version.")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.cream)
                    }
                    
                    GlassPanel {
                        SectionLabel("Credits")
                        MetricRow("Weather", "Open-Meteo")
                        MetricRow("Mapping", "Apple Maps")
                        MetricRow("Coordinates", "Ordnance Survey grid")
                        MetricRow("Astronomy", "SunCalc formulae")
                        Text("Species identification notes and legal season dates are indicative only.")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 4)
                    }
                    
                    GlassPanel {
                        SectionLabel("Version")
                        MetricRow("Version", "1.1.27")
                        MetricRow("Build", "29")
                        MetricRow("Platform", "iOS")
                    }
                    
                    Text("Field conditions change. Always verify current law, land access and weather before you go out.")
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .padding(.top, 4)
                }
                .padding(16)
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}
