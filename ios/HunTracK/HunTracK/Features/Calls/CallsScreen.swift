import SwiftUI
import AVFoundation

// MARK: - Streamed call playback

/// Streams the catalog's remote call recordings.
///
/// `SoundManager` is deliberately NOT used here — that is for UI sound effects.
/// Some catalog entries are Ogg Vorbis (Wikimedia), which AVFoundation cannot
/// decode on iOS; those surface as a friendly error rather than a crash.
final class CallAudioPlayer: NSObject, ObservableObject {
    static let shared = CallAudioPlayer()

    @Published private(set) var currentCallId: String?
    @Published var errorMessage: String?
    @Published var loopEnabled: Bool = false

    private var player: AVPlayer?
    private var notifications: [NSObjectProtocol] = []
    private var observations: [NSKeyValueObservation] = []

    private override init() {
        super.init()
    }

    var isPlayingSomething: Bool { currentCallId != nil }

    func isPlaying(_ callId: String) -> Bool { currentCallId == callId }

    /// Play the call, or stop it if it is already the active one.
    func toggle(call: AnimalCall) {
        if currentCallId == call.id {
            stop()
        } else {
            play(call: call)
        }
    }

    func play(call: AnimalCall) {
        errorMessage = nil
        teardown()

        guard let url = URL(string: call.remoteUrl), url.scheme == "https" || url.scheme == "http" else {
            errorMessage = "This call has no playable recording URL."
            return
        }

        activateSession()

        let item = AVPlayerItem(url: url)
        let avPlayer = AVPlayer(playerItem: item)
        avPlayer.actionAtItemEnd = .pause
        player = avPlayer
        currentCallId = call.id

        // Loop or stop when the recording finishes.
        let endToken = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            if self.loopEnabled {
                self.player?.seek(to: .zero)
                self.player?.play()
            } else {
                self.stop()
            }
        }
        notifications.append(endToken)

        let failToken = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] note in
            guard let self = self else { return }
            let error = note.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
            self.fail(with: error?.localizedDescription)
        }
        notifications.append(failToken)

        // Surface load failures (network, 404, or an unsupported codec such as Ogg).
        let statusObservation = item.observe(\.status, options: [.new]) { [weak self] observed, _ in
            guard observed.status == .failed else { return }
            DispatchQueue.main.async {
                self?.fail(with: observed.error?.localizedDescription)
            }
        }
        observations.append(statusObservation)

        avPlayer.play()
    }

    func stop() {
        teardown()
    }

    /// Stop playback and hand the audio session back to the system.
    func release() {
        teardown()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func clearError() {
        errorMessage = nil
    }

    private func fail(with message: String?) {
        errorMessage = message ?? "This recording could not be played on iOS."
        teardown()
    }

    private func teardown() {
        observations.forEach { $0.invalidate() }
        observations.removeAll()
        notifications.forEach { NotificationCenter.default.removeObserver($0) }
        notifications.removeAll()
        player?.pause()
        player = nil
        currentCallId = nil
    }

    private func activateSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)
    }
}

// MARK: - Calls screen

public struct CallsScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var player = CallAudioPlayer.shared

    @State private var logTarget: AnimalCall?

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    if let message = player.errorMessage {
                        errorBanner(message)
                    }
                    library
                    journal
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { player.release() }
        .sheet(item: $logTarget) { call in
            CallLogSheet(call: call)
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Animal calls")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text("Library, playback & journal")
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 16)
    }

    private func errorBanner(_ message: String) -> some View {
        GlassPanel(compact: true) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(theme.palette.danger)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Could not play that call")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text(message)
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)
                }
                Spacer()
                Button("Dismiss") { player.clearError() }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
            }
        }
    }

    // MARK: Library

    private var library: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                SectionLabel("Call library")
                Spacer()
                Toggle("Loop", isOn: $player.loopEnabled)
                    .labelsHidden()
                    .toggleStyle(SwitchToggleStyle(tint: theme.palette.bronze))
                Text("Loop")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.mist)
            }

            if UkFieldCatalog.calls.isEmpty {
                Text("No calls in the catalog.")
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.mist)
            }

            ForEach(categories, id: \.self) { category in
                VStack(alignment: .leading, spacing: 8) {
                    Text(category.uppercased())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(theme.palette.bronze)
                        .tracking(1.2)
                        .padding(.top, 4)

                    ForEach(calls(in: category)) { call in
                        CallRow(
                            call: call,
                            isPlaying: player.isPlaying(call.id),
                            onToggle: { player.toggle(call: call) },
                            onLog: { logTarget = call }
                        )
                    }
                }
            }
        }
    }

    /// Category order follows the catalog so the list is stable between launches.
    private var categories: [String] {
        var seen = Set<String>()
        var ordered: [String] = []
        for call in UkFieldCatalog.calls where !seen.contains(call.category) {
            seen.insert(call.category)
            ordered.append(call.category)
        }
        return ordered
    }

    private func calls(in category: String) -> [AnimalCall] {
        UkFieldCatalog.calls.filter { $0.category == category }
    }

    // MARK: Journal

    private var journal: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel("Call journal")

            if db.callJournal.isEmpty {
                Text("No call journal entries yet.")
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.mist)
            }

            ForEach(orderedJournal) { entry in
                journalRow(entry)
            }
        }
    }

    private var orderedJournal: [CallJournalEntry] {
        db.callJournal.sorted { $0.recordedAtEpochMs > $1.recordedAtEpochMs }
    }

    private func journalRow(_ entry: CallJournalEntry) -> some View {
        let species = (entry.speciesId ?? "").isEmpty
            ? nil
            : UkFieldCatalog.speciesById(entry.speciesId ?? "")?.commonName
        let callName = (entry.callId ?? "").isEmpty
            ? nil
            : UkFieldCatalog.calls.first { $0.id == entry.callId }?.name

        return GlassPanel(compact: true) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(callName ?? species ?? "Call logged")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.cream)

                    if let species = species, callName != nil {
                        Text(species)
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.bronze)
                    }

                    Text(CallFormat.timestamp(entry.recordedAtEpochMs))
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)

                    if !entry.response.isEmpty {
                        Text("Response: \(entry.response)")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.cream)
                    }
                    if let distance = entry.distanceEstimateMeters {
                        Text("Distance ~\(distance) m")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                    }
                    if !entry.notes.isEmpty {
                        Text(entry.notes)
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                    }
                }
                Spacer(minLength: 8)
                Button {
                    SoundManager.shared.play(.click)
                    db.deleteCallJournalEntry(id: entry.id)
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 15))
                        .foregroundColor(theme.palette.danger)
                }
            }
        }
    }
}

