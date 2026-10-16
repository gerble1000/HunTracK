import SwiftUI
import LocalAuthentication
import UIKit

/// Public In Case of Emergency (ICE) profile.
///
/// The read-only ICE panel is always visible so a first responder can act without
/// unlocking anything. Editing requires the PIN, or biometrics when enabled.
/// Everything is persisted through `DatabaseService`, so edits survive relaunch.
public struct EmergencyScreen: View {
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared

    @State private var isUnlocked = false
    @State private var draft = EmergencyProfile()
    @State private var statusMessage: String?

    @State private var activeSheet: EmergencySheet?
    @State private var pinPurpose: PinPurpose = .unlock
    @State private var pinInput = ""
    @State private var pinError: String?

    @State private var contactEditorIndex: Int?
    @State private var contactName = ""
    @State private var contactPhone = ""
    @State private var contactRelationship = ""

    public init() {}

    // MARK: - Sheet routing

    private enum PinPurpose: String {
        case unlock
        case setPin
    }

    private enum EmergencySheet: Identifiable {
        case pin(PinPurpose)
        case contact

        var id: String {
            switch self {
            case .pin(let purpose): return "pin-" + purpose.rawValue
            case .contact: return "contact"
            }
        }
    }

    // MARK: - Body

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    headerView

