import SwiftUI
import LocalAuthentication

public struct EmergencyScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var isUnlocked: Bool = false
    @State private var fullName: String = "Hunter"
    @State private var bloodType: String = "O Positive"
    @State private var allergies: String = "Penicillin"
    @State private var emergencyContact: String = "Jane Doe: +44 7700 900456"
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("EMERGENCY")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(theme.palette.danger)
                        .padding(.top, 16)
                    
                    Text("Public In Case of Emergency (ICE) display for first responders.")
                        .font(.system(size: 14))
                        .foregroundColor(theme.palette.mist)
                    
                    GlassPanel {
                        SectionLabel("Medical Profile")
                        Text(fullName)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        MetricRow("Blood Type", bloodType)
                        MetricRow("Allergies", allergies)
                        MetricRow("Emergency Contact", emergencyContact)
                    }
                    
                    if !isUnlocked {
                        HuntButton("Unlock to Edit (Face ID / Touch ID)", icon: "faceid") {
                            authenticate()
                        }
                        .padding(.top, 8)
                    } else {
                        GlassPanel {
                            SectionLabel("Edit Profile")
                            TextField("Full Name", text: $fullName)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            TextField("Blood Type", text: $bloodType)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            TextField("Allergies", text: $allergies)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            TextField("Emergency Contact", text: $emergencyContact)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            
                            Button("Lock Profile") {
                                isUnlocked = false
                            }
                            .foregroundColor(theme.palette.bronze)
                            .padding(.top, 8)
                        }
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func authenticate() {
        let context = LAContext()
        var error: NSError?
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Unlock emergency profile to edit") { success, _ in
                DispatchQueue.main.async {
                    if success {
                        self.isUnlocked = true
                        SoundManager.shared.play(.success)
                    }
                }
            }
        } else {
            // Simulator or device fallback
            self.isUnlocked = true
        }
    }
}
