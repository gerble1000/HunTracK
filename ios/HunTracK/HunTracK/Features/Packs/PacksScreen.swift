import StoreKit
import SwiftUI

/// StoreKit products are not created in App Store Connect by this build.
/// Add one non-consumable per country at £1.00, using the same ids as Android:
/// hunttrack_pack_gb, hunttrack_pack_ie, hunttrack_pack_fr, hunttrack_pack_de,
/// hunttrack_pack_ee, hunttrack_pack_lv, hunttrack_pack_lt, hunttrack_pack_pl, hunttrack_pack_ro,
/// hunttrack_pack_es, hunttrack_pack_pt, hunttrack_pack_it.
/// The UK pack is included with the app until that product exists.
@MainActor
public final class PackPurchaseStore: ObservableObject {
    public static let shared = PackPurchaseStore()
    @Published public var owned: Set<String> = []
    @Published public var prices: [String: String] = [:]
    @Published public var message: String?
    private var products: [String: Product] = [:]

    public func isUnlocked(_ pack: SeasonPack) -> Bool {
        pack.bundledWithApp || owned.contains(pack.productId)
    }

    public func priceLabel(_ pack: SeasonPack) -> String {
        prices[pack.productId] ?? "\(pack.intendedPriceLabel) intended"
    }

    public func refresh() async {
        let ids = RegionPackCatalog.file.seasonPacks.map(\.productId)
        do {
            let loaded = try await Product.products(for: ids)
            products = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
            prices = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0.displayPrice) })
            var purchased = owned
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result {
                    purchased.insert(transaction.productID)
                }
            }
            owned = purchased
            if loaded.isEmpty {
                message = "No App Store products are live yet. Create the hunttrack_pack_* products at £1.00. The UK pack stays included."
            }
        } catch {
            message = "StoreKit could not reach the App Store. Nothing was charged. The UK pack stays included."
        }
    }

    public func purchase(_ pack: SeasonPack) async {
        if isUnlocked(pack) {
            message = "\(pack.countryName) is already available."
            return
        }
        guard let product = products[pack.productId] else {
            message = "Store product ID \(pack.productId) is not live. Create a £1.00 non-consumable in App Store Connect. Nothing was charged."
            return
        }
        do {
            let result = try await product.purchase()
            if case .success(let verification) = result, case .verified(let transaction) = verification {
                owned.insert(transaction.productID)
                await transaction.finish()
                message = "Purchase recorded on this device."
            } else {
                message = "Purchase was not completed."
            }
        } catch {
            message = "Purchase did not start."
        }
    }

    public func restore() async {
        do {
            try await AppStore.sync()
        } catch {
            message = "Restore could not reach the App Store."
        }
        await refresh()
    }
}

