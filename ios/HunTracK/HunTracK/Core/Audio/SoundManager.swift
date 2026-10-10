import AVFoundation
import UIKit

public enum HuntSound {
    case click
    case gpsLock
    case markerPlace
    case success
    case arrival
    case appLoaded
}

public class SoundManager: ObservableObject {
    public static let shared = SoundManager()
    
    @Published public var soundsEnabled: Bool = true
    @Published public var vibrationEnabled: Bool = true
    
    private var chirpPlayer: AVAudioPlayer?
    private var trillPlayer: AVAudioPlayer?
    private var roarPlayer: AVAudioPlayer?
    
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()
    
    private init() {
        configureAudioSession()
        loadSounds()
    }
    
    private func configureAudioSession() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }
    
    private func loadSounds() {
        chirpPlayer = loadPlayer(named: "sfx_bird_chirp")
        trillPlayer = loadPlayer(named: "sfx_bird_trill")
        roarPlayer = loadPlayer(named: "sfx_stag_roar")
    }
    
    private func loadPlayer(named name: String) -> AVAudioPlayer? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "mp3") else { return nil }
        let player = try? AVAudioPlayer(contentsOf: url)
        player?.prepareToPlay()
        return player
    }
    
    public func play(_ sound: HuntSound) {
        triggerHaptic(sound)
        guard soundsEnabled else { return }
        
        switch sound {
        case .click, .markerPlace:
            chirpPlayer?.currentTime = 0
            chirpPlayer?.volume = 0.7
            chirpPlayer?.play()
        case .gpsLock, .success:
            trillPlayer?.currentTime = 0
            trillPlayer?.volume = 0.7
            trillPlayer?.play()
        case .arrival:
            trillPlayer?.currentTime = 0
            trillPlayer?.volume = 0.95
            trillPlayer?.play()
        case .appLoaded:
            roarPlayer?.currentTime = 0
            roarPlayer?.volume = 0.85
            roarPlayer?.play()
        }
    }
    
    private func triggerHaptic(_ sound: HuntSound) {
        guard vibrationEnabled else { return }
        switch sound {
        case .click, .markerPlace:
            lightImpact.impactOccurred()
        case .gpsLock, .success:
            mediumImpact.impactOccurred()
        case .arrival:
            notificationFeedback.notificationOccurred(.warning)
        case .appLoaded:
            heavyImpact.impactOccurred()
        }
    }
}
