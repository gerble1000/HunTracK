import Foundation

/// Downloads an audio pack's tracks over HTTPS and keeps them in
/// Application Support, outside the iCloud backup.
@MainActor
final class AudioPackDownloader: ObservableObject {
    static let shared = AudioPackDownloader()

    @Published private(set) var activePackId: String?
    /// 0...1 across every track in the pack that is currently downloading.
    @Published private(set) var fraction: Double = 0
    @Published private(set) var detail: String = ""
    @Published private(set) var errors: [String: String] = [:]

    private let session: URLSession
    private var cancelRequested = false

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 30
        session = URLSession(configuration: configuration)
    }

    func reconcileStoredPacks() {
        for pack in DatabaseService.shared.audioPacks where pack.downloaded {
            if !AudioPackFiles.allTracksExist(pack) {
                store(pack, downloaded: false, progress: 0)
            }
        }
    }

    func download(_ pack: AudioPack) async {
        guard activePackId == nil else {
            errors[pack.id] = "Wait for the current download to finish, then try again."
            return
        }
        cancelRequested = false
        errors[pack.id] = nil
        activePackId = pack.id
        fraction = 0
        detail = "Starting download…"
        store(pack, downloaded: false, progress: 0)

        let tracks = pack.tracks
        do {
            guard !tracks.isEmpty else {
                throw AudioPackDownloadError.noTracks
            }
            for (index, track) in tracks.enumerated() {
                try Task.checkCancellation()
                if cancelRequested { throw AudioPackDownloadError.cancelled }
                guard let remote = URL(string: track.remoteUrl), remote.scheme == "https" else {
                    throw AudioPackDownloadError.badURL(track.fileName)
                }
                let destination = try AudioPackFiles.fileURL(packId: pack.id, fileName: track.fileName)
                try await fetch(remote, to: destination, index: index, total: tracks.count)
            }
            guard AudioPackFiles.allTracksExist(pack) else {
                throw AudioPackDownloadError.incomplete
            }
            if cancelRequested { throw AudioPackDownloadError.cancelled }
            store(pack, downloaded: true, progress: 1)
            fraction = 1
            detail = "Downloaded"
            activePackId = nil
        } catch {
            AudioPackFiles.deletePack(pack.id)
            store(pack, downloaded: false, progress: 0)
            fraction = 0
            detail = ""
            if !cancelRequested {
                errors[pack.id] = AudioPackDownloadError.describe(error)
            }
            cancelRequested = false
            activePackId = nil
        }
    }

    func remove(_ pack: AudioPack) {
        if activePackId == pack.id {
            cancelRequested = true
            session.getAllTasks { tasks in
                tasks.forEach { $0.cancel() }
            }
        }
        AudioPackFiles.deletePack(pack.id)
        store(pack, downloaded: false, progress: 0)
        errors[pack.id] = nil
        if activePackId == pack.id {
            fraction = 0
            detail = ""
        }
    }

    private func fetch(_ remote: URL, to destination: URL, index: Int, total: Int) async throws {
        let progress = TrackByteProgress { [weak self] written, expected in
            Task { @MainActor in
                self?.note(written: written, expected: expected, index: index, total: total)
            }
        }
        note(written: 0, expected: -1, index: index, total: total)
        let (temporary, response) = try await session.download(from: remote, delegate: progress)
        if cancelRequested { throw AudioPackDownloadError.cancelled }
        guard let http = response as? HTTPURLResponse else {
            throw AudioPackDownloadError.badStatus(0, remote.host ?? remote.absoluteString)
        }
        guard (200..<300).contains(http.statusCode) else {
            throw AudioPackDownloadError.badStatus(http.statusCode, remote.host ?? remote.absoluteString)
        }
        if let type = http.value(forHTTPHeaderField: "Content-Type")?.lowercased(), type.contains("text/html") {
            throw AudioPackDownloadError.notAudio(remote.host ?? remote.absoluteString)
        }
        let folder = destination.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        AudioPackFiles.excludeFromBackup(folder)
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        do {
            try FileManager.default.moveItem(at: temporary, to: destination)
        } catch {
            try FileManager.default.copyItem(at: temporary, to: destination)
            try? FileManager.default.removeItem(at: temporary)
        }
        AudioPackFiles.excludeFromBackup(destination)
        let size = (try? destination.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size > 0 else { throw AudioPackDownloadError.emptyFile(destination.lastPathComponent) }
    }

    private func note(written: Int64, expected: Int64, index: Int, total: Int) {
        let trackFraction: Double
        if expected > 0 {
            trackFraction = min(1, Double(written) / Double(expected))
            detail = "Track \(index + 1) of \(total) · \(Self.bytes(written)) of \(Self.bytes(expected))"
        } else {
            trackFraction = written > 0 ? 0.5 : 0
            detail = "Track \(index + 1) of \(total) · \(Self.bytes(written))"
        }
        fraction = (Double(index) + trackFraction) / Double(max(total, 1))
    }

    private func store(_ pack: AudioPack, downloaded: Bool, progress: Float) {
        var updated = pack
        updated.downloaded = downloaded
        updated.downloadProgress = progress
        DatabaseService.shared.updateAudioPack(updated)
    }

    private static func bytes(_ value: Int64) -> String {
        if value < 1024 { return "\(value) B" }
        if value < 1024 * 1024 { return String(format: "%.0f KB", Double(value) / 1024) }
        return String(format: "%.1f MB", Double(value) / (1024 * 1024))
    }
}