public struct PacksScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @StateObject private var store = PackPurchaseStore.shared
    @State private var selectedId: String = RegionPackCatalog.ukPackId

    public init() {}

    public var body: some View {
        let selected = RegionPackCatalog.pack(selectedId) ?? RegionPackCatalog.file.seasonPacks.first
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Packs")
                        .font(.field(size: 32, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                    Text("One pack per country, intended price £1.00. Map areas are about 15 square miles. On iPhone the outline is saved; Apple Maps does not bulk-download tiles.")
                        .font(.field(size: 14))
                        .foregroundColor(theme.palette.mist)
                    countryList
                    if let selected {
                        detail(selected)
                    } else {
                        Text("Pack catalog did not load.")
                            .foregroundColor(theme.palette.mist)
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle("Packs")
        .navigationBarTitleDisplayMode(.inline)
        .task { await store.refresh() }
    }

    private var countryList: some View {
        GlassPanel {
            SectionLabel("Countries")
            ForEach(RegionPackCatalog.file.seasonPacks) { pack in
                Button(action: { selectedId = pack.id }) {
                    HStack {
                        Text(pack.countryName)
                            .foregroundColor(theme.palette.cream)
                        Spacer()
                        Text(store.isUnlocked(pack) ? (pack.bundledWithApp ? "Included" : "Purchased") : store.priceLabel(pack))
                            .foregroundColor(theme.palette.bronzeBright)
                    }
                }
            }
            Button("Restore purchases") { Task { await store.restore() } }
                .foregroundColor(theme.palette.bronzeBright)
            if let message = store.message {
                Text(message)
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.bronzeBright)
            }
        }
    }

    @ViewBuilder
    private func detail(_ pack: SeasonPack) -> some View {
        let open = store.isUnlocked(pack)
        GlassPanel {
            Text(pack.title)
                .font(.field(size: 20, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text(pack.summary)
                .font(.field(size: 13))
                .foregroundColor(theme.palette.mist)
            Text(pack.disclaimer)
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
            Text("Product ID \(pack.productId) · \(store.priceLabel(pack))")
                .font(.field(size: 12, weight: .semibold))
                .foregroundColor(theme.palette.bronzeBright)
            if !open {
                Button("Buy \(pack.countryName)") { Task { await store.purchase(pack) } }
                    .foregroundColor(theme.palette.bronzeBright)
                Text("Species seasons stay locked until the App Store acknowledges this product. The product is not in the store yet, so this will not charge you.")
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.mist)
            }
        }
        GlassPanel {
            SectionLabel("Hunting rules")
            ForEach(pack.rules) { rule in
                VStack(alignment: .leading, spacing: 2) {
                    Text(rule.title)
                        .foregroundColor(theme.palette.cream)
                    Text(rule.answer.shortLabel)
                        .foregroundColor(theme.palette.bronzeBright)
                    Text(rule.notes)
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    Text(rule.sourceTitle + (rule.lastVerified.map { " · read \($0)" } ?? ""))
                        .font(.field(size: 11))
                        .foregroundColor(theme.palette.bronze)
                }
                .padding(.top, 6)
            }
        }
        if open {
            GlassPanel {
                SectionLabel("Map areas")
                Text("About 15 square miles. iOS saves the outline only.")
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.mist)
                ForEach(RegionPackCatalog.regions(for: pack.countryCode)) { region in
                    let saved = db.offlineRegions.contains { $0.name == region.name }
                    VStack(alignment: .leading) {
                        Text(region.name).foregroundColor(theme.palette.cream)
                        Text(region.summary).font(.field(size: 12)).foregroundColor(theme.palette.mist)
                        Button(saved ? "Saved" : "Save map outline") {
                            guard !saved else { return }
                            let bounds = region.bounds()
                            db.createNamedPackRegion(
                                name: region.name,
                                north: bounds.north,
                                south: bounds.south,
                                east: bounds.east,
                                west: bounds.west
                            )
                        }
                        .foregroundColor(theme.palette.bronzeBright)
                    }
                }
            }
            let cited = pack.species.filter { $0.quality != .placeholder }
            let slots = pack.species.filter { $0.quality == .placeholder }
            if !cited.isEmpty {
                SectionLabel("Cited seasons")
                ForEach(cited.prefix(40)) { row in
                    speciesRow(row)
                }
            }
            SectionLabel("Unverified species slots")
            ForEach(slots.prefix(12)) { row in
                speciesRow(row)
            }
            if slots.count > 12 {
                Text("\(slots.count - 12) more unverified slots are in this pack. None of them store a season.")
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.mist)
            }
        }
    }

    private func speciesRow(_ row: PackSpeciesSeason) -> some View {
        GlassPanel(compact: true) {
            Text([row.commonName, row.sexLabel, row.regionLabel].filter { !$0.isEmpty }.joined(separator: " · "))
                .foregroundColor(theme.palette.cream)
            if !row.scientificName.isEmpty {
                Text(row.scientificName).font(.field(size: 12)).foregroundColor(theme.palette.mist)
            }
            Text(row.statusLabel).foregroundColor(theme.palette.bronzeBright)
            Text(row.seasonText).foregroundColor(theme.palette.cream)
            if !row.notes.isEmpty {
                Text(row.notes).font(.field(size: 12)).foregroundColor(theme.palette.mist)
            }
        }
    }
}
