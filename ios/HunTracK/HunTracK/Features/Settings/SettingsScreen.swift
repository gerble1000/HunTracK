import SwiftUI

public struct SettingsScreen: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var sound = SoundManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            Form {
                Section(header: Text("Theme & Display").foregroundColor(theme.palette.bronze)) {
                    Picker("Theme Mode", selection: $theme.mode) {
                        ForEach(HuntThemeMode.allCases, id: \.self) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                }
                
                Section(header: Text("Units of Measure").foregroundColor(theme.palette.bronze)) {
                    Toggle("Use Celsius (°C)", isOn: $db.preferences.useCelsius)
                    Toggle("Show OS Grid Ref (UK)", isOn: $db.preferences.showOsGridRef)
                }
                
                Section(header: Text("Feedback & Audio").foregroundColor(theme.palette.bronze)) {
                    Toggle("Sound Effects", isOn: $sound.soundsEnabled)
                    Toggle("Haptic Vibration", isOn: $sound.vibrationEnabled)
                }
                
                Section(header: Text("About HunTracK").foregroundColor(theme.palette.bronze)) {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.1.27 (iOS Ready)")
                            .foregroundColor(theme.palette.mist)
                    }
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}
