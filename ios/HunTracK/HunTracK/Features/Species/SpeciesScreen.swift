import SwiftUI

// MARK: - Season status

/// Result of comparing a season's DD-MM range against today's date.
public enum SeasonDisplayState {
    case open
    case closed
    case unknown
}

/// Port of the Android `SeasonStatus` object.
///
/// Season values in the catalog are DD-MM (UK style), e.g. red stag 01-08 -> 30-04.
/// Ranges are inclusive and may wrap across New Year, so Aug -> Apr counts as open
/// from 1 Aug through 31 Dec and again from 1 Jan through 30 Apr.
public enum SeasonStatus {

    private static let dash = "\u{2014}"
    private static let monthNames = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    ]

    public static func state(openFrom: String, openTo: String, now: Date = Date()) -> SeasonDisplayState {
        guard let from = parse(openFrom), let to = parse(openTo) else { return .unknown }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? TimeZone.current
        let parts = calendar.dateComponents([.month, .day], from: now)
        guard let month = parts.month, let day = parts.day else { return .unknown }

        let today = ordinal(month: month, day: day)
        let lower = ordinal(month: from.month, day: from.day)
        let upper = ordinal(month: to.month, day: to.day)

        let inRange: Bool
        if lower <= upper {
            inRange = today >= lower && today <= upper
        } else {
            // Wraps across New Year, e.g. 01-08 -> 30-04.
            inRange = today >= lower || today <= upper
        }
        return inRange ? .open : .closed
    }

    /// True when the range is a placeholder rather than a real set of dates.
    public static func isPlaceholder(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || trimmed == dash || trimmed == "-" || trimmed == "\u{2013}"
    }

    /// Parses DD-MM (also accepts DD/MM and DD.MM) into month and day.
    private static func parse(_ value: String) -> (month: Int, day: Int)? {
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !isPlaceholder(cleaned) else { return nil }

        let separators = CharacterSet(charactersIn: "-/.")
        let parts = cleaned.components(separatedBy: separators)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        guard parts.count == 2 else { return nil }

        guard let day = Int(parts[0]), let month = Int(parts[1]) else { return nil }
        guard month >= 1, month <= 12, day >= 1, day <= 31 else { return nil }
        return (month: month, day: day)
    }

    private static func ordinal(month: Int, day: Int) -> Int {
        month * 100 + day
    }

    /// "01-08" -> "1 Aug"; falls back to the raw value when it cannot be parsed.
    public static func formatDate(_ ddMm: String) -> String {
        guard let parsed = parse(ddMm) else { return ddMm }
        return "\(parsed.day) \(monthNames[parsed.month - 1])"
    }

    /// "01-08" - "30-04" -> "1 Aug - 30 Apr", or a plain-language note for placeholders.
    public static func formatRange(from: String, to: String) -> String {
        if isPlaceholder(from) || isPlaceholder(to) {
            return "Dates vary, check local authority"
        }
        return "\(formatDate(from)) \u{2013} \(formatDate(to))"
    }

    /// Compact one-line preview used on the species list cards.
    public static func previewLine(speciesId: String, nation: UkNation) -> String? {
        var rows = UkFieldCatalog.seasonsFor(speciesId, nation: nation)
        if rows.isEmpty {
            rows = UkFieldCatalog.seasonsFor(speciesId, nation: .england)
        }
        if rows.isEmpty { return nil }

        let deerClasses = rows.filter { row in
            let label = row.sexLabel.lowercased()
            return label.contains("stag") || label.contains("buck")
                || label.contains("hind") || label.contains("doe")
        }
        if deerClasses.count >= 2 {
            return deerClasses
                .map { "\($0.sexLabel) \($0.openFromMonthDay)\u{2013}\($0.openToMonthDay)" }
                .joined(separator: " \u{00B7} ")
        }

        guard let first = rows.first else { return nil }
        if isPlaceholder(first.openFromMonthDay) {
            let note = first.notes.trimmingCharacters(in: .whitespacesAndNewlines)
            return note.isEmpty ? nil : String(note.prefix(80))
        }
        return "\(first.sexLabel): \(first.openFromMonthDay) \u{2013} \(first.openToMonthDay)"
    }
}

// MARK: - Species list

