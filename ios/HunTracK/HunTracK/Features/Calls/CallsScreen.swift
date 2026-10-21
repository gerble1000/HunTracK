import SwiftUI
import AVFoundation

// MARK: - Streamed call playback

final class CallAudioPlayer: NSObject, ObservableObject {
    static let shared = CallAudioPlayer()

    @Published private(set) var currentCallId: String?
    @Published var errorMessage: String?
    @Published var loopEnabled: Bool = false
    @Published var isPlaying: Bool = false

    private var player: AVPlayer?
    private var notifications: [NSObjectProtocol] = []
    private var observations: [NSKeyValueObservation] = []

    private override init() {
        super.init()
    }

    var isPlayingSomething: Bool { currentCallId != nil && isPlaying }

    func isCallPlaying(_ callId: String) -> Bool {
        currentCallId == callId && isPlaying
    }

    func toggle(callId: String, remoteUrl: String) {
        if currentCallId == callId && isPlaying {
            stop()
        } else {
            play(callId: callId, urlString: remoteUrl)
        }
    }

    func play(callId: String, urlString: String) {
        errorMessage = nil
        teardown()

        guard let url = URL(string: urlString), url.scheme == "https" || url.scheme == "http" || url.isFileURL else {
            errorMessage = "This call has no playable recording URL."
            return
        }

        activateSession()

        let item = AVPlayerItem(url: url)
        let avPlayer = AVPlayer(playerItem: item)
        avPlayer.actionAtItemEnd = .pause
        player = avPlayer
        currentCallId = callId
        isPlaying = true

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
        isPlaying = false
    }

    private func activateSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)
    }
}

// MARK: - Calls Tabs

public enum CallsTab: String, CaseIterable {
    case library = "Library"
    case addCall = "Add call"
    case packs = "Packs"
    case journal = "Journal"
}

// MARK: - Calls Screen

