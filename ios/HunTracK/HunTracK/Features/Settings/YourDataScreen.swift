import SwiftUI
import UniformTypeIdentifiers

public struct YourDataScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @State private var includeMedical = false
    @State private var applyMedical = false
    @State private var message: String?
    @State private var shareURL: URL?
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
                    Text("Photos and PDFs stay on the device. The PIN and biometric unlock are never written into the file. The zip holds JSON plus GPX, KML and GeoJSON.")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    Toggle("Include medical profile", isOn: $includeMedical)
                    if let shareURL {
                        ShareLink(item: shareURL, preview: SharePreview("HunTracK field backup")) {
                            Text("Share backup")
                        }
                    } else {
                        Button("Prepare backup") { prepareBackup() }
                    }
                }
                Section(header: sectionHeader("Import")) {
                    Text("Adds permissions, boundaries, markers and tracks. It does not delete what is already here.")
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

    private func prepareBackup() {
        let backup = FieldBackupCodec.build(from: db, includeMedical: includeMedical)
        do {
            let json = try FieldBackupCodec.encode(backup)
            let zip = StoredZip.write([
                FieldBackupFormat.jsonName: json,
                FieldBackupFormat.gpxName: Data(GeoFormats.gpx(backup).utf8),
                FieldBackupFormat.kmlName: Data(GeoFormats.kml(backup).utf8),
                FieldBackupFormat.geoJsonName: Data(GeoFormats.geoJson(backup).utf8)
            ])
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(FieldBackupFormat.zipName)
            try zip.write(to: url)
            shareURL = url
            message = backup.includesMedicalProfile
                ? "Backup ready. Medical profile included."
                : "Backup ready. Medical profile left out."
        } catch {
            message = "Could not build the backup."
        }
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
