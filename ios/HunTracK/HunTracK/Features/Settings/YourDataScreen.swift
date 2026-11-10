import SwiftUI
import UniformTypeIdentifiers

public struct YourDataScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @State private var includeMedical = false
    @State private var applyMedical = false
    @State private var message: String?
    @State private var shareURL: URL?
    @State private var shareTitle = "HunTracK field backup"
    @State private var showImporter = false

    public init() {}

    public var body: some View {
        ScreenBackground {
            Form {
                Section(header: sectionHeader("On this device")) {
                    Text("\(db.permissions.count) permissions")
                    Text("\(db.boundaries.count) boundaries")
                    Text("\(db.markers.count) markers")
                    Text("\(db.routes.count) tracks")
                }
                Section(header: sectionHeader("Export")) {
                    Text("Photos and PDFs stay on the device. The PIN and biometric unlock are never written into the file.")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    formatNote("Backup zip", "JSON is the full HunTracK backup you can import back onto this phone or another, including danger and sold zones. The same zip also holds GPX, KML and GeoJSON.")
                    formatNote("GPX", "For a GPS unit and most mapping apps. Markers, tracks and zone outlines.")
                    formatNote("KML", "For Google Earth. Boundaries, markers and zone outlines.")
                    formatNote("GeoJSON", "For GIS software and web maps. Boundaries, markers, tracks and zone outlines.")
                    formatNote("Text, not a map", "Permissions, boundary details and zone notes are just text. Only the map shapes need GPX, KML or GeoJSON.")
                    formatNote("CSV", "Spreadsheet files for Excel or Google Sheets. One file each for permissions, boundaries, markers, zones and tracks, in a zip. Each zone names the boundary it sits on.")
                    formatNote("PDF", "A short summary to print and keep: permissions, boundaries with their areas, danger and sold zones, and the marker list.")
                    formatNote("XML", "The same records as structured text, including zones, if a computer or a club system asks for XML.")
                    Toggle("Include medical profile", isOn: $includeMedical)
                    Button("Share backup") { prepare(.backup) }
                    Text("JSON, GPX, KML and GeoJSON, in one zip.")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    Button("Share spreadsheet (CSV)") { prepare(.csv) }
                    Button("Share print summary (PDF)") { prepare(.pdf) }
                    Button("Share XML") { prepare(.xml) }
                    if let shareURL {
                        ShareLink(item: shareURL, preview: SharePreview(shareTitle)) {
                            Text("Send \(shareTitle)")
                        }
                    }
                }
                Section(header: sectionHeader("Import")) {
                    Text("Adds permissions, boundaries, markers and tracks. Zones on a boundary come with it. It does not delete what is already here.")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    Toggle("Apply medical profile", isOn: $applyMedical)
                    Button("Import backup") { showImporter = true }
                }
                if let message {
                    Section {
                        Text(message)
                            .foregroundColor(theme.palette.bronzeBright)
                    }
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Your data")
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .zip, .data]) { result in
            switch result {
            case .success(let url):
                importBackup(url)
            case .failure:
                message = "Could not read that file."
            }
        }
    }

    private func sectionHeader(_ title: String) -> Text {
        Text(title).font(.field(size: 11, weight: .bold)).foregroundColor(theme.palette.bronze)
    }

    private func formatNote(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.field(size: 13, weight: .semibold))
                .foregroundColor(theme.palette.cream)
            Text(body)
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.vertical, 2)
    }

    private enum ShareKind {
        case backup, csv, pdf, xml
    }

    private func prepare(_ kind: ShareKind) {
        let backup = FieldBackupCodec.build(from: db, includeMedical: includeMedical)
        let counts = "\(backup.permissions.count) permissions, \(backup.boundaries.count) boundaries, \(backup.markers.count) markers, \(backup.routes.count) tracks. " +
            (backup.includesMedicalProfile ? "Medical profile included." : "Medical profile left out.")
        do {
            let url: URL
            switch kind {
            case .backup:
                let json = try FieldBackupCodec.encode(backup)
                let zip = StoredZip.write([
                    FieldBackupFormat.jsonName: json,
                    FieldBackupFormat.gpxName: Data(GeoFormats.gpx(backup).utf8),
                    FieldBackupFormat.kmlName: Data(GeoFormats.kml(backup).utf8),
                    FieldBackupFormat.geoJsonName: Data(GeoFormats.geoJson(backup).utf8)
                ])
                url = try writeTemp(zip, FieldBackupFormat.zipName)
                shareTitle = "HunTracK field backup"
                message = "Backup ready: \(counts)"
            case .csv:
                url = try writeTemp(TextFormats.csvZip(backup), TextFormats.csvZipName)
                shareTitle = "HunTracK spreadsheet"
                message = "Spreadsheet ready: \(counts)"
            case .pdf:
                url = try writeTemp(TextFormats.pdf(backup), TextFormats.pdfName)
                shareTitle = "HunTracK print summary"
                message = "Print summary ready: \(counts)"
            case .xml:
                url = try writeTemp(Data(TextFormats.xml(backup).utf8), TextFormats.xmlName)
                shareTitle = "HunTracK XML"
                message = "XML ready: \(counts)"
            }
            shareURL = url
        } catch {
            message = "Could not build the export."
        }
    }

    private func writeTemp(_ data: Data, _ name: String) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try data.write(to: url)
        return url
    }

    private func importBackup(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            message = try db.importFieldBackup(data, applyMedical: applyMedical)
        } catch {
            message = error.localizedDescription
        }
    }
}
