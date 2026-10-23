import SwiftUI

// MARK: - View mode

public enum CalendarViewMode: String, CaseIterable {
    case day = "DAY"
    case month = "MONTH"
    case year = "YEAR"

    public var label: String {
        switch self {
        case .day: return "Day"
        case .month: return "Month"
        case .year: return "Year"
        }
    }
}

// MARK: - Date helpers

/// Europe/London date maths, mirroring Android's `CalendarDates` (Monday-first grid).
private enum FieldCalendarDates {

    static var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/London") ?? TimeZone(secondsFromGMT: 0)!
        cal.locale = Locale(identifier: "en_GB")
        return cal
    }

    static func today() -> Date {
        calendar.startOfDay(for: Date())
    }

    /// Epoch millis at 00:00 Europe/London for the given day.
    static func dayStart(for date: Date) -> Int64 {
        Int64(calendar.startOfDay(for: date).timeIntervalSince1970 * 1000)
    }

    static func date(fromDayStart epochMs: Int64) -> Date {
        Date(timeIntervalSince1970: Double(epochMs) / 1000)
    }

    static func isSameDay(_ a: Date, _ b: Date) -> Bool {
        calendar.isDate(a, inSameDayAs: b)
    }

    static func dayNumber(_ date: Date) -> Int {
        calendar.component(.day, from: date)
    }

    static func year(_ date: Date) -> Int {
        calendar.component(.year, from: date)
    }

    static func month(_ date: Date) -> Int {
        calendar.component(.month, from: date)
    }

    /// Monday-first month grid, padded with nils to a whole number of weeks.
    static func monthGrid(year: Int, month: Int) -> [Date?] {
        let cal = calendar
        guard let firstOfMonth = cal.date(from: DateComponents(year: year, month: month, day: 1)),
              let dayRange = cal.range(of: .day, in: .month, for: firstOfMonth) else {
            return []
        }

        // Calendar.weekday is 1=Sunday…7=Saturday; shift so Monday == 0.
        let weekday = cal.component(.weekday, from: firstOfMonth)
        let leading = (weekday + 5) % 7

        var cells: [Date?] = Array(repeating: nil, count: leading)
        for day in dayRange {
            if let date = cal.date(from: DateComponents(year: year, month: month, day: day)) {
                cells.append(date)
            }
        }
        while cells.count % 7 != 0 {
            cells.append(nil)
        }
        return cells
    }

    static func periodLabel(_ mode: CalendarViewMode, _ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")

        switch mode {
        case .day:
            formatter.dateFormat = "EEEE d MMMM yyyy"
        case .month:
            formatter.dateFormat = "MMMM yyyy"
        case .year:
            formatter.dateFormat = "yyyy"
        }
        return formatter.string(from: date)
    }

    static func monthTitle(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: date)
    }

    static func shortMonthName(_ year: Int, _ month: Int) -> String {
        let symbols = calendar.shortMonthSymbols
        guard month >= 1, month <= symbols.count else { return "" }
        return symbols[month - 1].uppercased()
    }

    /// Days in the month that carry a saved entry.
    static func entryCount(year: Int, month: Int, entries: [CalendarDayEntry]) -> Int {
        entries.filter { entry in
            let dayStart = date(fromDayStart: entry.dayStartEpochMs)
            return FieldCalendarDates.year(dayStart) == year
                && FieldCalendarDates.month(dayStart) == month
                && entry.hasContent
        }.count
    }
}

// MARK: - Screen

