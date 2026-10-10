import SwiftUI

public struct PermissionsScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var farmName: String = ""
    @State private var ownerName: String = ""
    @State private var speciesPermitted: String = "Deer & Vermin"
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Permissions Wallet")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.top, 16)
                    
                    Text("Store your landowner permission records, shoot boundaries, and contact verification.")
                        .font(.system(size: 14))
                        .foregroundColor(theme.palette.mist)
                    
                    GlassPanel {
                        SectionLabel("Active Permission Example")
                        Text("Manor Farm Shoot")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        MetricRow("Landowner", "John Thornhill")
                        MetricRow("Contact", "+44 7700 900123")
                        MetricRow("Quarry", "Roe Deer, Muntjac, Fox")
                        MetricRow("Expires", "01 Sep 2027")
                        
                        Text("Verified written permission valid on estate boundaries.")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.success)
                            .padding(.top, 4)
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
