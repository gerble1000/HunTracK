import SwiftUI

public struct SettingsScreen: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    public init() {}

    public var body: some View {
        ScreenBackground {
            Form {
                Section(header: sectionHeader("Account")) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Local guest")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Text("Cloud accounts coming soon")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.vertical, 2)
                }

                Section(header: sectionHeader("Display theme")) {
                    Picker("Theme mode", selection: themeModeBinding) {
                        ForEach(HuntThemeMode.allCases, id: \.self) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                }

                Section(header: sectionHeader("Units")) {
                    Picker("Units", selection: unitSystemBinding) {
                        Text("Metric").tag(UnitSystem.metric)
                        Text("Imperial").tag(UnitSystem.imperial)
                    }
                    .pickerStyle(.segmented)

                    Text(unitSystemSummary)
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)

                    Toggle("Use Celsius", isOn: useCelsiusBinding)
                    Toggle("Wind in mph", isOn: windUnitBinding)
                }

                Section(header: sectionHeader("Map style")) {
                    Picker("Basemap", selection: mapLayerStyleBinding) {
                        Text("Street").tag(MapLayerStyle.street)
                        Text("Outdoor").tag(MapLayerStyle.outdoor)
                        Text("Satellite").tag(MapLayerStyle.satellite)
                    }
                }

                Section(header: sectionHeader("OS Grid & culls")) {
                    Toggle("Show OS Grid lines & refs", isOn: showOsGridRefBinding)
                    Toggle("Show cull pins on map", isOn: showCullPinsOnMapBinding)
                }

                Section(header: sectionHeader("Markers")) {
                    Toggle("Mark anywhere on map", isOn: markAnywhereBinding)
                }

                Section(header: sectionHeader("Sound")) {
                    Toggle("UI sounds", isOn: soundsEnabledBinding)
                    Toggle("Vibration", isOn: vibrationEnabledBinding)
                }

                Section(header: sectionHeader("Field training")) {
                    Toggle("Map training", isOn: mapTrainingBinding)
                    Toggle("Wind & scent training", isOn: windTrainingBinding)
                }

                Section(header: sectionHeader("Tides")) {
                    TideSettingsSection()
                }

                Section(header: sectionHeader("Storage")) {
                    Button(action: {
                        db.clearWeatherCache()
                        SoundManager.shared.play(.click)
                    }) {
                        Text("Clear weather cache")
                            .foregroundColor(theme.palette.bronzeBright)
                    }
                    Text("Offline map packs are managed from the Map screen.")
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)
                }

                Section(header: sectionHeader("About HunTracK")) {
                    NavigationLink(destination: AboutScreen()) {
                        Text("About HunTracK")
                            .foregroundColor(theme.palette.cream)
                    }
                    HStack {
                        Text("Version")
                            .foregroundColor(theme.palette.cream)
                        Spacer()
                        Text("1.1.27 (31)")
                            .foregroundColor(theme.palette.mist)
                    }
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> Text {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(theme.palette.bronze)
    }

    private var unitSystemSummary: String {
        switch db.preferences.unitSystem {
        case .metric: return "Metres \u{00B7} km/h \u{00B7} \u{00B0}C"
        case .imperial: return "Yards \u{00B7} mph \u{00B7} \u{00B0}F"
        }
    }

    // MARK: - Preference bindings
    //
    // Each control writes through `db.preferences`, whose `didSet` persists the
    // whole struct and re-applies theme/sound. Explicit `Binding(get:set:)` is
    // used because these are nested struct fields.

    private var themeModeBinding: Binding<HuntThemeMode> {
        Binding(
            get: { db.preferences.themeMode },
            set: { db.preferences.themeMode = $0 }
        )
    }

    private var unitSystemBinding: Binding<UnitSystem> {
        Binding(
            get: { db.preferences.unitSystem },
            set: { db.preferences.unitSystem = $0 }
        )
    }

    private var useCelsiusBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.useCelsius },
            set: { db.preferences.useCelsius = $0 }
        )
    }

    private var windUnitBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.windUnit == .mph },
            set: { db.preferences.windUnit = $0 ? .mph : .kmh }
        )
    }

    private var mapLayerStyleBinding: Binding<MapLayerStyle> {
        Binding(
            get: { db.preferences.mapLayerStyle },
            set: { db.preferences.mapLayerStyle = $0 }
        )
    }

    private var showOsGridRefBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.showOsGridRef },
            set: { db.preferences.showOsGridRef = $0 }
        )
    }

    private var showCullPinsOnMapBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.showCullPinsOnMap },
            set: { db.preferences.showCullPinsOnMap = $0 }
        )
    }

    private var markAnywhereBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.markAnywhereEnabled },
            set: { db.preferences.markAnywhereEnabled = $0 }
        )
    }

    private var soundsEnabledBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.soundsEnabled },
            set: { db.preferences.soundsEnabled = $0 }
        )
    }

    private var vibrationEnabledBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.vibrationEnabled },
            set: { db.preferences.vibrationEnabled = $0 }
        )
    }

    private var mapTrainingBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.mapTrainingEnabled },
            set: { db.preferences.mapTrainingEnabled = $0 }
        )
    }

    private var windTrainingBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.windTrainingEnabled },
            set: { db.preferences.windTrainingEnabled = $0 }
        )
    }
}
