import SwiftUI
import UIKit

/// Persistent app chrome matching Android's `HunTracKAppScaffold`.
///
/// Android always shows a top app bar (tappable title, theme cycler, sound
/// toggle) plus a floating "mark current location" button. iOS previously hid
/// the navigation bar on every tab and showed none of these.
public struct HuntTopBar: View {
    let onTitleTap: () -> Void

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared

    public init(onTitleTap: @escaping () -> Void) {
        self.onTitleTap = onTitleTap
    }

    public var body: some View {
        HStack(spacing: 4) {
            Button(action: onTitleTap) {
                Label("Home", systemImage: "house.fill")
                    .labelStyle(.iconOnly)
                    .frame(width: 44, height: 44)
                    .foregroundColor(theme.palette.bronzeBright)
            }
            .accessibilityLabel("Back to Home")

            Button(action: {
                SoundManager.shared.play(.click)
                onTitleTap()
            }) {
                Text("HunTracK")
                    .font(.field(size: 18, weight: .bold))
                    .foregroundColor(theme.palette.cream)
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            Button(action: cycleTheme) {
                Image(systemName: themeIconName)
                    .font(.field(size: 18, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(PlainButtonStyle())
            .accessibilityLabel(themeAccessibilityLabel)

            Button(action: toggleSound) {
                Image(systemName: db.preferences.soundsEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    .font(.field(size: 18, weight: .semibold))
                    .foregroundColor(db.preferences.soundsEnabled ? theme.palette.bronzeBright : theme.palette.mist)
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(PlainButtonStyle())
            .accessibilityLabel(db.preferences.soundsEnabled ? "Mute sounds" : "Unmute sounds")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(theme.palette.forestDeep)
        .overlay(
            Rectangle()
                .fill(theme.palette.glassBorder)
                .frame(height: 1),
            alignment: .bottom
        )
    }

    private var themeIconName: String {
        switch db.preferences.themeMode {
        case .woodland: return "tree.fill"
        case .night: return "moon.fill"
        case .hunt: return "flame.fill"
        }
    }

    private var themeAccessibilityLabel: String {
        switch db.preferences.themeMode {
        case .woodland: return "Theme: Woodland. Tap for Night"
        case .night: return "Theme: Night. Tap for Hunt"
        case .hunt: return "Theme: Hunt. Tap for Woodland"
        }
    }

    private func cycleTheme() {
        SoundManager.shared.play(.click)
        let next: HuntThemeMode
        switch db.preferences.themeMode {
        case .woodland: next = .night
        case .night: next = .hunt
        case .hunt: next = .woodland
        }
        db.preferences.themeMode = next
    }

    private func toggleSound() {
        let next = !db.preferences.soundsEnabled
        db.preferences.soundsEnabled = next
        if next {
            SoundManager.shared.play(.click)
        }
    }
}

/// Android's floating "Mark current location" action button.
public struct HuntMarkLocationButton: View {
    let action: () -> Void

    @ObservedObject private var theme = ThemeManager.shared

    public init(action: @escaping () -> Void) {
        self.action = action
    }

    public var body: some View {
        Button(action: {
            SoundManager.shared.play(.click)
            action()
        }) {
            Image(systemName: "mappin.and.ellipse")
                .font(.field(size: 22, weight: .bold))
                .foregroundColor(theme.palette.charcoal)
                .frame(width: 56, height: 56)
                .background(RoundedRectangle(cornerRadius: 12).fill(theme.palette.bronze))
                .shadow(radius: 4)
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityLabel("Mark current location")
    }
}

/// Quick-add marker sheet used by the floating button.
///
/// Android's sheet additionally surfaces GPS readiness and an "save area as
/// offline map" option; this mirrors the marker half of it.
public struct HuntQuickAddMarkerSheet: View {
    @Binding var isPresented: Bool

    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var location = LocationManager.shared

    @State private var name: String = ""
    @State private var category: MarkerCategory = .highSeat
    @State private var notes: String = ""

    public init(isPresented: Binding<Bool>) {
        _isPresented = isPresented
    }

    public var body: some View {
        NavigationView {
            ScreenBackground {
                Form {
                    Section(header: Text("Marker Details").foregroundColor(theme.palette.bronze)) {
                        TextField("Name", text: $name)
                        Picker("Category", selection: $category) {
                            ForEach(MarkerCategory.allCases, id: \.self) { cat in
                                Text(cat.label).tag(cat)
                            }
                        }
                        TextField("Notes (optional)", text: $notes)
                    }

                    Section(header: Text("Location").foregroundColor(theme.palette.bronze)) {
                        let fix = location.latestFix ?? location.fallbackFix()
                        Text(String(format: "%.5f, %.5f", fix.latitude, fix.longitude))
                        if let grid = OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude) {
                            Text(grid).foregroundColor(theme.palette.bronzeBright)
                        }
                        if location.latestFix == nil {
                            Text("Using UK fallback until satellite lock")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.gold)
                        }
                    }

                    Section {
                        Button("Save Marker") { save() }
                            .foregroundColor(theme.palette.bronze)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Add Field Marker")
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(leading: Button("Cancel") { isPresented = false })
        }
    }

    private func save() {
        let fix = location.latestFix ?? location.fallbackFix()
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let marker = FieldMarker(
            category: category,
            name: trimmed.isEmpty ? category.label : trimmed,
            notes: notes,
            latitude: fix.latitude,
            longitude: fix.longitude,
            elevationMeters: fix.altitudeMeters
        )
        db.addMarker(marker)
        isPresented = false
    }
}

/// Wraps a tab's content with the persistent top bar, matching Android's scaffold.
public struct HuntTabShell<Content: View>: View {
    let onTitleTap: () -> Void
    let content: Content
    @State private var navigationID = UUID()

    public init(onTitleTap: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.onTitleTap = onTitleTap
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: 0) {
            HuntTopBar {
                navigationID = UUID()
                onTitleTap()
            }
            NavigationStack {
                content
                    .navigationBarTitleDisplayMode(.inline)
            }
            .id(navigationID)
            .tint(ThemeManager.shared.palette.bronzeBright)
        }
    }
}