public struct SpeciesScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var searchText: String = ""
    /// nil means "All". This filter is applied to the list (previously dead state).
    @State private var selectedGroup: SpeciesGroup? = nil
    @State private var nation: UkNation = .england

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    header
                    searchField
                    groupChips
                    nationChips
                    disclaimerPanel
                    speciesList
                }
                .padding(.bottom, 24)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("UK Species")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text("Tap any species for ID guide & seasons")
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }

    // MARK: Search

    private var searchField: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(theme.palette.mist)
            TextField("Search UK quarry & wildlife", text: $searchText)
                .foregroundColor(theme.palette.cream)
            if !searchText.isEmpty {
                Button(action: { searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(theme.palette.mist)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(theme.palette.glass)
        )
        .padding(.horizontal, 16)
    }

    // MARK: Filters

    private var groupChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                SpeciesFilterChip(title: "All", selected: selectedGroup == nil) {
                    selectedGroup = nil
                }
                ForEach(SpeciesGroup.allCases, id: \.self) { group in
                    SpeciesFilterChip(title: group.label, selected: selectedGroup == group) {
                        // Tapping the active group clears it, matching chip behaviour.
                        selectedGroup = (selectedGroup == group) ? nil : group
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var nationChips: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionLabel("Legal seasons nation")
                .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(UkNation.allCases, id: \.self) { item in
                        SpeciesFilterChip(title: item.label, selected: nation == item) {
                            nation = item
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private var disclaimerPanel: some View {
        GlassPanel {
            Text(UkFieldCatalog.legalDisclaimer)
                .font(.system(size: 12))
                .foregroundColor(theme.palette.gold)
        }
        .padding(.horizontal, 16)
    }

    // MARK: List

    private var speciesList: some View {
        LazyVStack(spacing: 10) {
            if filteredSpecies.isEmpty {
                Text("No species match your filters.")
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.mist)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
            } else {
                ForEach(filteredSpecies) { species in
                    NavigationLink(
                        destination: SpeciesDetailScreen(species: species, nation: nation)
                    ) {
                        speciesCard(species)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private func speciesCard(_ species: Species) -> some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: 4) {
                Text(species.commonName)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                Text(species.scientificName)
                    .font(.system(size: 13, weight: .light))
                    .italic()
                    .foregroundColor(theme.palette.bronze)
                Text(species.summary)
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.mist)
                    .lineLimit(3)
                    .padding(.top, 2)

                if let preview = SeasonStatus.previewLine(speciesId: species.id, nation: nation) {
                    Text(preview)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                        .padding(.top, 6)
                }
            }
        }
    }

    /// Applies BOTH the search text and the group filter.
    private var filteredSpecies: [Species] {
        UkFieldCatalog.species.filter { species in
            if let group = selectedGroup, species.group != group {
                return false
            }
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            if query.isEmpty { return true }
            return species.commonName.localizedCaseInsensitiveContains(query)
                || species.scientificName.localizedCaseInsensitiveContains(query)
                || species.summary.localizedCaseInsensitiveContains(query)
        }
    }
}

// MARK: - Filter chip

private struct SpeciesFilterChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: selected ? .bold : .semibold))
                .foregroundColor(selected ? theme.palette.ink : theme.palette.cream)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(selected ? theme.palette.bronze : theme.palette.slate)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Species detail

public struct SpeciesDetailScreen: View {
    let species: Species
    @ObservedObject private var theme = ThemeManager.shared
    @State private var selectedNation: UkNation
    @State private var showGooseInfo: Bool = false

    public init(species: Species, nation: UkNation = .england) {
        self.species = species
        _selectedNation = State(initialValue: nation)
    }

    private var sexTerms: (male: String, female: String) {
        UkFieldCatalog.sexTermsFor(species.id)
    }

    private var seasons: [LegalSeason] {
        UkFieldCatalog.seasonsFor(species.id, nation: selectedNation)
    }

    private var relatedCalls: [AnimalCall] {
        UkFieldCatalog.callsFor(species.id)
    }

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    titleBlock
                    overviewPanel
                    identificationPanel
                    fieldSignsPanel
                    habitatPanel
                    nationSelector
                    seasonsSection
                    gooseSection
                    relatedCallsSection
                    disclaimerFooter
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showGooseInfo) {
            gooseInfoSheet
        }
    }

    // MARK: Sections

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(species.commonName)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text(species.scientificName)
                .font(.system(size: 16, weight: .medium))
                .italic()
                .foregroundColor(theme.palette.bronze)
        }
        .padding(.top, 8)
    }

    private var overviewPanel: some View {
        GlassPanel {
            SectionLabel("Overview")
            Text(species.summary)
                .foregroundColor(theme.palette.cream)
        }
    }

    private var identificationPanel: some View {
        GlassPanel {
            SectionLabel("Identification")
            SpeciesTraitBlock(label: sexTerms.male, text: species.maleTraits)
            SpeciesTraitBlock(label: sexTerms.female, text: species.femaleTraits)
            SpeciesTraitBlock(label: "Young", text: species.youngTraits)
        }
    }

    private var fieldSignsPanel: some View {
        GlassPanel {
            SectionLabel("Field signs")
            SpeciesTraitBlock(label: "Tracks", text: species.tracks)
            SpeciesTraitBlock(label: "Droppings", text: species.droppings)
        }
    }

    private var habitatPanel: some View {
        GlassPanel {
            SectionLabel("Habitat & behaviour")
            SpeciesTraitBlock(label: "Habitat", text: species.habitat)
            SpeciesTraitBlock(label: "Behaviour", text: species.behaviour)
            SpeciesTraitBlock(label: "Distribution", text: species.distribution)
        }
    }

    private var nationSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel("Legal seasons")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(UkNation.allCases, id: \.self) { item in
                        SpeciesFilterChip(title: item.label, selected: selectedNation == item) {
                            selectedNation = item
                        }
                    }
                }
            }
        }
    }

    private var seasonsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            if seasons.isEmpty {
                GlassPanel {
                    Text("No season rows for this nation \u{2014} verify with local authority.")
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                    if !species.seasonsNote.isEmpty {
                        Text(species.seasonsNote)
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 4)
                    }
                }
            } else {
                if !species.seasonsNote.isEmpty {
                    Text(species.seasonsNote)
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
                // LegalSeason is Equatable but NOT Hashable, so index the array.
                ForEach(seasons.indices, id: \.self) { index in
                    SeasonCardView(season: seasons[index])
                }
            }
        }
    }

    @ViewBuilder
    private var gooseSection: some View {
        if UkFieldCatalog.isGooseSpecies(species.id) {
            HuntButton("More information", icon: "info.circle") {
                showGooseInfo = true
            }
        }
    }

    @ViewBuilder
    private var relatedCallsSection: some View {
        if !relatedCalls.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                SectionLabel("Related calls")
                GlassPanel {
                    ForEach(relatedCalls) { call in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(call.name)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(call.description)
                                .font(.system(size: 12))
                                .foregroundColor(theme.palette.mist)
                            Text("Open Calls from More to listen")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                                .padding(.top, 2)
                        }
                        .padding(.bottom, 8)
                    }
                }
            }
        }
    }

    private var disclaimerFooter: some View {
        Text(UkFieldCatalog.legalDisclaimer)
            .font(.system(size: 11))
            .foregroundColor(theme.palette.mist)
            .padding(.top, 4)
    }

    private var gooseParagraphs: [String] {
        UkFieldCatalog.gooseMoreInformation
            .components(separatedBy: "\n\n")
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private var gooseInfoSheet: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Geese \u{2014} more information")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.top, 16)

                    // Index rather than destructuring the enumerated tuple: Swift closures
                    // cannot destructure a single tuple parameter.
                    ForEach(gooseParagraphs.indices, id: \.self) { index in
                        Text(gooseParagraphs[index])
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }

                    HuntButton("Close", icon: "xmark") {
                        showGooseInfo = false
                    }
                    .padding(.top, 8)
                }
                .padding(16)
            }
        }
    }
}

