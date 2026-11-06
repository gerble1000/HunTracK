import SwiftUI

public struct PermissionsScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared

    /// Non-nil while the permission editor sheet is open. `id == 0` means "new".
    @State private var editorDraft: HuntingPermission?
    @State private var linkTarget: HuntingPermission?
    @State private var showingBoundarySheet = false
    @Environment(\.dismiss) private var dismiss
    private let captureOnCurrentMap: Bool

    public init(captureOnCurrentMap: Bool = false) {
        self.captureOnCurrentMap = captureOnCurrentMap
    }

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    walletSection
                    boundariesSection
                    captureSection
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editorDraft) { draft in
            PermissionEditorSheet(permission: draft, boundaries: db.boundaries) { saved in
                if saved.id == 0 {
                    db.addPermission(saved)
                    if let stored = db.permissions.last, stored.boundaryAreaId == nil {
                        // Present after the editor sheet finishes dismissing.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                            linkTarget = stored
                        }
                    }
                } else {
                    db.updatePermission(saved)
                }
            }
        }
        .sheet(item: $linkTarget) { permission in
            PermissionBoundaryLinkSheet(permission: permission)
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Permissions Wallet")
                .font(.field(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
                .padding(.top, 16)

            Text("Store your landowner permission records, shoot boundaries, and contact verification.")
                .font(.field(size: 14))
                .foregroundColor(theme.palette.mist)
        }
    }

    // MARK: - Wallet

    private var walletSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel("Written permissions")

            if db.permissions.isEmpty {
                GlassPanel {
                    Text("No permissions stored yet.")
                        .font(.field(size: 14))
                        .foregroundColor(theme.palette.mist)
                    Text("Add a landowner's written permission to keep the details and any linked boundary to hand in the field.")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
            } else {
                ForEach(db.permissions) { permission in
                    PermissionCard(
                        permission: permission,
                        boundary: db.boundaries.first { $0.id == permission.boundaryAreaId },
                        onEdit: { editorDraft = permission },
                        onLink: { linkTarget = permission },
                        onShowMap: {
                            db.focusMap(on: [permission.id])
                            NotificationCenter.default.post(name: .openMapTab, object: nil)
                        },
                        onDelete: { db.deletePermission(id: permission.id) }
                    )
                }
            }

            HuntButton("Add permission", icon: "plus") {
                editorDraft = HuntingPermission(
                    label: "",
                    farmName: "",
                    permissionType: "Written permission",
                    species: "",
                    validFromEpochMs: PermissionFormatting.todayEpochMs(),
                    validToEpochMs: PermissionFormatting.oneYearFromTodayEpochMs(),
                    ownerName: "",
                    ownerContact: "",
                    notes: "",
                    docType: .note,
                    attachmentUri: nil,
                    boundaryAreaId: nil
                )
            }
        }
    }

    // MARK: - Boundaries

    private var boundariesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel("Boundary areas")

            if db.boundaries.isEmpty {
                GlassPanel {
                    Text("No boundary areas yet.")
                        .font(.field(size: 14))
                        .foregroundColor(theme.palette.mist)
                    Text("Record your permitted ground, no-go zones and emergency safe zones, then view them overlaid on the Map.")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
            } else {
                ForEach(db.boundaries) { boundary in
                    BoundaryCard(
                        boundary: boundary,
                        permissions: db.permissions,
                        onShowMap: {
                            db.focusMap(onBoundary: boundary.id)
                            NotificationCenter.default.post(name: .openMapTab, object: nil)
                        },
                        onDelete: { db.deleteBoundary(id: boundary.id) }
                    )
                }
            }
        }
    }

    // MARK: - Boundary capture hand-off

    private var captureSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel("Record a new boundary")

            ForEach(BoundaryZoneType.allCases, id: \.self) { zone in
                GlassPanel(compact: true) {
                    HStack(spacing: 10) {
                        Circle()
                            .fill(Color(hex: zone.mapColorHex))
                            .frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(zone.displayName)
                                .font(.field(size: 15, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(zone.summary)
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }
                    }
                }
            }

            HuntButton("Start boundary capture", icon: "map") {
                showingBoundarySheet = true
            }

            if db.pendingBoundary != nil {
                Text("Boundary capture ready — finish it on the Map tab.")
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.success)
            }
        }
        .sheet(isPresented: $showingBoundarySheet, onDismiss: {
            // When opened from Map's menu, uncover the map after the setup
            // sheet closes; selecting the already-active Map tab cannot pop us.
            if captureOnCurrentMap && db.pendingBoundary != nil { dismiss() }
        }) {
            PermissionBoundarySheet(
                permissions: db.permissions
            ) { pending in
                db.pendingBoundary = pending
                SoundManager.shared.play(.success)
            }
        }
    }
}

