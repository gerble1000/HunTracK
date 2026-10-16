import SwiftUI
import Foundation

/// Cull log — spreadsheet-style bag records with optional map pins and CSV export.
/// Ported from `feature/culls/CullScreen.kt` + `CullViewModel.kt`.
public struct CullScreen: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var location = LocationManager.shared

    @State private var filterYear: Int? = CullScreen.londonYear(of: Date())
    @State private var filterSpeciesId: String? = nil
    @State private var showEditor = false
    @State private var editorIsNew = true
    @State private var editorDraft = CullRecord(culledAtEpochMs: 0, speciesLabel: "")
    @State private var message: String? = nil
    @State private var csvURL: URL? = nil

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header

                    summaryPanel

                    SectionLabel("Year")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Chip(label: "All", selected: filterYear == nil) {
                                filterYear = nil
                            }
                            ForEach(availableYears, id: \.self) { year in
                                Chip(label: String(year), selected: filterYear == year) {
                                    filterYear = year
                                }
                            }
                        }
                        .padding(.horizontal, 2)
                    }

                    SectionLabel("Species")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Chip(label: "All", selected: filterSpeciesId == nil) {
                                filterSpeciesId = nil
                            }
                            ForEach(UkFieldCatalog.cullLogSpecies) { species in
                                Chip(
                                    label: species.commonName,
                                    selected: filterSpeciesId == species.id
                                ) {
                                    filterSpeciesId = species.id
                                }
                            }
                        }
                        .padding(.horizontal, 2)
                    }

                    if let message = message {
                        Text(message)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                    }

                    tablePanel
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { refreshCSV() }
        .onChange(of: db.culls) { _ in refreshCSV() }
        .sheet(isPresented: $showEditor) {
            CullEditorSheet(
                draft: $editorDraft,
                isNew: editorIsNew,
                onUseGps: useCurrentGps,
                onApplyOsGrid: applyOsGrid,
                onSave: save,
                onDelete: editorIsNew ? nil : { deleteCull() }
            )
        }
        .task(id: message) {
            guard message != nil else { return }
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            message = nil
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Cull log")
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text("Spreadsheet view for bag records \u{2014} map pins optional")
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 8)
    }

    private var summaryPanel: some View {
        GlassPanel {
            Text("Total in view: \(yearTotal)")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(theme.palette.bronzeBright)
            Text("\(filtered.count) rows")
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)

            HStack(spacing: 10) {
                HuntButton("Add cull") { openNew() }

                if let csvURL = csvURL {
                    ShareLink(item: csvURL, preview: SharePreview("HunTracK cull log")) {
                        Text("Share CSV")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(theme.palette.slate)
                            )
                            .foregroundColor(theme.palette.cream)
                    }
                } else {
                    Text("Share CSV")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(theme.palette.slate)
                        )
                        .foregroundColor(theme.palette.mist)
                }
            }
            .padding(.top, 6)
        }
    }

    private var tablePanel: some View {
        GlassPanel(compact: true) {
            ScrollView(.horizontal, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 0) {
                    tableHeaderRow

                    if filtered.isEmpty {
                        Text("No culls yet \u{2014} tap Add cull")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                            .padding(.vertical, 12)
                    } else {
                        ForEach(filtered) { record in
                            cullRow(record)
                        }
                    }
                }
            }
        }
    }

    private var tableHeaderRow: some View {
        HStack(spacing: 10) {
            tableCell("Date", width: 72, isHeader: true)
            tableCell("Species", width: 100, isHeader: true)
            tableCell("Sex", width: 56, isHeader: true)
            tableCell("Age", width: 64, isHeader: true)
            tableCell("Qty", width: 36, isHeader: true)
            tableCell("OS Grid", width: 120, isHeader: true)
            tableCell("Test", width: 88, isHeader: true)
        }
        .padding(.vertical, 6)
    }

    private func cullRow(_ record: CullRecord) -> some View {
        Button {
            SoundManager.shared.play(.click)
            openEdit(record)
        } label: {
            HStack(spacing: 10) {
                tableCell(Self.dateLabel(record.culledAtEpochMs), width: 72)
                tableCell(record.speciesLabel, width: 100)
                tableCell(record.sex.label, width: 56)
                tableCell(record.ageClass.label, width: 64)
                tableCell(String(record.count), width: 36)
                Text(record.osGridRef.isEmpty ? "\u{2014}" : record.osGridRef)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(theme.palette.bronzeBright)
                    .frame(width: 120, alignment: .leading)
                    .lineLimit(1)
                tableCell(record.carcassTestStatus.label, width: 88)
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(
            Rectangle()
                .fill(theme.palette.glassBorder)
                .frame(height: 1),
            alignment: .bottom
        )
    }

    private func tableCell(_ text: String, width: CGFloat, isHeader: Bool = false) -> some View {
        Text(text)
            .font(.system(size: isHeader ? 11 : 12, weight: isHeader ? .bold : .regular))
            .foregroundColor(isHeader ? theme.palette.mist : theme.palette.cream)
            .frame(width: width, alignment: .leading)
            .lineLimit(1)
    }

    // MARK: - Derived state

    private var availableYears: [Int] {
        let years = Set(db.culls.map { Self.londonYear(of: $0.culledAtEpochMs) }).sorted(by: >)
        return years.isEmpty ? [Self.londonYear(of: Date())] : years
    }

    private var filtered: [CullRecord] {
        db.culls
            .filter { record in
                let yearOk = filterYear == nil || Self.londonYear(of: record.culledAtEpochMs) == filterYear
                let speciesOk = filterSpeciesId == nil || record.speciesId == filterSpeciesId
                return yearOk && speciesOk
            }
            .sorted { $0.culledAtEpochMs > $1.culledAtEpochMs }
    }

    private var yearTotal: Int {
        filtered.reduce(0) { $0 + max(1, $1.count) }
    }

    private static func londonYear(of date: Date) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? TimeZone.current
        return calendar.component(.year, from: date)
    }

    private static func londonYear(of epochMs: Int64) -> Int {
        londonYear(of: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    private static var shortDateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM yy"
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        return formatter
    }

    private static func dateLabel(_ epochMs: Int64) -> String {
        shortDateFormatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    // MARK: - Actions

    private func openNew() {
        let fix = location.latestFix ?? location.fallbackFix()
        let grid = OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude) ?? ""
        let first = UkFieldCatalog.cullLogSpecies.first
        editorDraft = CullRecord(
            culledAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            speciesId: first?.id,
            speciesLabel: first?.commonName ?? "Unknown",
            latitude: fix.latitude,
            longitude: fix.longitude,
            osGridRef: grid
        )
        editorIsNew = true
        showEditor = true
    }

    private func openEdit(_ record: CullRecord) {
        editorDraft = record
        editorIsNew = false
        showEditor = true
    }

    private func useCurrentGps() {
        let fix = location.latestFix ?? location.fallbackFix()
        editorDraft.latitude = fix.latitude
        editorDraft.longitude = fix.longitude
        editorDraft.osGridRef = OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude) ?? ""
    }

    private func applyOsGrid(_ raw: String) {
        guard let latLng = OsGridRef.osGridToLatLng(raw) else {
            message = "Invalid OS Grid Reference"
            return
        }
        editorDraft.latitude = latLng.latitude
        editorDraft.longitude = latLng.longitude
        editorDraft.osGridRef = OsGridRef.latLngToOsGrid(latitude: latLng.latitude, longitude: latLng.longitude)
            ?? raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        message = nil
    }

    private func save(placeMapPin: Bool) {
        if editorDraft.speciesLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            message = "Species is required"
            return
        }

        var linkedId = editorDraft.linkedMarkerId
        if placeMapPin, let lat = editorDraft.latitude, let lng = editorDraft.longitude {
            let marker = FieldMarker(
                id: linkedId ?? 0,
                category: .cull,
                name: "\(editorDraft.speciesLabel) cull",
                notes: editorDraft.notes,
                latitude: lat,
                longitude: lng,
                createdAtEpochMs: editorDraft.culledAtEpochMs,
                speciesId: editorDraft.speciesId
            )
            if let existingId = linkedId, db.markers.contains(where: { $0.id == existingId }) {
                if let index = db.markers.firstIndex(where: { $0.id == existingId }) {
                    db.markers[index] = marker
                }
            } else {
                db.addMarker(marker)
                linkedId = db.markers.last?.id
            }
        }

        let grid: String
        if !editorDraft.osGridRef.isEmpty {
            grid = editorDraft.osGridRef
        } else if let lat = editorDraft.latitude, let lng = editorDraft.longitude {
            grid = OsGridRef.latLngToOsGrid(latitude: lat, longitude: lng) ?? ""
        } else {
            grid = ""
        }

        editorDraft.count = max(1, editorDraft.count)
        editorDraft.osGridRef = grid
        editorDraft.linkedMarkerId = linkedId

        if editorIsNew {
            db.addCull(editorDraft)
        } else {
            db.updateCull(editorDraft)
        }

        SoundManager.shared.play(.success)
        showEditor = false
        message = "Cull saved"
    }

    private func deleteCull() {
        let id = editorDraft.id
        guard id != 0 else { return }
        db.deleteCull(id: id)
        SoundManager.shared.play(.success)
        showEditor = false
        message = "Cull deleted"
    }

    private func refreshCSV() {
        let csv = db.cullCSV()
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("hunttrack-cull-log.csv")
        do {
            try csv.write(to: url, atomically: true, encoding: .utf8)
            csvURL = url
        } catch {
            csvURL = nil
        }
    }
}

