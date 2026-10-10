import SwiftUI

public struct SpeciesScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @State private var searchText: String = ""
    @State private var selectedGroup: SpeciesGroup? = nil
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            VStack(alignment: .leading, spacing: 12) {
                Text("UK Species ID")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                
                // Search Field
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(theme.palette.mist)
                    TextField("Search UK quarry & wildlife", text: $searchText)
                        .foregroundColor(theme.palette.cream)
                }
                .padding(10)
                .background(theme.palette.glass)
                .cornerRadius(10)
                .padding(.horizontal, 16)
                
                List(filteredSpecies) { sp in
                    NavigationLink(destination: SpeciesDetailScreen(species: sp)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(sp.commonName)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(sp.scientificName)
                                .font(.system(size: 13, weight: .light))
                                .italic()
                                .foregroundColor(theme.palette.bronze)
                            Text(sp.summary)
                                .font(.system(size: 13))
                                .foregroundColor(theme.palette.mist)
                                .lineLimit(2)
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(theme.palette.glass)
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private var filteredSpecies: [Species] {
        UkFieldCatalog.species.filter {
            if searchText.isEmpty { return true }
            return $0.commonName.localizedCaseInsensitiveContains(searchText) ||
                   $0.scientificName.localizedCaseInsensitiveContains(searchText)
        }
    }
}

public struct SpeciesDetailScreen: View {
    let species: Species
    @ObservedObject private var theme = ThemeManager.shared
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(species.commonName)
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text(species.scientificName)
                            .font(.system(size: 16, weight: .medium))
                            .italic()
                            .foregroundColor(theme.palette.bronze)
                    }
                    .padding(.top, 16)
                    
                    GlassPanel {
                        SectionLabel("Field Overview")
                        Text(species.summary)
                            .foregroundColor(theme.palette.cream)
                    }
                    
                    GlassPanel {
                        SectionLabel("Identification Traits")
                        MetricRow("Male", species.maleTraits)
                        MetricRow("Female", species.femaleTraits)
                        MetricRow("Young", species.youngTraits)
                    }
                    
                    GlassPanel {
                        SectionLabel("Field Signs")
                        MetricRow("Tracks", species.tracks)
                        MetricRow("Droppings", species.droppings)
                    }
                    
                    GlassPanel {
                        SectionLabel("Seasons & Regulations")
                        Text(species.seasonsNote)
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