public struct CalendarScreen: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    @State private var viewMode: CalendarViewMode = .month
    @State private var selectedDate: Date = FieldCalendarDates.today()
    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var draftLocation: String = ""
    @State private var draftSpeciesId: String? = nil

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    viewModePicker
                    navBar
                    content
                }
                .padding(16)
            }
        }
        .navigationTitle("Calendar")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadDraft(for: selectedDate) }
    }

    // MARK: Header & chrome

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Field calendar")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.palette.cream)
            Text("Plan hunts, log outings, and note what happened each day")
                .font(.system(size: 13))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 8)
    }

    private var viewModePicker: some View {
        Picker("View", selection: $viewMode) {
            ForEach(CalendarViewMode.allCases, id: \.self) { mode in
                Text(mode.label).tag(mode)
            }
        }
        .pickerStyle(.segmented)
    }

    private var navBar: some View {
        GlassPanel {
            HStack {
                Button {
                    stepPeriod(forward: false)
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                        .frame(width: 44, height: 32)
                }
                .buttonStyle(.plain)

                Spacer()

                VStack(spacing: 2) {
                    Text(FieldCalendarDates.periodLabel(viewMode, selectedDate))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                        .multilineTextAlignment(.center)
                    if isViewingToday {
                        // Only label the bar "Today" when the visible period really is today.
                        Text("Today")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                    } else {
                        Button {
                            goToToday()
                        } label: {
                            Text("Back to today")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                                .underline()
                        }
                        .buttonStyle(.plain)
                    }
                }

                Spacer()

                Button {
                    stepPeriod(forward: true)
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                        .frame(width: 44, height: 32)
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewMode {
        case .day:
            dayPanel
        case .month:
            monthPanel
        case .year:
            yearPanel
        }
    }

    // MARK: Month view

    private var monthPanel: some View {
        let year = FieldCalendarDates.year(selectedDate)
        let month = FieldCalendarDates.month(selectedDate)
        let cells = FieldCalendarDates.monthGrid(year: year, month: month)
        let weekCount = cells.count / 7
        let today = FieldCalendarDates.today()

        return GlassPanel {
            SectionLabel(FieldCalendarDates.monthTitle(selectedDate))

            HStack(spacing: 0) {
                ForEach(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"], id: \.self) { label in
                    Text(label)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(theme.palette.mist)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 4)

            ForEach(Array(0..<weekCount), id: \.self) { week in
                HStack(spacing: 2) {
                    ForEach(Array(0..<7), id: \.self) { offset in
                        CalendarScreenDayCell(
                            date: cellDate(cells, week, offset),
                            isSelected: cellIsSelected(cells, week, offset),
                            isToday: cellIsToday(cells, week, offset, today),
                            hasEntry: cellHasEntry(cells, week, offset)
                        ) {
                            selectDay(cells, week, offset)
                        }
                    }
                }
            }

            Text("Tap a day to open details")
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)
                .padding(.top, 8)
        }
    }

    private func cellDate(_ cells: [Date?], _ week: Int, _ offset: Int) -> Date? {
        let index = week * 7 + offset
        guard index < cells.count else { return nil }
        return cells[index]
    }

    private func cellIsSelected(_ cells: [Date?], _ week: Int, _ offset: Int) -> Bool {
        guard let date = cellDate(cells, week, offset) else { return false }
        return FieldCalendarDates.isSameDay(date, selectedDate)
    }

    private func cellIsToday(_ cells: [Date?], _ week: Int, _ offset: Int, _ today: Date) -> Bool {
        guard let date = cellDate(cells, week, offset) else { return false }
        return FieldCalendarDates.isSameDay(date, today)
    }

    private func cellHasEntry(_ cells: [Date?], _ week: Int, _ offset: Int) -> Bool {
        guard let date = cellDate(cells, week, offset) else { return false }
        return db.entry(forDayStart: FieldCalendarDates.dayStart(for: date))?.hasContent == true
    }

    private func selectDay(_ cells: [Date?], _ week: Int, _ offset: Int) {
        guard let date = cellDate(cells, week, offset) else { return }
        SoundManager.shared.play(.click)
        selectedDate = date
        loadDraft(for: date)
        viewMode = .day
    }

    // MARK: Year view

    private var yearPanel: some View {
        let year = FieldCalendarDates.year(selectedDate)
        let months = Array(stride(from: 0, to: 12, by: 3))
        let currentYear = FieldCalendarDates.year(FieldCalendarDates.today())
        let currentMonth = FieldCalendarDates.month(FieldCalendarDates.today())

        return GlassPanel {
            SectionLabel("\(year)")

            ForEach(months, id: \.self) { start in
                HStack(spacing: 8) {
                    ForEach(Array(start..<(start + 3)), id: \.self) { monthIndex in
                        CalendarScreenYearMonthCell(
                            title: FieldCalendarDates.shortMonthName(year, monthIndex + 1),
                            entryCount: FieldCalendarDates.entryCount(
                                year: year,
                                month: monthIndex + 1,
                                entries: db.calendarEntries
                            ),
                            isCurrent: year == currentYear && (monthIndex + 1) == currentMonth
                        ) {
                            selectMonth(year: year, month: monthIndex + 1)
                        }
                    }
                }
                .padding(.bottom, 8)
            }

            Text("Tap a month to open the grid")
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)
        }
    }

    private func selectMonth(year: Int, month: Int) {
        SoundManager.shared.play(.click)
        if let date = FieldCalendarDates.calendar.date(from: DateComponents(year: year, month: month, day: 1)) {
            selectedDate = date
        }
        loadDraft(for: selectedDate)
        viewMode = .month
    }

    // MARK: Day view

    private var dayPanel: some View {
        let dayStart = FieldCalendarDates.dayStart(for: selectedDate)
        let saved = db.entry(forDayStart: dayStart)

        return GlassPanel {
            SectionLabel("Day details")
            Text(FieldCalendarDates.periodLabel(.day, selectedDate))
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(theme.palette.cream)

            if saved?.hasContent == true {
                Text("Last saved notes on this day")
                    .font(.system(size: 12))
                    .foregroundColor(theme.palette.mist)
            }

            TextField("Title", text: $draftTitle)
                .textFieldStyle(.roundedBorder)

            TextField("Location / beat", text: $draftLocation)
                .textFieldStyle(.roundedBorder)

            SectionLabel("Species (optional)")
            Picker("Species", selection: $draftSpeciesId) {
                Text("None").tag(Optional<String>.none)
                ForEach(UkFieldCatalog.cullLogSpecies) { species in
                    Text(species.commonName).tag(Optional(species.id))
                }
            }
            .pickerStyle(.menu)

            TextField("Notes", text: $draftNotes, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(4...10)

            HStack(spacing: 8) {
                HuntButton("Save day", icon: "checkmark") {
                    saveDay()
                }
                Button {
                    clearDay()
                } label: {
                    Text("Clear")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.mist)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: Actions

    private var isViewingToday: Bool {
        FieldCalendarDates.isSameDay(selectedDate, FieldCalendarDates.today())
    }

    private func loadDraft(for date: Date) {
        let start = FieldCalendarDates.dayStart(for: date)
        guard let entry = db.entry(forDayStart: start) else {
            draftTitle = ""
            draftNotes = ""
            draftLocation = ""
            draftSpeciesId = nil
            return
        }
        draftTitle = entry.title
        draftNotes = entry.notes
        draftLocation = entry.location
        draftSpeciesId = entry.speciesId
    }

    private func saveDay() {
        let start = FieldCalendarDates.dayStart(for: selectedDate)
        let trimmedSpecies = (draftSpeciesId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        var entry = db.entry(forDayStart: start) ?? CalendarDayEntry(dayStartEpochMs: start)
        entry.title = draftTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        entry.notes = draftNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        entry.location = draftLocation.trimmingCharacters(in: .whitespacesAndNewlines)
        entry.speciesId = trimmedSpecies.isEmpty ? nil : trimmedSpecies

        // An entry with no content is removed by the store, matching Android.
        db.upsertCalendarEntry(entry)
        loadDraft(for: selectedDate)
    }

    private func clearDay() {
        let start = FieldCalendarDates.dayStart(for: selectedDate)
        if let existing = db.entry(forDayStart: start) {
            db.deleteCalendarEntry(id: existing.id)
        }
        loadDraft(for: selectedDate)
    }

    private func goToToday() {
        selectedDate = FieldCalendarDates.today()
        loadDraft(for: selectedDate)
    }

    private func stepPeriod(forward: Bool) {
        let cal = FieldCalendarDates.calendar
        let step = forward ? 1 : -1
        let unit: Calendar.Component

        switch viewMode {
        case .day:
            unit = .day
        case .month:
            unit = .month
        case .year:
            unit = .year
        }

        if let next = cal.date(byAdding: unit, value: step, to: selectedDate) {
            selectedDate = next
            loadDraft(for: next)
        }
    }
}

// MARK: - Cells

private struct CalendarScreenDayCell: View {
    let date: Date?
    let isSelected: Bool
    let isToday: Bool
    let hasEntry: Bool
    let action: () -> Void

    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        Group {
            if let date = date {
                Button(action: action) {
                    VStack(spacing: 2) {
                        Text("\(FieldCalendarDates.dayNumber(date))")
                            .font(.system(size: 13, weight: isToday ? .bold : .regular))
                            .foregroundColor(isSelected ? theme.palette.cream : theme.palette.mist)
                        Circle()
                            .fill(hasEntry ? theme.palette.bronzeBright : Color.clear)
                            .frame(width: 5, height: 5)
                    }
                    .frame(maxWidth: .infinity, minHeight: 38)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(isSelected ? theme.palette.bronze.opacity(0.35) : Color.clear)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(borderColor, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            } else {
                Color.clear
                    .frame(maxWidth: .infinity, minHeight: 38)
            }
        }
    }

    private var borderColor: Color {
        if isSelected { return theme.palette.bronzeBright }
        if isToday { return theme.palette.bronzeBright.opacity(0.6) }
        return Color.clear
    }
}

private struct CalendarScreenYearMonthCell: View {
    let title: String
    let entryCount: Int
    let isCurrent: Bool
    let action: () -> Void

    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(entryCount > 0 ? theme.palette.bronzeBright : theme.palette.mist)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 62)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(theme.palette.charcoal.opacity(0.55))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isCurrent ? theme.palette.bronzeBright : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var subtitle: String {
        if entryCount <= 0 { return "No notes" }
        return entryCount == 1 ? "1 day noted" : "\(entryCount) days noted"
    }
}
