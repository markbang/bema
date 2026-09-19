import SharedLogic
import UIKit

/// The panel a sideways drag on the timeline reveals: the month's memos as a shaded
/// grid with month navigation, then the tag cloud. The Android activity panel draws
/// the same two halves from the same `GetUserStats` response, and the calendar
/// arithmetic behind the grid lives in sharedLogic.
final class ActivityPanelView: UIView {
    /// Called with a tag the user tapped, already prefixed with `#`.
    var onTagTap: ((String) -> Void)?
    /// Called with the local epoch day of a tapped cell.
    var onDayTap: ((Int64) -> Void)?

    private let monthLabel = UILabel()
    private let previousButton = UIButton(type: .system)
    private let nextButton = UIButton(type: .system)
    private let gridStack = UIStackView()
    private let tagsTitle = UILabel()
    private let tagsStack = UIStackView()

    private var monthShift = 0
    private var activity: ActivityStats?
    private var chips: [TagChipView] = []
    private var laidOutWidth: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = Palette.inkElevated

        monthLabel.font = TextStyle.headline1
        monthLabel.textColor = Palette.textPrimary
        monthLabel.textAlignment = .center

        configure(previousButton, symbol: "chevron.backward", label: "Previous month") { [weak self] in
            guard let self else { return }
            self.monthShift -= 1
            self.render()
        }
        configure(nextButton, symbol: "chevron.forward", label: "Next month") { [weak self] in
            guard let self else { return }
            self.monthShift += 1
            self.render()
        }

        let header = UIStackView(arrangedSubviews: [previousButton, monthLabel, nextButton])
        header.axis = .horizontal
        header.alignment = .center
        header.distribution = .equalCentering

        gridStack.axis = .vertical
        gridStack.spacing = 6

        tagsTitle.text = "Tags"
        tagsTitle.font = TextStyle.headline1
        tagsTitle.textColor = Palette.textPrimary

        tagsStack.axis = .vertical
        tagsStack.spacing = 8