public struct CallsScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var player = CallAudioPlayer.shared

    @State private var selectedTab: CallsTab = .library
    @State private var logTarget: AnimalCall?
    @State private var logCustomTarget: UserCall?

    // Add call states
    @State private var customName: String = ""
    @State private var customSpeciesId: String = "red_deer"
    @State private var customNotes: String = ""
    @State private var customUrl: String = ""
    @State private var saveSuccessMessage: String?

    public init() {}

    public var body: some View {
        ScreenBackground {
            VStack(spacing: 0) {
                // Tab selector bar
                HStack(spacing: 8) {
                    ForEach(CallsTab.allCases, id: \.self) { tab in
                        Button(action: {
                            SoundManager.shared.play(.click)
                            selectedTab = tab
                        }) {
                            Text(tab.rawValue)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(selectedTab == tab ? theme.palette.ink : theme.palette.cream)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(selectedTab == tab ? theme.palette.bronze : theme.palette.slate.opacity(0.6))
                                .cornerRadius(8)
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        header
                        if let message = player.errorMessage {
                            errorBanner(message)
                        }

                        switch selectedTab {
                        case .library:
                            libraryView
                        case .addCall:
                            addCallView
                        case .packs:
                            packsView
                        case .journal:
                            journalView
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { player.release() }
        .sheet(item: $logTarget) { call in
            CallLogSheet(call: call)
        }
        .sheet(item: $logCustomTarget) { call in
            CustomCallLogSheet(call: call)
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Animal calls")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text("Play built-in calls or add your own audio files and recordings.")
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 4)
    }

    private func errorBanner(_ message: String) -> some View {
        GlassPanel(compact: true) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(theme.palette.danger)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Audio playback warning")
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

    // MARK: - Library Tab

    private var libraryView: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Quick banner to jump to Add Call
            Button(action: {
                SoundManager.shared.play(.click)
                selectedTab = .addCall
            }) {
                GlassPanel(compact: true) {
                    HStack(spacing: 12) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(theme.palette.bronzeBright)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("+ Add custom call")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(theme.palette.bronzeBright)
                            Text("Upload an audio file or configure custom recordings.")
                                .font(.system(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundColor(theme.palette.mist)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            // Custom Calls if any
            if !db.userCalls.isEmpty {
                SectionLabel("Your custom calls")
                ForEach(db.userCalls) { userCall in
                    GlassPanel(compact: true) {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(userCall.name)
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(theme.palette.cream)
                                if !userCall.notes.isEmpty {
                                    Text(userCall.notes)
                                        .font(.system(size: 12))
                                        .foregroundColor(theme.palette.mist)
                                }
                                Text("Custom · \(userCall.category)")
                                    .font(.system(size: 11))
                                    .foregroundColor(theme.palette.bronze)
                            }
                            Spacer()

                            Button(action: {
                                SoundManager.shared.play(.click)
                                logCustomTarget = userCall
                            }) {
                                Image(systemName: "square.and.pencil")
                                    .font(.system(size: 18))
                                    .foregroundColor(theme.palette.bronzeBright)
                            }
                            .buttonStyle(PlainButtonStyle())

                            if !userCall.filePath.isEmpty {
                                Button(action: {
                                    player.toggle(callId: "custom_\(userCall.id)", remoteUrl: userCall.filePath)
                                }) {
                                    Image(systemName: player.isCallPlaying("custom_\(userCall.id)") ? "stop.circle.fill" : "play.circle.fill")
                                        .font(.system(size: 32))
                                        .foregroundColor(theme.palette.bronze)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }

                            Button(action: {
                                SoundManager.shared.play(.click)
                                db.deleteUserCall(id: userCall.id)
                            }) {
                                Image(systemName: "trash")
                                    .font(.system(size: 16))
                                    .foregroundColor(theme.palette.danger)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
            }

            // Built-in library
            HStack {
                SectionLabel("Built-in library")
                Spacer()
                Toggle("Loop", isOn: $player.loopEnabled)
                    .labelsHidden()
                    .toggleStyle(SwitchToggleStyle(tint: theme.palette.bronze))
                Text("Loop")
                    .font(.system(size: 12, weight: .semibold))
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
                            isPlaying: player.isCallPlaying(call.id),
                            onToggle: { player.toggle(callId: call.id, remoteUrl: call.remoteUrl) },
                            onLog: { logTarget = call }
                        )
                    }
                }
            }
        }
    }

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

    // MARK: - Add Call Tab

    private var addCallView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Add a custom call — configure audio URL or field recording.")
                .font(.system(size: 13))
                .foregroundColor(theme.palette.mist)

            GlassPanel {
                VStack(alignment: .leading, spacing: 12) {
                    SectionLabel("Call details")

                    TextField("Call name (e.g. Sika Stag Stalker Whistle)", text: $customName)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    Text("Species")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(theme.palette.cream)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(UkFieldCatalog.species) { sp in
                                Button(action: {
                                    customSpeciesId = sp.id
                                }) {
                                    Text(sp.commonName)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(customSpeciesId == sp.id ? theme.palette.ink : theme.palette.cream)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(customSpeciesId == sp.id ? theme.palette.bronze : theme.palette.slate)
                                        .cornerRadius(6)
                                }
                            }
                        }
                    }

                    TextField("Audio URL / File Path (mp3 / m4a / wav)", text: $customUrl)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    TextField("Notes / when to use", text: $customNotes)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    if let msg = saveSuccessMessage {
                        Text(msg)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.success)
                    }

                    HuntButton("Save custom call", icon: "tray.and.arrow.down") {
                        saveCustomCall()
                    }
                    .padding(.top, 4)
                }
            }
        }
    }

    private func saveCustomCall() {
        guard !customName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let call = UserCall(
            name: customName.trimmingCharacters(in: .whitespaces),
            speciesId: customSpeciesId,
            category: "Custom",
            description: customNotes,
            filePath: customUrl.trimmingCharacters(in: .whitespaces),
            notes: customNotes
        )
        db.addUserCall(call)
        SoundManager.shared.play(.success)
        saveSuccessMessage = "Custom call saved to your library!"
        customName = ""
        customNotes = ""
        customUrl = ""
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            saveSuccessMessage = nil
        }
    }

    // MARK: - Packs Tab

    private var packsView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Real wildlife audio recordings for offline field practice.")
                .font(.system(size: 13))
                .foregroundColor(theme.palette.mist)

            ForEach(db.audioPacks) { pack in
                GlassPanel {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(pack.name)
                                    .font(.system(size: 17, weight: .bold))
                                    .foregroundColor(theme.palette.cream)
                                Text(pack.description)
                                    .font(.system(size: 13))
                                    .foregroundColor(theme.palette.mist)
                            }
                            Spacer()
                            if pack.downloaded {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(theme.palette.success)
                            }
                        }

                        Text("\(pack.recordingCount) recordings · ~\(pack.sizeBytes / 1024) KB")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)

                        if pack.downloaded {
                            Text("Downloaded · Ready offline")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(theme.palette.success)
                                .padding(.top, 4)
                        } else {
                            HuntButton("Download audio pack", icon: "arrow.down.circle") {
                                var updated = pack
                                updated.downloaded = true
                                updated.downloadProgress = 1.0
                                db.updateAudioPack(updated)
                                SoundManager.shared.play(.success)
                            }
                            .padding(.top, 4)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Journal Tab

    private var journalView: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel("Call journal")

            if db.callJournal.isEmpty {
                GlassPanel(compact: true) {
                    Text("No call journal entries yet. Tap the pencil icon on any call to record responses, distance, and field notes.")
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
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

// MARK: - Custom Call Log Sheet

struct CustomCallLogSheet: View {
    let call: UserCall

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var response: String = ""
    @State private var distanceText: String = ""
    @State private var notes: String = ""

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassPanel {
                            SectionLabel("Custom Call")
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

                            HuntButton("Save entry", icon: "tray.and.arrow.down") {
                                let fix = LocationManager.shared.latestFix ?? LocationManager.shared.fallbackFix()
                                let entry = CallJournalEntry(
                                    callId: "custom_\(call.id)",
                                    speciesId: call.speciesId,
                                    notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
                                    response: response.trimmingCharacters(in: .whitespacesAndNewlines),
                                    distanceEstimateMeters: Int(distanceText),
                                    latitude: fix.latitude,
                                    longitude: fix.longitude,
                                    weatherJson: nil,
                                    recordedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
                                )
                                db.addCallJournalEntry(entry)
                                SoundManager.shared.play(.success)
                                dismiss()
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
