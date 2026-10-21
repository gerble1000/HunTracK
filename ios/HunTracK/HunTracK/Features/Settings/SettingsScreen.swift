import SwiftUI

public struct SettingsScreen: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @State private var showMapGuide: Bool = false
    @State private var showWindGuide: Bool = false

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
                    Button("View Map Training Guide") {
                        showMapGuide = true
                    }
                    .foregroundColor(theme.palette.bronzeBright)

                    Toggle("Wind & scent training", isOn: windTrainingBinding)
                    Button("View Wind & Scent Training Guide") {
                        showWindGuide = true
                    }
                    .foregroundColor(theme.palette.bronzeBright)
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
                        Text("1.1.27 (35)")
                            .foregroundColor(theme.palette.mist)
                    }
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showMapGuide) {
            MapTrainingGuideSheet(isPresented: $showMapGuide)
        }
        .sheet(isPresented: $showWindGuide) {
            WindGuideSheet(isPresented: $showWindGuide)
        }
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> Text {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(theme.palette.bronze)
    }

    private var unitSystemSummary: String {
        switch db.preferences.unitSystem {
        case .metric:
            return "Metric: km/h or m/s wind, metres distance, hectares/sq km area."
        case .imperial:
            return "Imperial: mph wind, yards distance, acres/sq miles area."
        }
    }

    // MARK: - Bindings

    private var themeModeBinding: Binding<HuntThemeMode> {
        Binding(
            get: { theme.mode },
            set: { mode in
                theme.mode = mode
                db.preferences.themeMode = mode
                SoundManager.shared.play(.click)
            }
        )
    }

    private var unitSystemBinding: Binding<UnitSystem> {
        Binding(
            get: { db.preferences.unitSystem },
            set: { newSystem in
                db.preferences.unitSystem = newSystem
                if newSystem == .imperial {
                    db.preferences.useCelsius = false
                    db.preferences.windUnit = .mph
                } else {
                    db.preferences.useCelsius = true
                    db.preferences.windUnit = .kmh
                }
                SoundManager.shared.play(.click)
            }
        )
    }

    private var useCelsiusBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.useCelsius },
            set: {
                db.preferences.useCelsius = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var windUnitBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.windUnit == .mph },
            set: {
                db.preferences.windUnit = $0 ? .mph : .kmh
                SoundManager.shared.play(.click)
            }
        )
    }

    private var mapLayerStyleBinding: Binding<MapLayerStyle> {
        Binding(
            get: { db.preferences.mapLayerStyle },
            set: {
                db.preferences.mapLayerStyle = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var showOsGridRefBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.showOsGridRef },
            set: {
                db.preferences.showOsGridRef = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var showCullPinsOnMapBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.showCullPinsOnMap },
            set: {
                db.preferences.showCullPinsOnMap = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var markAnywhereBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.markAnywhereEnabled },
            set: {
                db.preferences.markAnywhereEnabled = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var soundsEnabledBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.soundsEnabled },
            set: {
                db.preferences.soundsEnabled = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var vibrationEnabledBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.vibrationEnabled },
            set: {
                db.preferences.vibrationEnabled = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var mapTrainingBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.mapTrainingEnabled },
            set: {
                db.preferences.mapTrainingEnabled = $0
                SoundManager.shared.play(.click)
            }
        )
    }

    private var windTrainingBinding: Binding<Bool> {
        Binding(
            get: { db.preferences.windTrainingEnabled },
            set: {
                db.preferences.windTrainingEnabled = $0
                SoundManager.shared.play(.click)
            }
        )
    }
}

public struct MapTrainingGuideSheet: View {
    @Binding var isPresented: Bool
    @ObservedObject private var theme = ThemeManager.shared

    public var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Map Training Guide")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                            .padding(.top, 16)

                        GlassPanel {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Home Point")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("Long-press or tap Home button to mark your vehicle or camp. The map will display distance and heading back to safety.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }

                        GlassPanel {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Range Finder")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("Tap the Range button to measure line-of-sight distance and elevation from your current position to any target.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }

                        GlassPanel {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Live Scent Plume")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("When enabled, the map renders a live scent cone showing downwind dispersal from your current location.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationBarItems(trailing: Button("Done") {
                isPresented = false
            }.foregroundColor(theme.palette.bronzeBright))
        }
    }
}