// MARK: - Permission card

private struct PermissionCard: View {
    let permission: HuntingPermission
    let boundary: BoundaryArea?
    let onEdit: () -> Void
    let onLink: () -> Void
    let onShowMap: () -> Void
    let onDelete: () -> Void

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared

    var body: some View {
        GlassPanel {
            HStack(alignment: .top, spacing: 8) {
                Text(permission.displayLabel)
                    .font(.field(size: 20, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                Spacer()
                if permission.isExpired {
                    Text("EXPIRED")
                        .font(.field(size: 11, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(theme.palette.danger)
                        )
                }
            }

            // Grouped to stay within ViewBuilder's 10-child limit.
            Group {
                if !permission.farmName.isEmpty && permission.farmName != permission.displayLabel {
                    MetricRow("Farm", permission.farmName)
                }
                MetricRow("Type", permission.permissionType)
                MetricRow("Landowner", permission.ownerName.isEmpty ? "—" : permission.ownerName)
                MetricRow("Contact", permission.ownerContact.isEmpty ? "—" : permission.ownerContact)
                MetricRow("Quarry", permission.species.isEmpty ? "—" : permission.species)
                MetricRow("Valid from", PermissionFormatting.date(permission.validFromEpochMs))
                MetricRow("Valid to", PermissionFormatting.date(permission.validToEpochMs))
                MetricRow("Document", permission.docType.label)
            }
            if let boundary = boundary {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Linked boundary")
                        .font(.field(size: 12, weight: .semibold))
                        .foregroundColor(theme.palette.mist)
                    Text(boundary.name.isEmpty ? boundary.zoneType.displayName : boundary.name)
                        .font(.field(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text("\(boundary.zoneType.displayName) · \(PermissionFormatting.area(boundary.areaSqMeters))")
                        .font(.field(size: 12))
                        .foregroundColor(Color(hex: boundary.zoneType.mapColorHex))
                }
                .padding(.top, 4)
                Button {
                    db.setPermissionShownOnMap(id: permission.id, shown: !permission.showOnMap)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: permission.showOnMap ? "checkmark.square.fill" : "square")
                            .foregroundColor(theme.palette.bronzeBright)
                        Text("Show on map")
                            .font(.field(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Spacer()
                    }
                }
                .buttonStyle(PlainButtonStyle())
            } else {
                MetricRow("Boundary", "Not linked")
            }

            if !permission.notes.isEmpty {
                Text(permission.notes)
                    .font(.field(size: 13))
                    .foregroundColor(theme.palette.mist)
            }

            Text(
                permission.isExpired
                    ? "Permission has lapsed — do not shoot on this ground."
                    : "Permission details are stored on this device. Confirm your written agreement and boundaries before shooting."
            )
            .font(.field(size: 12))
            .foregroundColor(permission.isExpired ? theme.palette.danger : theme.palette.success)
            .padding(.top, 2)

            HStack(spacing: 12) {
                Button(boundary == nil ? "Link boundary" : "Change link") { onLink() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)

                Button("Show on map") { onShowMap() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(boundary == nil ? theme.palette.mist : theme.palette.bronzeBright)
                    .disabled(boundary == nil)

                Button("Edit") { onEdit() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
            }
            .padding(.top, 6)

            HStack {
                Button("Delete") { onDelete() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.danger)
                Spacer()
            }
        }
    }
}

// MARK: - Boundary card

private struct BoundaryCard: View {
    let boundary: BoundaryArea
    let permissions: [HuntingPermission]
    let onShowMap: () -> Void
    let onDelete: () -> Void

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared

    private var linked: [HuntingPermission] {
        permissions.filter { $0.boundaryAreaId == boundary.id }
    }

    var body: some View {
        GlassPanel {
            HStack(spacing: 8) {
                Circle()
                    .fill(Color(hex: boundary.zoneType.mapColorHex))
                    .frame(width: 10, height: 10)
                Text(boundary.name.isEmpty ? boundary.zoneType.displayName : boundary.name)
                    .font(.field(size: 17, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                Spacer()
            }

            MetricRow("Zone", boundary.zoneType.displayName)
            MetricRow("Points", String(boundary.points.count))
            MetricRow("Area", PermissionFormatting.area(boundary.areaSqMeters))
            MetricRow("Perimeter", PermissionFormatting.perimeter(DatabaseService.perimeterMeters(boundary.points)))
            MetricRow("Permission", linked.isEmpty ? "Not linked" : linked.map { $0.displayLabel }.joined(separator: ", "))

            if !boundary.notes.isEmpty {
                Text(boundary.notes)
                    .font(.field(size: 13))
                    .foregroundColor(theme.palette.mist)
            }

            HStack(spacing: 12) {
                Menu {
                    if permissions.isEmpty {
                        Button("Add a permission first") {}
                    } else {
                        ForEach(permissions) { permission in
                            let isLinked = permission.boundaryAreaId == boundary.id
                            Button {
                                db.linkPermission(id: permission.id, toBoundary: isLinked ? nil : boundary.id)
                            } label: {
                                Text(isLinked ? "Unlink \(permission.displayLabel)" : "Link \(permission.displayLabel)")
                            }
                        }
                    }
                } label: {
                    Text(linked.isEmpty ? "Link permission" : "Change link")
                        .font(.field(size: 14, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                }

                Button("Show on map") { onShowMap() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)

                Button("Delete") { onDelete() }
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.danger)
            }
            .padding(.top, 6)
        }
    }
}

// MARK: - Permission editor

private struct PermissionEditorSheet: View {
    @State private var draft: HuntingPermission
    let boundaries: [BoundaryArea]
    let onSave: (HuntingPermission) -> Void

    @ObservedObject private var theme = ThemeManager.shared
    @Environment(\.dismiss) private var dismiss

    init(permission: HuntingPermission, boundaries: [BoundaryArea], onSave: @escaping (HuntingPermission) -> Void) {
        _draft = State(initialValue: permission)
        self.boundaries = boundaries
        self.onSave = onSave
    }

    private var isNew: Bool { draft.id == 0 }

    var body: some View {
        NavigationView {
            ScreenBackground {
                Form {
                    Section(header: Text("Permission").foregroundColor(theme.palette.bronze)) {
                        TextField("Short label (e.g. Bob's farm)", text: $draft.label)
                        TextField("Farm / estate name", text: $draft.farmName)
                        TextField("Permission type", text: $draft.permissionType)
                        TextField("Quarry / species permitted", text: $draft.species)
                    }

                    Section(header: Text("Validity").foregroundColor(theme.palette.bronze)) {
                        DatePicker(
                            "Valid from",
                            selection: validFromBinding,
                            displayedComponents: .date
                        )
                        DatePicker(
                            "Valid to",
                            selection: validToBinding,
                            displayedComponents: .date
                        )
                        if draft.validToEpochMs < draft.validFromEpochMs {
                            Text("End date is before the start date.")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.danger)
                        }
                    }

                    Section(header: Text("Linked boundary").foregroundColor(theme.palette.bronze)) {
                        Button {
                            draft.boundaryAreaId = nil
                        } label: {
                            HStack {
                                Text("None")
                                Spacer()
                                if draft.boundaryAreaId == nil {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                        if boundaries.isEmpty {
                            Text("Save this permission, then link an existing area or draw one on the map.")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }
                        ForEach(boundaries) { boundary in
                            Button {
                                draft.boundaryAreaId = boundary.id
                            } label: {
                                HStack {
                                    Text(boundary.name.isEmpty ? boundary.zoneType.displayName : boundary.name)
                                    Spacer()
                                    if draft.boundaryAreaId == boundary.id {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    }

                    Section(header: Text("Landowner").foregroundColor(theme.palette.bronze)) {
                        TextField("Owner name", text: $draft.ownerName)
                        TextField("Owner contact", text: $draft.ownerContact)
                            .keyboardType(.phonePad)
                    }

                    Section(header: Text("Record").foregroundColor(theme.palette.bronze)) {
                        Picker("Document type", selection: $draft.docType) {
                            ForEach(PermissionDocType.allCases, id: \.self) { kind in
                                Text(kind.label).tag(kind)
                            }
                        }
                        TextField("Notes", text: $draft.notes)
                    }

                    Section {
                        Button(isNew ? "Save permission" : "Update permission") {
                            onSave(draft)
                            dismiss()
                        }
                        .foregroundColor(theme.palette.bronze)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle(isNew ? "New permission" : "Edit permission")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(theme.palette.mist)
                }
            }
        }
    }

    private var validFromBinding: Binding<Date> {
        Binding(
            get: { Date(timeIntervalSince1970: Double(draft.validFromEpochMs) / 1000) },
            set: { draft.validFromEpochMs = Int64($0.timeIntervalSince1970 * 1000) }
        )
    }

    private var validToBinding: Binding<Date> {
        Binding(
            get: { Date(timeIntervalSince1970: Double(draft.validToEpochMs) / 1000) },
            set: { draft.validToEpochMs = Int64($0.timeIntervalSince1970 * 1000) }
        )
    }
}

// MARK: - Boundary capture setup

struct PermissionBoundarySheet: View {
    let permissions: [HuntingPermission]
    let onStart: (PendingBoundaryCreation) -> Void
    let returnToPermissions: Bool

    init(permissions: [HuntingPermission], initialMode: BoundaryCaptureMode = .walk, returnToPermissions: Bool = true, onStart: @escaping (PendingBoundaryCreation) -> Void) {
        self.permissions = permissions
        self.onStart = onStart
        self.returnToPermissions = returnToPermissions
        self._mode = State(initialValue: initialMode)
    }

    @State private var name: String = ""
    @State private var mode: BoundaryCaptureMode
    @State private var zoneType: BoundaryZoneType = .hunting
    @State private var linkedPermissionId: Int64?

    @ObservedObject private var theme = ThemeManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScreenBackground {
                Form {
                    Section(header: Text("Boundary").foregroundColor(theme.palette.bronze)) {
                        TextField("Name (e.g. North paddock)", text: $name)

                        Picker("Zone type", selection: $zoneType) {
                            ForEach(BoundaryZoneType.allCases, id: \.self) { zone in
                                Text(zone.displayName).tag(zone)
                            }
                        }
                    }

                    Section(header: Text("What this zone means").foregroundColor(theme.palette.bronze)) {
                        Text(zoneType.summary)
                            .font(.field(size: 13))
                            .foregroundColor(theme.palette.mist)
                    }

                    Section(header: Text("Link to a permission").foregroundColor(theme.palette.bronze)) {
                        Text("Choose one of your saved permissions. This list is not a map layer.")
                            .font(.field(size: 12))
                            .foregroundColor(theme.palette.mist)
                        Button {
                            linkedPermissionId = nil
                        } label: {
                            HStack {
                                Text("None")
                                Spacer()
                                if linkedPermissionId == nil { Image(systemName: "checkmark") }
                            }
                        }
                        if permissions.isEmpty {
                            Text("No permissions saved yet. Add one in the wallet, then it will appear here.")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }
                        ForEach(permissions) { permission in
                            Button {
                                linkedPermissionId = permission.id
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(permission.displayLabel)
                                        if !permission.farmName.isEmpty && permission.farmName != permission.displayLabel {
                                            Text(permission.farmName)
                                                .font(.field(size: 12))
                                                .foregroundColor(theme.palette.mist)
                                        }
                                    }
                                    Spacer()
                                    if linkedPermissionId == permission.id { Image(systemName: "checkmark") }
                                }
                            }
                        }
                        if let id = linkedPermissionId,
                           permissions.first(where: { $0.id == id })?.boundaryAreaId != nil {
                            Text("Saving this boundary replaces that permission's current boundary link.")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }
                    }

                    Section {
                        Button("Draw fence on map") { start(.draw) }
                            .foregroundColor(theme.palette.bronze)
                        Button("Walk fence on map") { start(.walk) }
                            .foregroundColor(theme.palette.bronze)
                        Text("Suggested: \(mode.label). Draw traces the fence with your finger. Walk records it from GPS.")
                            .font(.field(size: 12))
                            .foregroundColor(theme.palette.mist)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("New boundary")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(theme.palette.mist)
                }
            }
        }
    }

    private func start(_ captureMode: BoundaryCaptureMode) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        onStart(
            PendingBoundaryCreation(
                mode: captureMode,
                name: trimmed.isEmpty ? zoneType.displayName : trimmed,
                zoneType: zoneType,
                returnToPermissions: returnToPermissions,
                permissionId: linkedPermissionId
            )
        )
        dismiss()
    }
}

// MARK: - Link an existing permission to a boundary

private struct PermissionBoundaryLinkSheet: View {
    let permission: HuntingPermission

    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var theme = ThemeManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var boundaryName: String = ""
    @State private var zoneType: BoundaryZoneType = .hunting

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Link hunting area")
                            .font(.field(size: 26, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("Tie \(permission.displayLabel) to a saved boundary, or create one. The link is kept on this device and shown on both the permission and the boundary.")
                            .font(.field(size: 14))
                            .foregroundColor(theme.palette.mist)

                        Button("Skip for now") { dismiss() }
                            .font(.field(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)

                        SectionLabel("Choose existing")
                        if db.boundaries.isEmpty {
                            Text("No saved boundaries yet — create one below.")
                                .font(.field(size: 13))
                                .foregroundColor(theme.palette.mist)
                        } else {
                            ForEach(db.boundaries) { boundary in
                                Button {
                                    db.linkPermission(id: permission.id, toBoundary: boundary.id)
                                    dismiss()
                                } label: {
                                    GlassPanel(compact: true) {
                                        HStack {
                                            Circle()
                                                .fill(Color(hex: boundary.zoneType.mapColorHex))
                                                .frame(width: 10, height: 10)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(boundary.name.isEmpty ? boundary.zoneType.displayName : boundary.name)
                                                    .font(.field(size: 15, weight: .semibold))
                                                    .foregroundColor(theme.palette.cream)
                                                Text("\(boundary.zoneType.displayName) · \(PermissionFormatting.area(boundary.areaSqMeters))")
                                                    .font(.field(size: 12))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                            Spacer()
                                            if permission.boundaryAreaId == boundary.id {
                                                Image(systemName: "checkmark")
                                                    .foregroundColor(theme.palette.bronzeBright)
                                            }
                                        }
                                    }
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }

                        SectionLabel("Create new boundary")
                        TextField("Boundary name", text: $boundaryName)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                        Picker("Zone type", selection: $zoneType) {
                            ForEach(BoundaryZoneType.allCases, id: \.self) { zone in
                                Text(zone.displayName).tag(zone)
                            }
                        }
                        .pickerStyle(.segmented)

                        Button("Draw fence on map") { startCapture(.draw) }
                            .font(.field(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Button("Walk fence on map") { startCapture(.walk) }
                            .font(.field(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Button("Quick 500 m square around me") { createSquare() }
                            .font(.field(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                            .disabled(location.latestFix == nil)
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Link boundary")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") { dismiss() }
                        .foregroundColor(theme.palette.mist)
                }
            }
        }
        .onAppear {
            if boundaryName.isEmpty { boundaryName = permission.displayLabel }
            location.requestPermission()
            location.startUpdating()
        }
    }

    private var resolvedName: String {
        let trimmed = boundaryName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? zoneType.displayName : trimmed
    }

    private func startCapture(_ mode: BoundaryCaptureMode) {
        db.pendingBoundary = PendingBoundaryCreation(
            mode: mode,
            name: resolvedName,
            zoneType: zoneType,
            returnToPermissions: true,
            permissionId: permission.id
        )
        dismiss()
    }

    private func createSquare() {
        guard let fix = location.latestFix else { return }
        let id = db.addSquareBoundary(
            name: resolvedName,
            zoneType: zoneType,
            latitude: fix.latitude,
            longitude: fix.longitude
        )
        db.linkPermission(id: permission.id, toBoundary: id)
        dismiss()
    }
}

// MARK: - Formatting

private enum PermissionFormatting {
    private static var londonCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? TimeZone.current
        return calendar
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM yyyy"
        formatter.timeZone = TimeZone(identifier: "Europe/London") ?? TimeZone.current
        return formatter
    }()

    static func date(_ epochMs: Int64) -> String {
        dateFormatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    static func todayEpochMs() -> Int64 {
        let start = londonCalendar.startOfDay(for: Date())
        return Int64(start.timeIntervalSince1970 * 1000)
    }

    static func oneYearFromTodayEpochMs() -> Int64 {
        let start = londonCalendar.startOfDay(for: Date())
        let nextYear = londonCalendar.date(byAdding: .year, value: 1, to: start) ?? start
        return Int64(nextYear.timeIntervalSince1970 * 1000)
    }

    /// Square metres shown as hectares and acres, mirroring Android's boundary area formatting.
    static func area(_ squareMeters: Double) -> String {
        guard squareMeters > 0 else { return "—" }
        let hectares = squareMeters / 10000.0
        let acres = squareMeters / 4046.8564224
        return String(format: "%.2f ha · %.2f ac", hectares, acres)
    }

    static func perimeter(_ meters: Double) -> String {
        guard meters > 0 else { return "—" }
        if meters >= 1000 {
            return String(format: "%.2f km", meters / 1000.0)
        }
        return String(format: "%.0f m", meters)
    }
}
