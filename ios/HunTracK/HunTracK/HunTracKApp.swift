import SwiftUI

@main
struct HunTracKApp: App {
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var sound = SoundManager.shared
    
    init() {
        SoundManager.shared.play(.appLoaded)
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(theme)
                .environmentObject(sound)
                .preferredColorScheme(.dark)
        }
    }
}

struct ContentView: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var selectedTab: Int = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationView {
                HomeScreen()
                    .navigationBarHidden(true)
            }
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }
            .tag(0)
            
            NavigationView {
                MapScreen()
                    .navigationBarHidden(true)
            }
            .tabItem {
                Label("Map", systemImage: "map.fill")
            }
            .tag(1)
            
            NavigationView {
                WeatherScreen()
                    .navigationBarHidden(true)
            }
            .tabItem {
                Label("Weather", systemImage: "cloud.sun.fill")
            }
            .tag(2)
            
            NavigationView {
                MoreScreen()
                    .navigationBarHidden(true)
            }
            .tabItem {
                Label("More", systemImage: "ellipsis.circle.fill")
            }
            .tag(3)
        }
        .accentColor(theme.palette.bronze)
    }
}