private enum AudioPackDownloadError: Error {
    case noTracks
    case badURL(String)
    case badStatus(Int, String)
    case notAudio(String)
    case emptyFile(String)
    case incomplete
    case cancelled

    static func describe(_ error: Error) -> String {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .dataNotAllowed:
                return "You're offline. Nothing was saved."
            case .cancelled:
                return "Download cancelled. Nothing was saved."
            case .timedOut:
                return "The download timed out. Nothing was saved."
            default:
                return "Download failed (\(urlError.localizedDescription)). Nothing was saved."
            }
        }
        if let packError = error as? AudioPackDownloadError {
            switch packError {
            case .noTracks:
                return "This pack has no recordings to download."
            case .badURL(let name):
                return "\(name) has no HTTPS address. Nothing was saved."
            case .badStatus(let code, let host):
                return "\(host) returned \(code). Nothing was saved."
            case .notAudio(let host):
                return "\(host) did not return an audio file. Nothing was saved."
            case .emptyFile(let name):
                return "\(name) was empty. Nothing was saved."
            case .incomplete:
                return "Not every recording was saved. Nothing was kept."
            case .cancelled:
                return "Download cancelled. Nothing was saved."
            }
        }
        return "Download failed. Nothing was saved."
    }
}

enum AudioPackFiles {
    static func existingFile(forRemoteURL remote: String) -> URL? {
        for pack in DatabaseService.shared.audioPacks where pack.downloaded {
            for track in pack.tracks where track.remoteUrl == remote {
                guard let url = try? fileURL(packId: pack.id, fileName: track.fileName, create: false) else { continue }
                if FileManager.default.fileExists(atPath: url.path) {
                    return url
                }
            }
        }
        return nil
    }

    static func allTracksExist(_ pack: AudioPack) -> Bool {
        guard !pack.tracks.isEmpty else { return false }
        return pack.tracks.allSatisfy { track in
            guard let url = try? fileURL(packId: pack.id, fileName: track.fileName, create: false) else { return false }
            return FileManager.default.fileExists(atPath: url.path)
        }
    }

    static func deletePack(_ packId: String) {
        guard let folder = try? packFolder(packId, create: false) else { return }
        try? FileManager.default.removeItem(at: folder)
    }

    static func fileURL(packId: String, fileName: String, create: Bool = true) throws -> URL {
        let safeName = sanitized(fileName)
        guard !safeName.isEmpty else { throw AudioPackDownloadError.badURL(fileName) }
        return try packFolder(packId, create: create).appendingPathComponent(safeName)
    }

    static func excludeFromBackup(_ url: URL) {
        var copy = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? copy.setResourceValues(values)
    }

    private static func packFolder(_ packId: String, create: Bool) throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let root = support.appendingPathComponent("AudioPacks", isDirectory: true)
        if create || FileManager.default.fileExists(atPath: root.path) {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            excludeFromBackup(root)
        }
        let safeId = sanitized(packId)
        guard !safeId.isEmpty else { throw AudioPackDownloadError.badURL(packId) }
        let folder = root.appendingPathComponent(safeId, isDirectory: true)
        if create {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            excludeFromBackup(folder)
        }
        return folder
    }

    private static func sanitized(_ name: String) -> String {
        let base = (name as NSString).lastPathComponent
        if base == "." || base == ".." { return "" }
        return base
    }
}

private final class TrackByteProgress: NSObject, URLSessionDownloadDelegate {
    private let onBytes: @Sendable (Int64, Int64) -> Void

    init(onBytes: @escaping @Sendable (Int64, Int64) -> Void) {
        self.onBytes = onBytes
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        onBytes(totalBytesWritten, totalBytesExpectedToWrite)
    }
}
