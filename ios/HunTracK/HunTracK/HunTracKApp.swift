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
    @State private var showQuickAdd: Bool = false
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            TabView(selection: $selectedTab) {
                HuntTabShell(onTitleTap: goHome) {
                    HomeScreen()
                }
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
                .tag(0)
                
                HuntTabShell(onTitleTap: goHome) {
                    MapScreen()
                }
                .tabItem {
                    Label("Map", systemImage: "map.fill")
                }
                .tag(1)
                
                HuntTabShell(onTitleTap: goHome) {
                    WeatherScreen()
                }
                .tabItem {
                    Label("Weather", systemImage: "cloud.sun.fill")
                }
                .tag(2)
                
                HuntTabShell(onTitleTap: goHome) {
                    MoreScreen()
                }
                .tabItem {
                    Label("More", systemImage: "ellipsis.circle.fill")
                }
                .tag(3)
            }
            .accentColor(theme.palette.bronze)
            
            // Android shows this on every bottom tab plus the Map route.
            HuntMarkLocationButton {
                showQuickAdd = true
            }
            .padding(.trailing, 24)
            .padding(.bottom, 96)
        }
        .sheet(isPresented: $showQuickAdd) {
            HuntQuickAddMarkerSheet(isPresented: $showQuickAdd)
        }
    }
    
    private func goHome() {
        selectedTab = 0
    }
}
