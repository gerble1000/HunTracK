import SwiftUI
import AVFoundation

public struct CallsScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var playingCallId: String? = nil
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Animal Calls")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.top, 16)
                    
                    Text("Built-in audio library for species observation and calling.")
                        .font(.system(size: 14))
                        .foregroundColor(theme.palette.mist)
                    
                    CallCard(
                        title: "Red Deer Stag Roar",
                        subtitle: "Autumn rut territorial challenge",
                        sound: .appLoaded
                    )
                    
                    CallCard(
                        title: "Roe Deer Doe Squeak",
                        subtitle: "High pitch mating contact call",
                        sound: .click
                    )
                    
                    CallCard(
                        title: "Mallard Duck Quack",
                        subtitle: "Feeding and landing greeting",
                        sound: .arrival
                    )
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

public struct CallCard: View {
    let title: String
    let subtitle: String
    let sound: HuntSound
    @ObservedObject private var theme = ThemeManager.shared
    @State private var isPlaying = false
    
    public var body: some View {
        GlassPanel {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
                Spacer()
                Button(action: {
                    SoundManager.shared.play(sound)
                    isPlaying = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        isPlaying = false
                    }
                }) {
                    Image(systemName: isPlaying ? "stop.circle.fill" : "play.circle.fill")
                        .font(.system(size: 38))
                        .foregroundColor(theme.palette.bronze)
                }
            }
        }
    }
}
