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
    private var updates: Task<Void, Never>?

    /// Paid country packs. The UK product id is queried too, but the UK pack stays unlocked without it.
    public static let paidProductIds: [String] = [
        "hunttrack_pack_ie",
        "hunttrack_pack_fr",
        "hunttrack_pack_de",
        "hunttrack_pack_ee",
        "hunttrack_pack_lv",
        "hunttrack_pack_lt",
        "hunttrack_pack_pl",
        "hunttrack_pack_ro",
        "hunttrack_pack_es",
        "hunttrack_pack_pt",
        "hunttrack_pack_it",
    ]

    public func isUnlocked(_ pack: SeasonPack) -> Bool {
        pack.bundledWithApp || owned.contains(pack.productId)
    }

    public func priceLabel(_ pack: SeasonPack) -> String {
        prices[pack.productId] ?? "\(pack.intendedPriceLabel) intended"
    }

    public func hasStoreProduct(_ pack: SeasonPack) -> Bool {
        products[pack.productId] != nil
    }

    public func refresh() async {
        startListening()
        let ids = RegionPackCatalog.file.seasonPacks.map(\.productId)
        do {
            let loaded = try await Product.products(for: ids)
            products = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
            prices = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0.displayPrice) })
            owned = await currentOwnedProductIds()
            let catalogIds = Set(ids)
            let missingPaid = Self.paidProductIds.filter { !catalogIds.contains($0) }
            if !missingPaid.isEmpty {
                message = "Pack catalog is missing \(missingPaid.joined(separator: ", "))."
            } else if loaded.isEmpty {
                message = "No App Store products are live yet. Create the hunttrack_pack_* products at £1.00. The UK pack stays included."
            }
        } catch {
            owned = await currentOwnedProductIds()
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
            switch result {
            case .success(let verification):
                guard case .verified(let transaction) = verification else {
                    message = "Purchase could not be verified. Nothing was charged."
                    return
                }
                owned.insert(transaction.productID)
                await transaction.finish()
                message = "Purchase recorded on this device."
            case .userCancelled:
                message = "Purchase cancelled."
            case .pending:
                message = "Purchase is pending approval. This pack stays locked until the App Store finishes it."
            @unknown default:
                message = "Purchase was not completed."
            }
        } catch {
            message = "Purchase did not start."
        }
    }

    public func restore() async {
        startListening()
        do {
            try await AppStore.sync()
        } catch {
            message = "Restore could not reach the App Store."
            return
        }
        await refresh()
        if products.isEmpty {
            message = "No App Store products are live yet. Create the hunttrack_pack_* products at £1.00. The UK pack stays included."
        } else {
            message = "Restored purchases from the App Store. The UK pack stays included."
        }
    }

    private func startListening() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.handle(result)
            }
        }
    }

    private func handle(_ result: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = result else {
            message = "A purchase could not be verified."
            return
        }
        owned.insert(transaction.productID)
        await transaction.finish()
    }

    private func currentOwnedProductIds() async -> Set<String> {
        var purchased: Set<String> = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result {
                purchased.insert(transaction.productID)
            }
        }
        for await result in Transaction.unfinished {
            if case .verified(let transaction) = result {
                purchased.insert(transaction.productID)
                await transaction.finish()
            }
        }
        return purchased
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
                    Text("Country hunting packs: seasons, species and rules. The UK pack is included. Ireland, France, Germany, Estonia, Latvia, Lithuania, Poland, Romania, Spain, Portugal and Italy are £1.00 purchases. Restore brings those purchases back on this Apple ID.")
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
                Text(store.hasStoreProduct(pack)
                     ? "Buying unlocks this country's seasons on this Apple ID."
                     : "Species seasons stay locked until the App Store acknowledges \(pack.productId). The product is not in the store yet, so this will not charge you.")
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
                ForEach(cited) { row in
                    speciesRow(row)
                }
            }
            if !slots.isEmpty {
                SectionLabel("Unverified species slots")
                ForEach(slots) { row in
                    speciesRow(row)
                }
                Text("None of these unverified slots store a season.")
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