                    if isUnlocked {
                        editorView
                    } else {
                        publicIceView
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .pin(let purpose):
                pinSheet(purpose)
            case .contact:
                contactSheet
            }
        }
    }

    // MARK: - Header

    private var headerView: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("EMERGENCY")
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(theme.palette.danger)

            Text(
                isUnlocked
                    ? "Editing unlocked - lock the profile when you are finished."
                    : "Public display - edit locked"
            )
            .font(.system(size: 14))
            .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 8)
    }

    // MARK: - Public ICE display (read-only)

    private var publicIceView: some View {
        VStack(alignment: .leading, spacing: 14) {
            if db.emergency.hasContent {
                GlassPanel {
                    SectionLabel("Medical profile")
                    if !db.emergency.fullName.isEmpty {
                        Text(db.emergency.fullName)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                    }
                    if !db.emergency.bloodType.isEmpty {
                        MetricRow("Blood type", db.emergency.bloodType)
                    }
                    if !db.emergency.allergies.isEmpty {
                        MetricRow("Allergies", db.emergency.allergies)
                    }
                    if !db.emergency.medicalInfo.isEmpty {
                        detailRow("Medical information", db.emergency.medicalInfo)
                    }
                    if !db.emergency.medication.isEmpty {
                        detailRow("Medication", db.emergency.medication)
                    }
                    if !db.emergency.notes.isEmpty {
                        detailRow("Notes", db.emergency.notes)
                    }
                }

                contactsPanel(db.emergency.contacts, readOnly: true)
            } else {
                GlassPanel {
                    SectionLabel("No profile yet")
                    Text("Add your medical details and emergency contacts so a first responder can help you quickly.")
                        .font(.system(size: 14))
                        .foregroundColor(theme.palette.mist)
                }
            }

            HuntButton(
                DatabaseService.hasPin(db.emergency.pinHash)
                    ? "Unlock to Edit"
                    : "Add / Edit Details",
                icon: DatabaseService.hasPin(db.emergency.pinHash) ? "lock.open" : "square.and.pencil"
            ) {
                beginUnlock()
            }
            .padding(.top, 4)

            if !DatabaseService.hasPin(db.emergency.pinHash) {
                Text("Tip: unlock and set a PIN to stop others editing this profile.")
                    .font(.system(size: 12))
                    .foregroundColor(theme.palette.mist)
            }

            if let statusMessage = statusMessage {
                Text(statusMessage)
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.success)
            }
        }
    }

    /// Long-form text block - `MetricRow` would cramp multi-line medical notes.
    private func detailRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(theme.palette.mist)
            Text(value)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(theme.palette.cream)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 2)
    }

    // MARK: - Contacts

    private func contactsPanel(_ contacts: [EmergencyContact], readOnly: Bool) -> some View {
        GlassPanel {
            SectionLabel("Emergency contacts")

            if contacts.isEmpty {
                Text("No contacts stored yet.")
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.mist)
            } else {
                ForEach(contacts.enumerated().map { IndexedContact(id: $0.offset, contact: $0.element) }) { item in
                    contactRow(item.contact, index: item.id, readOnly: readOnly)
                }
            }

            if !readOnly {
                Button(action: { openContactEditor(index: nil) }) {
                    HStack(spacing: 6) {
                        Image(systemName: "plus.circle")
                        Text("Add contact")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundColor(theme.palette.bronzeBright)
                }
                .padding(.top, 4)
            }
        }
    }

    private func contactRow(_ contact: EmergencyContact, index: Int, readOnly: Bool) -> some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                Text(contact.phone)
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.bronzeBright)
                if !contact.relationship.isEmpty {
                    Text(contact.relationship)
                        .font(.system(size: 12))
                        .foregroundColor(theme.palette.mist)
                }
            }

            Spacer()

            Button(action: { call(contact.phone) }) {
                Image(systemName: "phone.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(theme.palette.success)
                    .padding(8)
                    .background(
                        Circle().fill(theme.palette.success.opacity(0.18))
                    )
            }

            if !readOnly {
                Button(action: { openContactEditor(index: index) }) {
                    Image(systemName: "pencil")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(theme.palette.bronze)
                        .padding(8)
                }
                Button(action: { deleteContact(at: index) }) {
                    Image(systemName: "trash")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(theme.palette.danger)
                        .padding(8)
                }
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Editor

    private var editorView: some View {
        VStack(alignment: .leading, spacing: 14) {
            GlassPanel {
                SectionLabel("Medical profile")
                fieldRow("Full name", text: $draft.fullName)
                fieldRow("Blood type", text: $draft.bloodType)
                fieldRow("Allergies", text: $draft.allergies)
                fieldRow("Medical information", text: $draft.medicalInfo)
                fieldRow("Medication", text: $draft.medication)
                fieldRow("Notes", text: $draft.notes)
            }

            contactsPanel(draft.contacts, readOnly: false)

            securityPanel

            VStack(spacing: 10) {
                HuntButton("Save Profile", icon: "checkmark.circle") {
                    save()
                }
                Button(action: { lock() }) {
                    Text("Lock Profile")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.bronze)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
            }

            if let statusMessage = statusMessage {
                Text(statusMessage)
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.success)
            }
        }
    }

    private var securityPanel: some View {
        GlassPanel {
            SectionLabel("Security")
            MetricRow(
                "PIN",
                DatabaseService.hasPin(draft.pinHash) ? "Set" : "Not set"
            )
            MetricRow(
                "Biometric unlock",
                draft.biometricEnabled ? "On" : "Off"
            )

            Toggle(isOn: $draft.biometricEnabled) {
                Text("Use Face ID / Touch ID")
                    .font(.system(size: 14))
                    .foregroundColor(theme.palette.cream)
            }
            .toggleStyle(SwitchToggleStyle(tint: theme.palette.bronze))
            .padding(.top, 4)

            HStack(spacing: 16) {
                Button(action: {
                    pinInput = ""
                    pinError = nil
                    pinPurpose = .setPin
                    activeSheet = .pin(.setPin)
                }) {
                    Text(DatabaseService.hasPin(draft.pinHash) ? "Change PIN" : "Set PIN")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                }

                if DatabaseService.hasPin(draft.pinHash) {
                    Button(action: { removePin() }) {
                        Text("Remove PIN")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.danger)
                    }
                }
            }
            .padding(.top, 6)
        }
    }

    private func fieldRow(_ label: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(theme.palette.mist)
            TextField(label, text: text)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .foregroundColor(theme.palette.cream)
                .autocapitalization(.words)
        }
    }

    // MARK: - PIN sheet

    private func pinSheet(_ purpose: PinPurpose) -> some View {
        ScreenBackground {
            VStack(alignment: .leading, spacing: 16) {
                Text(pinSheetTitle(purpose))
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(theme.palette.cream)

                Text(
                    purpose == .unlock
                        ? "Enter your PIN to edit the emergency profile."
                        : "Choose a PIN of at least 4 digits. It is stored only as a hash."
                )
                .font(.system(size: 13))
                .foregroundColor(theme.palette.mist)

                SecureField("PIN", text: $pinInput)
                    .keyboardType(.numberPad)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .foregroundColor(theme.palette.cream)

                if let pinError = pinError {
                    Text(pinError)
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.danger)
                }

                VStack(spacing: 10) {
                    HuntButton("Confirm", icon: "checkmark") {
                        submitPin(purpose)
                    }
                    Button(action: {
                        pinInput = ""
                        pinError = nil
                        activeSheet = nil
                    }) {
                        Text("Cancel")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronze)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                }

                Spacer()
            }
            .padding(20)
        }
    }

    private func pinSheetTitle(_ purpose: PinPurpose) -> String {
        if purpose == .unlock {
            return "Enter PIN"
        }
        return DatabaseService.hasPin(draft.pinHash) ? "Change PIN" : "Set PIN"
    }

    // MARK: - Contact sheet

    private var contactSheet: some View {
        ScreenBackground {
            VStack(alignment: .leading, spacing: 16) {
                Text(contactEditorIndex == nil ? "Add contact" : "Edit contact")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(theme.palette.cream)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Name")
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                    TextField("Name", text: $contactName)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Phone")
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                    TextField("Phone", text: $contactPhone)
                        .keyboardType(.phonePad)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Relationship")
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                    TextField("e.g. Partner, GP", text: $contactRelationship)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                VStack(spacing: 10) {
                    HuntButton("Save Contact", icon: "checkmark") {
                        commitContact()
                    }
                    Button(action: { activeSheet = nil }) {
                        Text("Cancel")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(theme.palette.bronze)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                }

                Spacer()
            }
            .padding(20)
        }
    }

    // MARK: - Unlock

    private func beginUnlock() {
        statusMessage = nil

        guard DatabaseService.hasPin(db.emergency.pinHash) else {
            // No PIN has ever been set, so there is nothing to protect yet.
            enterEditor()
            return
        }

        if db.emergency.biometricEnabled {
            attemptBiometricUnlock()
        } else {
            pinInput = ""
            pinError = nil
            pinPurpose = .unlock
            activeSheet = .pin(.unlock)
        }
    }

    private func attemptBiometricUnlock() {
        let context = LAContext()
        var authError: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            // No biometrics enrolled or available - fall back to the PIN rather than
            // unlocking, so the profile stays protected.
            pinInput = ""
            pinError = nil
            pinPurpose = .unlock
            activeSheet = .pin(.unlock)
            return
        }

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Unlock the emergency profile to edit it"
        ) { success, _ in
            DispatchQueue.main.async {
                if success {
                    enterEditor()
                } else {
                    pinInput = ""
                    pinError = "Face ID / Touch ID did not match - enter your PIN."
                    pinPurpose = .unlock
                    activeSheet = .pin(.unlock)
                }
            }
        }
    }

    private func submitPin(_ purpose: PinPurpose) {
        switch purpose {
        case .unlock:
            if DatabaseService.verifyPin(pinInput, hash: db.emergency.pinHash) {
                pinInput = ""
                pinError = nil
                activeSheet = nil
                enterEditor()
            } else {
                pinError = "Incorrect PIN."
                pinInput = ""
            }

        case .setPin:
            guard pinInput.count >= 4 else {
                pinError = "PIN must be at least 4 digits."
                return
            }
            draft.pinHash = DatabaseService.hashPin(pinInput)
            pinInput = ""
            pinError = nil
            activeSheet = nil
            // Persist immediately so the new lock is effective straight away.
            db.saveEmergencyProfile(draft)
            statusMessage = "PIN saved."
            SoundManager.shared.play(.success)
        }
    }

    private func enterEditor() {
        draft = db.emergency
        isUnlocked = true
        activeSheet = nil
        statusMessage = nil
        SoundManager.shared.play(.success)
    }

    private func lock() {
        draft = db.emergency
        isUnlocked = false
        pinInput = ""
        pinError = nil
        statusMessage = nil
    }

    // MARK: - Save / security actions

    private func save() {
        db.saveEmergencyProfile(draft)
        SoundManager.shared.play(.success)
        statusMessage = "Profile saved."
    }

    private func removePin() {
        draft.pinHash = nil
        draft.biometricEnabled = false
        db.saveEmergencyProfile(draft)
        statusMessage = "PIN removed."
    }

    // MARK: - Contact CRUD

    private func openContactEditor(index: Int?) {
        contactEditorIndex = index

        if let index = index, db.emergency.contacts.indices.contains(index) {
            let contact = db.emergency.contacts[index]
            contactName = contact.name
            contactPhone = contact.phone
            contactRelationship = contact.relationship
        } else {
            contactEditorIndex = nil
            contactName = ""
            contactPhone = ""
            contactRelationship = ""
        }

        activeSheet = .contact
    }

    private func commitContact() {
        let name = contactName.trimmingCharacters(in: .whitespacesAndNewlines)
        let phone = contactPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let relationship = contactRelationship.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !name.isEmpty, !phone.isEmpty else { return }

        let contact = EmergencyContact(name: name, phone: phone, relationship: relationship)

        if let index = contactEditorIndex, draft.contacts.indices.contains(index) {
            draft.contacts[index] = contact
        } else {
            draft.contacts.append(contact)
        }

        contactEditorIndex = nil
        contactName = ""
        contactPhone = ""
        contactRelationship = ""
        activeSheet = nil
        statusMessage = "Contact updated - remember to Save the profile."
    }

    private func deleteContact(at index: Int) {
        guard draft.contacts.indices.contains(index) else { return }
        draft.contacts.remove(at: index)
        statusMessage = "Contact removed - remember to Save the profile."
    }

    // MARK: - Phone

    private func call(_ phone: String) {
        let digits = phone.filter { $0.isNumber || $0 == "+" }
        guard !digits.isEmpty, let url = URL(string: "tel://" + digits) else { return }
        UIApplication.shared.open(url)
    }
}

/// Pairs a contact with its position so `ForEach` can iterate without a key path.
private struct IndexedContact: Identifiable {
    let id: Int
    let contact: EmergencyContact
}