// MARK: - Season card

private struct SeasonCardView: View {
    let season: LegalSeason
    @ObservedObject private var theme = ThemeManager.shared

    private var state: SeasonDisplayState {
        SeasonStatus.state(openFrom: season.openFromMonthDay, openTo: season.openToMonthDay)
    }

    private var isClosedRealDates: Bool {
        state == .closed
            && !SeasonStatus.isPlaceholder(season.openFromMonthDay)
            && !SeasonStatus.isPlaceholder(season.openToMonthDay)
    }

    var body: some View {
        GlassPanel {
            statusButtons

            Text(season.sexLabel)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(theme.palette.cream)
                .padding(.top, 10)

            Text(SeasonStatus.formatRange(from: season.openFromMonthDay, to: season.openToMonthDay))
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(theme.palette.bronzeBright)
                .padding(.top, 2)

            if isClosedRealDates {
                Text("Currently closed \u{2014} do not shoot this class.")
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.danger)
                    .padding(.top, 6)
            }

            if !season.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(season.notes)
                    .font(.system(size: 12))
                    .foregroundColor(theme.palette.gold)
                    .padding(.top, 6)
            }
        }
    }

    @ViewBuilder
    private var statusButtons: some View {
        if state == .unknown {
            SeasonStatusPill(
                label: "Check season dates",
                active: true,
                activeColor: theme.palette.gold
            )
        } else {
            HStack(spacing: 8) {
                SeasonStatusPill(
                    label: "Season open",
                    active: state == .open,
                    activeColor: theme.palette.success
                )
                SeasonStatusPill(
                    label: "Season closed",
                    active: state == .closed,
                    activeColor: theme.palette.danger
                )
            }
        }
    }
}

private struct SeasonStatusPill: View {
    let label: String
    let active: Bool
    let activeColor: Color
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        Text(label)
            .font(.system(size: 13, weight: active ? .bold : .regular))
            .foregroundColor(active ? activeColor : theme.palette.mist.opacity(0.65))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(active ? activeColor.opacity(0.28) : theme.palette.charcoal.opacity(0.45))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        active ? activeColor : theme.palette.slateLight.opacity(0.5),
                        lineWidth: active ? 1.5 : 1
                    )
            )
    }
}

// MARK: - Trait block

/// Label-above-text block; `MetricRow` puts long prose in a cramped trailing column.
private struct SpeciesTraitBlock: View {
    let label: String
    let text: String
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 3) {
                Text(label)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                Text(text)
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.cream)
            }
            .padding(.bottom, 8)
        }
    }
}