// MARK: - Call row

struct CallRow: View {
    let call: AnimalCall
    let isPlaying: Bool
    let onToggle: () -> Void
    let onLog: () -> Void

    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        GlassPanel(compact: true) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(call.name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text(call.description)
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                    if !call.credit.isEmpty {
                        Text(call.credit)
                            .font(.system(size: 11))
                            .foregroundColor(theme.palette.bronze)
                    }
                }

                Spacer(minLength: 8)

                Button(action: {
                    SoundManager.shared.play(.click)
                    onLog()
                }) {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 18))
                        .foregroundColor(theme.palette.bronzeBright)
                }
                .buttonStyle(.plain)

                Button(action: onToggle) {
                    Image(systemName: isPlaying ? "stop.circle.fill" : "play.circle.fill")
                        .font(.system(size: 34))
                        .foregroundColor(theme.palette.bronze)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Log sheet

struct CallLogSheet: View {
    let call: AnimalCall

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var response: String = ""
    @State private var distanceText: String = ""
    @State private var notes: String = ""
    @State private var validationMessage: String?

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassPanel {
                            SectionLabel("Call")
                            Text(call.name)
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            if let species = UkFieldCatalog.speciesById(call.speciesId) {
                                Text(species.commonName)
                                    .font(.system(size: 13))
                                    .foregroundColor(theme.palette.bronze)
                            }
                        }

                        GlassPanel {
                            SectionLabel("Journal entry")
                            TextField("Response heard", text: $response)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            TextField("Distance (m)", text: $distanceText)
                                .keyboardType(.numberPad)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                            TextField("Notes", text: $notes)
                                .textFieldStyle(RoundedBorderTextFieldStyle())

                            if let message = validationMessage {
                                Text(message)
                                    .font(.system(size: 12))
                                    .foregroundColor(theme.palette.danger)
                            }

                            HuntButton("Save entry", icon: "tray.and.arrow.down") {
                                save()
                            }
                            .padding(.top, 6)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Log call")
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(leading: Button("Cancel") { dismiss() })
        }
    }

    private func save() {
        let trimmed = distanceText.trimmingCharacters(in: .whitespacesAndNewlines)
        var distance: Int?
        if !trimmed.isEmpty {
            guard let parsed = Int(trimmed), parsed > 0 else {
                validationMessage = "Distance must be a positive whole number of metres."
                return
            }
            distance = parsed
        }

        let fix = LocationManager.shared.latestFix ?? LocationManager.shared.fallbackFix()

        let entry = CallJournalEntry(
            callId: call.id,
            speciesId: call.speciesId,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            response: response.trimmingCharacters(in: .whitespacesAndNewlines),
            distanceEstimateMeters: distance,
            latitude: fix.latitude,
            longitude: fix.longitude,
            weatherJson: nil,
            recordedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )

        db.addCallJournalEntry(entry)
        SoundManager.shared.play(.success)
        dismiss()
    }
}

// MARK: - Formatting

enum CallFormat {
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM yyyy, HH:mm"
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        return formatter
    }()

    static func timestamp(_ epochMs: Int64) -> String {
        formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }
}
