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
                .font(.field(size: 16, weight: .bold))
                .foregroundColor(theme.palette.charcoal)
                .frame(width: 42, height: 42)
                .background(RoundedRectangle(cornerRadius: 10).fill(theme.palette.bronze))
                .shadow(radius: 3)
                .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(PlainButtonStyle())
        .accessibilityLabel("Mark current location")
    }
}

/// Same marker sheet as Android's "Mark location": group, then species or category, then name.
/// iOS does not download offline map tiles, so this sheet does not offer that.
public struct MarkLocationSheet: View {
    var pinnedLatitude: Double? = nil
    var pinnedLongitude: Double? = nil
    @Binding var isPresented: Bool

    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var location = LocationManager.shared

    @State private var name: String = ""
    @State private var notes: String = ""
    @State private var group: MarkerCategoryGroup = .wildlife
    @State private var speciesGroup: SpeciesGroup = .deer
    @State private var speciesId: String = "red_deer"
    @State private var category: MarkerCategory = .deer

    public init(isPresented: Binding<Bool>, pinnedLatitude: Double? = nil, pinnedLongitude: Double? = nil) {
        _isPresented = isPresented
        self.pinnedLatitude = pinnedLatitude
        self.pinnedLongitude = pinnedLongitude
    }

    public var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        header
                        groupRow
                        choiceRow
                        nameField
                        markerPreview
                        notesField
                        locationLine
                        saveButton
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Mark location")
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(leading: Button("Cancel") { isPresented = false })
        }
        .onAppear { location.startUpdating() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Mark location")
                .font(.field(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text(pinnedLatitude == nil
                 ? "Places at your current GPS position · private on device"
                 : "Saves at this point · private on device")
                .font(.field(size: 13))
                .foregroundColor(theme.palette.mist)
        }
    }

    private var groupRow: some View {
        chipScroller(MarkerCategoryGroup.allCases, id: \.self, selected: group, title: { $0.label }) { chosen in
            group = chosen
            if chosen == .wildlife {
                speciesGroup = .deer
                speciesId = firstSpecies(in: .deer)?.id ?? speciesId
                category = .deer
            } else if let first = MarkerCategory.allCases.first(where: { $0.group == chosen && $0 != .homing }) {
                category = first
            }
        }
    }

    @ViewBuilder
    private var choiceRow: some View {
        if group == .wildlife {
            chipScroller(SpeciesGroup.allCases, id: \.self, selected: speciesGroup, title: speciesChipTitle) { chosen in
                speciesGroup = chosen
                if let first = firstSpecies(in: chosen) {
                    speciesId = first.id
                    category = MarkerArtwork.category(for: first)
                }
            }
            chipScroller(species(in: speciesGroup), id: \.id, selectedId: speciesId, title: { $0.commonName }) { chosen in
                speciesId = chosen.id
                category = MarkerArtwork.category(for: chosen)
            }
        } else {
            chipScroller(categoryChoices, id: \.self, selected: category, title: { $0.label }) { chosen in
                category = chosen
            }
        }
    }

    private var categoryChoices: [MarkerCategory] {
        MarkerCategory.allCases.filter { $0.group == group && $0 != .homing }
    }

    private var nameField: some View {
        TextField("Name", text: $name)
            .textFieldStyle(RoundedBorderTextFieldStyle())
    }

    private var markerPreview: some View {
        let previewCategory = resolvedCategory
        let previewSpecies = group == .wildlife ? speciesId : nil
        return VStack(spacing: 6) {
            Text("Map marker")
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
            Image(uiImage: MarkerArtwork.icon(category: previewCategory, speciesId: previewSpecies, diameter: 56))
                .resizable()
                .frame(width: 56, height: 56)
            Text(previewName)
                .font(.field(size: 13))
                .foregroundColor(theme.palette.bronzeBright)
        }
        .frame(maxWidth: .infinity)
    }

    private var notesField: some View {
        TextField("Notes", text: $notes)
            .textFieldStyle(RoundedBorderTextFieldStyle())
    }

    private var locationLine: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let latitude = targetLatitude, let longitude = targetLongitude {
                if let grid = OsGridRef.latLngToOsGrid(latitude: latitude, longitude: longitude) {
                    Text(grid)
                        .font(.field(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                }
                Text(String(format: "Pin: %.5f, %.5f", latitude, longitude))
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.mist)
            } else {
                Text("Waiting for GPS lock…")
                    .font(.field(size: 13))
                    .foregroundColor(theme.palette.gold)
            }
            Text("Map tiles stay online on iPhone. The marker itself is stored on this device.")
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
        }
    }

    private var saveButton: some View {
        Button(action: save) {
            Text("Save marker here")
                .font(.field(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 12).fill(theme.palette.bronze))
                .foregroundColor(theme.palette.charcoal)
        }
        .disabled(targetLatitude == nil)
    }

    private var targetLatitude: Double? { pinnedLatitude ?? location.latestFix?.latitude }
    private var targetLongitude: Double? { pinnedLongitude ?? location.latestFix?.longitude }

    private var resolvedCategory: MarkerCategory {
        if group == .wildlife, let species = UkFieldCatalog.speciesById(speciesId) {
            return MarkerArtwork.category(for: species)
        }
        return category
    }

    private var previewName: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        if group == .wildlife, let species = UkFieldCatalog.speciesById(speciesId) {
            return species.commonName
        }
        return category.label
    }

    private func save() {
        guard let latitude = targetLatitude, let longitude = targetLongitude else { return }
        let marker = FieldMarker(
            category: resolvedCategory,
            name: previewName,
            notes: notes,
            latitude: latitude,
            longitude: longitude,
            elevationMeters: pinnedLatitude == nil ? location.latestFix?.altitudeMeters : nil,
            speciesId: group == .wildlife ? speciesId : nil
        )
        db.addMarker(marker)
        isPresented = false
    }

    private func species(in group: SpeciesGroup) -> [Species] {
        UkFieldCatalog.species.filter { $0.group == group }
    }

    private func firstSpecies(in group: SpeciesGroup) -> Species? {
        species(in: group).first
    }

    private func speciesChipTitle(_ group: SpeciesGroup) -> String {
        switch group {
        case .deer: return "Deer"
        case .otherQuarry: return "Quarry"
        case .bird: return "Birds"
        case .waterfowl: return "Wildfowl"
        case .corvid: return "Corvids"
        case .pest: return "Pest"
        }
    }

    private func chipScroller<Item: Hashable>(
        _ items: [Item],
        id: KeyPath<Item, Item>,
        selected: Item,
        title: @escaping (Item) -> String,
        choose: @escaping (Item) -> Void
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: id) { item in
                    chip(title(item), on: item == selected) { choose(item) }
                }
            }
        }
    }

    private func chipScroller(
        _ items: [Species],
        id: KeyPath<Species, String>,
        selectedId: String,
        title: @escaping (Species) -> String,
        choose: @escaping (Species) -> Void
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: id) { item in
                    chip(title(item), on: item.id == selectedId) { choose(item) }
                }
            }
        }
    }

    private func chip(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.field(size: 14, weight: .semibold))
                .foregroundColor(on ? theme.palette.charcoal : theme.palette.cream)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(on ? theme.palette.bronze : theme.palette.slate.opacity(0.55)))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

public struct HuntQuickAddMarkerSheet: View {
    @Binding var isPresented: Bool

    public init(isPresented: Binding<Bool>) {
        _isPresented = isPresented
    }

    public var body: some View {
        MarkLocationSheet(isPresented: $isPresented)
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