// MARK: - Editor

private struct CullEditorSheet: View {
    @Binding var draft: CullRecord
    let isNew: Bool
    let onUseGps: () -> Void
    let onApplyOsGrid: (String) -> Void
    let onSave: (Bool) -> Void
    let onDelete: (() -> Void)?

    @ObservedObject private var theme = ThemeManager.shared
    @State private var osGridInput: String = ""
    @State private var placePin: Bool = false

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(isNew ? "Add cull" : "Edit cull")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(theme.palette.cream)

                        SectionLabel("Species")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(UkFieldCatalog.cullLogSpecies) { species in
                                    Chip(
                                        label: species.commonName,
                                        selected: draft.speciesId == species.id
                                    ) {
                                        draft.speciesId = species.id
                                        draft.speciesLabel = species.commonName
                                    }
                                }
                            }
                            .padding(.horizontal, 2)
                        }

                        SectionLabel("Sex")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(CullSex.allCases, id: \.self) { sex in
                                    Chip(label: sex.label, selected: draft.sex == sex) {
                                        draft.sex = sex
                                    }
                                }
                            }
                            .padding(.horizontal, 2)
                        }

                        SectionLabel("Age class")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(CullAgeClass.allCases, id: \.self) { age in
                                    Chip(label: age.label, selected: draft.ageClass == age) {
                                        draft.ageClass = age
                                    }
                                }
                            }
                            .padding(.horizontal, 2)
                        }

                        SectionLabel("Count")
                        TextField("Count", value: $draft.count, format: .number)
                            .keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)

                        SectionLabel("Location name")
                        TextField("Location name", text: $draft.locationName)
                            .textFieldStyle(.roundedBorder)

                        SectionLabel("Carcass test")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(CarcassTestStatus.allCases, id: \.self) { status in
                                    Chip(label: status.label, selected: draft.carcassTestStatus == status) {
                                        draft.carcassTestStatus = status
                                    }
                                }
                            }
                            .padding(.horizontal, 2)
                        }

                        SectionLabel("OS Grid Reference")
                        TextField("SU 37290 15552", text: $osGridInput)
                            .textFieldStyle(.roundedBorder)
                            .autocapitalization(.allCharacters)
                            .disableAutocorrection(true)

                        HStack(spacing: 8) {
                            Button("Apply grid") { onApplyOsGrid(osGridInput) }
                                .foregroundColor(theme.palette.bronzeBright)
                            Button("Use GPS") { onUseGps() }
                                .foregroundColor(theme.palette.bronzeBright)
                        }

                        if !draft.osGridRef.isEmpty {
                            Text(draft.osGridRef)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                        }

                        SectionLabel("Notes")
                        TextEditor(text: $draft.notes)
                            .frame(minHeight: 70)
                            .padding(4)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(theme.palette.glass)
                            )
                            .foregroundColor(theme.palette.cream)

                        Toggle(isOn: $placePin) {
                            Text("Also place map pin")
                                .foregroundColor(theme.palette.cream)
                        }

                        HuntButton("Save cull") { onSave(placePin) }

                        if let onDelete = onDelete {
                            Button("Delete") { onDelete() }
                                .foregroundColor(theme.palette.danger)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationBarItems(trailing: Button("Cancel") { dismiss() })
        }
        .onAppear {
            osGridInput = draft.osGridRef
            placePin = draft.linkedMarkerId != nil
        }
    }

    @Environment(\.presentationMode) private var presentationMode

    private func dismiss() {
        presentationMode.wrappedValue.dismiss()
    }
}

// MARK: - Chip

private struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(selected ? theme.palette.charcoal : theme.palette.cream)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 9)
                        .fill(selected ? theme.palette.bronze : theme.palette.glass)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 9)
                        .stroke(theme.palette.glassBorder, lineWidth: selected ? 0 : 1)
                )
        }
        .buttonStyle(.plain)
    }
}