        let column = UIStackView(arrangedSubviews: [header, gridStack, tagsTitle, tagsStack])
        column.axis = .vertical
        column.spacing = 14
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 12),
            column.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            column.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16)
        ])

        render()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func apply(_ activity: ActivityStats?) {
        self.activity = activity
        render()
    }

    /// Chips wrap to the panel width, so they are re-laid out when that changes.
    override func layoutSubviews() {
        super.layoutSubviews()
        guard abs(bounds.width - laidOutWidth) > 1 else { return }
        laidOutWidth = bounds.width
        layoutChips()
    }

    private func configure(_ button: UIButton, symbol: String, label: String, action: @escaping () -> Void) {
        button.setImage(UIImage(systemName: symbol), for: .normal)
        button.tintColor = Palette.textPrimary
        button.accessibilityLabel = label
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
    }

    private func render() {
        let today = CalendarDays.shared.dateOf(epochDay: ActivityStatsKt.todayEpochDay())
        let shown = CalendarDays.shared.shiftMonth(year: today.year, month: today.month, delta: Int32(monthShift))
        let year = Int(shown.year)
        let month = Int(shown.month)
        monthLabel.text = "\(year)-\(month)"

        gridStack.arrangedSubviews.forEach {
            gridStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        gridStack.addArrangedSubview(weekdayHeader())

        let lead = Int(CalendarDays.shared.weekdayOf(epochDay: CalendarDays.shared.epochDay(year: Int32(year), month: Int32(month), day: 1)))
        let days = Int(CalendarDays.shared.daysInMonth(year: Int32(year), month: Int32(month)))
        let counts = activity?.dayCounts ?? [:]
        let busiest = max(counts.values.map { Int(truncating: $0) }.max() ?? 0, 1)
        let todayEpochDay = ActivityStatsKt.todayEpochDay()

        for row in 0..<((lead + days + 6) / 7) {
            let rowStack = UIStackView()
            rowStack.axis = .horizontal
            rowStack.distribution = .fillEqually
            rowStack.spacing = 6
            for column in 0..<7 {
                let dayNumber = row * 7 + column - lead + 1
                rowStack.addArrangedSubview(dayCell(
                    dayNumber: dayNumber,
                    days: days,
                    year: year,
                    month: month,
                    counts: counts,
                    busiest: busiest,
                    todayEpochDay: todayEpochDay
                ))
            }
            gridStack.addArrangedSubview(rowStack)
        }

        chips.forEach {
            tagsStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        chips = (activity?.tagCounts ?? []).map { entry in
            TagChipView(tag: entry.tag, count: Int(entry.count)) { [weak self] tag in
                self?.onTagTap?(tag)
            }
        }
        layoutChips()
        tagsTitle.isHidden = chips.isEmpty
    }

    private func weekdayHeader() -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.spacing = 6
        for label in ["S", "M", "T", "W", "T", "F", "S"] {
            let text = UILabel()
            text.text = label
            text.font = TextStyle.footnote2
            text.textColor = Palette.textSecondary
            text.textAlignment = .center
            row.addArrangedSubview(text)
        }
        return row
    }

    private func dayCell(
        dayNumber: Int,
        days: Int,
        year: Int,
        month: Int,
        counts: [KotlinLong: KotlinInt],
        busiest: Int,
        todayEpochDay: Int64
    ) -> UIView {
        let cell = DayCell()
        cell.textAlignment = .center
        cell.font = TextStyle.footnote2
        cell.layer.cornerRadius = 6
        cell.clipsToBounds = true
        cell.heightAnchor.constraint(equalTo: cell.widthAnchor).isActive = true
        guard dayNumber >= 1, dayNumber <= days else { return cell }

        cell.text = "\(dayNumber)"
        let day = CalendarDays.shared.epochDay(year: Int32(year), month: Int32(month), day: Int32(dayNumber))
        let count = counts[KotlinLong(value: day)].map { Int(truncating: $0) } ?? 0
        cell.backgroundColor = heatColour(count: count, busiest: busiest)
        cell.textColor = day == todayEpochDay ? Palette.accent : Palette.textPrimary
        // A tap narrows the timeline to this day; the grid is rebuilt on every render,
        // so the recognizer cannot pile up.
        cell.onTap = { [weak self] tapped in self?.onDayTap?(tapped) }
        cell.day = day
        cell.isUserInteractionEnabled = true
        cell.addGestureRecognizer(UITapGestureRecognizer(target: cell, action: #selector(DayCell.handleTap)))
        return cell
    }

    private func heatColour(count: Int, busiest: Int) -> UIColor {
        guard count > 0 else { return Palette.inkLine }
        let step = min(max(CGFloat(count) / CGFloat(busiest), 0.25), 1)
        return Palette.accent.withAlphaComponent(0.25 + 0.75 * step)
    }

    /// Wraps the chips the way the Android panel's flow row does.
    private func layoutChips() {
        tagsStack.arrangedSubviews.forEach {
            tagsStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        guard laidOutWidth > 0, !chips.isEmpty else { return }
        var row = UIStackView()
        row.axis = .horizontal
        row.spacing = 8
        var used: CGFloat = 0
        for chip in chips {
            let width = chip.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize).width
            if used > 0, used + width > laidOutWidth {
                tagsStack.addArrangedSubview(row)
                row = UIStackView()
                row.axis = .horizontal
                row.spacing = 8
                used = 0
            }
            row.addArrangedSubview(chip)
            used += width + 8
        }
        tagsStack.addArrangedSubview(row)
    }
}

/// A day in the month grid; tapping it filters the timeline to that day.
private final class DayCell: UILabel {
    var day: Int64 = 0
    var onTap: ((Int64) -> Void)?

    @objc func handleTap() {
        onTap?(day)
    }
}

/// A tag and its memo count, shaped like the Android chip.
private final class TagChipView: UIView {
    /// Not `tag`: UIView already has one, of type Int.
    private let tagName: String
    private let onTap: (String) -> Void

    init(tag: String, count: Int, onTap: @escaping (String) -> Void) {
        self.tagName = tag
        self.onTap = onTap
        super.init(frame: .zero)

        backgroundColor = Palette.inkLine
        layer.cornerRadius = 14
        clipsToBounds = true

        let name = UILabel()
        name.text = tag.hasPrefix("#") ? tag : "#\(tag)"
        name.font = TextStyle.footnote1
        name.textColor = Palette.accent

        let tally = UILabel()
        tally.text = "\(count)"
        tally.font = TextStyle.footnote2
        tally.textColor = Palette.textSecondary

        let row = UIStackView(arrangedSubviews: [name, tally])
        row.axis = .horizontal
        row.spacing = 6
        row.isUserInteractionEnabled = false
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6),
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12)
        ])

        isUserInteractionEnabled = true
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleTap)))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func handleTap() {
        onTap(tagName)
    }
}
