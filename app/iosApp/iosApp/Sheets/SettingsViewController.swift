import SharedLogic
import UIKit

/// The instance settings sheet: load, edit a draft, then confirm before applying
/// to the whole instance. Mirrors `SettingsSheet` with menus instead of dialogs.
final class SettingsViewController: UIViewController {
    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var loadedAccountId = ""
    private var loaded: InstanceSetting?
    private var siteTitle = "the instance"
    private var isSaving = false

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    private let titleField = UITextField.memo(memoPlaceholder: "Instance title")
    private let descriptionField = UITextField.memo(memoPlaceholder: "Description")
    private let logoField = UITextField.memo(memoPlaceholder: "Logo URL")
    private let announcementField = UITextField.memo(memoPlaceholder: "Announcement")
    private let atomFeedField = UITextField.memo(memoPlaceholder: "Atom feed badge URL")

    private let languageRow = MenuRow(label: "Language")
    private let themeRow = MenuRow(label: "Instance theme")
    private let appThemeRow = MenuRow(label: "App theme")
    private let uploadRow = MenuRow(label: "Upload size limit")

    private var languageValue = "en"
    private var themeValue = "system"
    private var appThemeValue = "system"
    private var uploadValue = "0"
    private var disallowRegistration = false
    private var disallowPasswordLogin = false
    private var enableLinkMetadata = true
    private var displayWithUpdateTime = false
    private var disallowChangeUsername = false
    private var disallowChangeNickname = false

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
        title = "Settings"
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink

        navigationItem.leftBarButtonItem = UIBarButtonItem(
            systemItem: .cancel,
            primaryAction: UIAction { [weak self] _ in self?.dismiss(animated: true) }
        )
        let save = UIBarButtonItem(
            title: "Save",
            image: nil,
            primaryAction: UIAction { [weak self] _ in self?.confirmSave() },
            menu: nil
        )
        save.tintColor = Palette.accent
        navigationItem.rightBarButtonItem = save

        contentStack.axis = .vertical
        contentStack.spacing = 14
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 20),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -20),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -40)
        ])

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            guard let self else { return }
            self.siteTitle = state.activeAccount?.siteTitle ?? "the instance"
            // Client-side appearance can change while the sheet is open; the
            // instance form below only reloads when the account changes.
            self.appThemeValue = ThemePreferenceKt.themeModeValue(mode: state.themeMode)
            self.appThemeRow.setOptions(self.tuples(ThemePreferenceKt.ThemeModeOptions), selected: self.appThemeValue)
            guard state.activeAccountId != self.loadedAccountId else { return }
            self.loadedAccountId = state.activeAccountId
            self.load()
        }
    }

    deinit {
        observation?.cancel()
    }

    private func load() {
        Task { [weak self] in
            guard let self else { return }
            do {
                let settings = try await self.controller.loadInstanceSettings()
                self.loaded = settings
                self.applyLoaded(settings)
            } catch {
                presentError(error.localizedDescription, from: self)
                self.dismiss(animated: true)
            }
        }
    }

    private func applyLoaded(_ settings: InstanceSetting) {
        let profile = settings.generalSetting?.customProfile
        titleField.text = profile?.title ?? ""
        descriptionField.text = profile?.description ?? ""
        logoField.text = profile?.logoUrl ?? ""
        // The instance may report a locale or appearance the client does not know,
        // so normalise it the same way the Android UI does.
        languageValue = InstanceSettingsPresentationKt.canonicalLocale(raw: profile?.locale ?? "")
        themeValue = InstanceSettingsPresentationKt.canonicalAppearance(raw: profile?.appearance ?? "")
        disallowRegistration = settings.generalSetting?.disallowUserRegistration ?? false
        disallowPasswordLogin = settings.generalSetting?.disallowPasswordLogin ?? false
        enableLinkMetadata = settings.memoRelatedSetting?.enableLinkMetadata ?? true
        displayWithUpdateTime = settings.memoRelatedSetting?.displayWithUpdateTime ?? false
        announcementField.text = settings.workspaceSetting?.announcement ?? ""
        uploadValue = String(settings.workspaceSetting?.maxUploadSizeMiB ?? 0)
        atomFeedField.text = settings.workspaceSetting?.atomFeedBadgeUrl ?? ""
        disallowChangeUsername = settings.workspaceSetting?.disallowChangeUsername ?? false
        disallowChangeNickname = settings.workspaceSetting?.disallowChangeNickname ?? false
        buildForm()
    }

    private func buildForm() {
        contentStack.arrangedSubviews.forEach {
            contentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        languageRow.setOptions(tuples(InstanceSettingsPresentationKt.localeOptionsFor(current: languageValue)), selected: languageValue)
        languageRow.onSelect = { [weak self] value in
            self?.languageValue = value
            self?.refreshMenuRows()
        }
        themeRow.setOptions(tuples(InstanceSettingsPresentationKt.AppearanceOptions), selected: themeValue)
        themeRow.onSelect = { [weak self] value in
            self?.themeValue = value
            self?.refreshMenuRows()
        }
        appThemeRow.setOptions(tuples(ThemePreferenceKt.ThemeModeOptions), selected: appThemeValue)
        appThemeRow.onSelect = { [weak self] value in
            guard let self else { return }
            self.appThemeValue = value
            self.controller.setThemeMode(mode: ThemePreferenceKt.canonicalThemeMode(raw: value))
        }
        uploadRow.setOptions(tuples(InstanceSettingsPresentationKt.uploadSizeOptionsFor(current: uploadValue)), selected: uploadValue)
        uploadRow.onSelect = { [weak self] value in
            self?.uploadValue = value
            self?.refreshMenuRows()
        }

        let rows: [UIView] = [
            sectionLabel("Appearance"),
            appThemeRow,
            sectionLabel("General"),
            titleField,
            descriptionField,
            logoField,
            languageRow,
            themeRow,
            switchRow("Disallow user registration", isOn: disallowRegistration) { [weak self] value in
                self?.disallowRegistration = value
            },
            switchRow("Disallow password sign-in", isOn: disallowPasswordLogin) { [weak self] value in
                self?.disallowPasswordLogin = value
            },

            sectionLabel("Memo"),
            switchRow("Fetch link metadata", isOn: enableLinkMetadata) { [weak self] value in
                self?.enableLinkMetadata = value
            },
            switchRow("Sort by update time", isOn: displayWithUpdateTime) { [weak self] value in
                self?.displayWithUpdateTime = value
            },

            sectionLabel("Workspace"),
            announcementField,
            uploadRow,
            atomFeedField,
            switchRow("Disallow changing username", isOn: disallowChangeUsername) { [weak self] value in
                self?.disallowChangeUsername = value
            },
            switchRow("Disallow changing nickname", isOn: disallowChangeNickname) { [weak self] value in
                self?.disallowChangeNickname = value
            }
        ]
        rows.forEach(contentStack.addArrangedSubview)
    }

    private func refreshMenuRows() {
        languageRow.setOptions(tuples(InstanceSettingsPresentationKt.localeOptionsFor(current: languageValue)), selected: languageValue)
        themeRow.setOptions(tuples(InstanceSettingsPresentationKt.AppearanceOptions), selected: themeValue)
        appThemeRow.setOptions(tuples(ThemePreferenceKt.ThemeModeOptions), selected: appThemeValue)
        uploadRow.setOptions(tuples(InstanceSettingsPresentationKt.uploadSizeOptionsFor(current: uploadValue)), selected: uploadValue)
    }

    private func tuples(_ options: [SettingOption]) -> [(value: String, label: String)] {
        options.map { (value: $0.value, label: $0.label) }
    }

    private func sectionLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = TextStyle.footnote1.weight(.semibold)
        label.textColor = Palette.accent
        return label
    }

    private func switchRow(_ label: String, isOn: Bool, onChange: @escaping (Bool) -> Void) -> UIView {
        let labelView = UILabel()
        labelView.text = label
        labelView.font = TextStyle.paragraph
        labelView.textColor = Palette.textPrimary
        labelView.numberOfLines = 0

        let toggle = UISwitch()
        toggle.isOn = isOn
        toggle.onTintColor = Palette.accent
        toggle.addAction(UIAction { [weak toggle] _ in
            guard let toggle else { return }
            onChange(toggle.isOn)
        }, for: .valueChanged)

        let row = UIStackView(arrangedSubviews: [labelView, toggle])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        return row
    }

    private func confirmSave() {
        guard let loaded else { return }
        let alert = UIAlertController(
            title: "Apply to instance?",
            message: "These changes apply to \(siteTitle) and affect every user.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Apply", style: .default) { [weak self] _ in
            self?.performSave(loaded)
        })
        present(alert, animated: true)
    }

    /// Rebuilds the whole setting from the loaded copy, so fields the form does
    /// not edit (memo reactions) survive the replace.
    private func performSave(_ loaded: InstanceSetting) {
        guard !isSaving else { return }
        isSaving = true

        let general = GeneralSetting(
            customProfile: CustomProfile(
                title: titleField.trimmedText,
                description: descriptionField.trimmedText,
                logoUrl: logoField.trimmedText,
                locale: languageValue.trimmingCharacters(in: .whitespacesAndNewlines),
                appearance: themeValue.trimmingCharacters(in: .whitespacesAndNewlines)
            ),
            disallowUserRegistration: disallowRegistration,
            disallowPasswordLogin: disallowPasswordLogin
        )
        let memoRelated = MemoRelatedSetting(
            enableLinkMetadata: enableLinkMetadata,
            displayWithUpdateTime: displayWithUpdateTime,
            reactions: loaded.memoRelatedSetting?.reactions
        )
        let workspace = WorkspaceSetting(
            announcement: announcementField.trimmedText,
            maxUploadSizeMiB: Int64(uploadValue.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
            atomFeedBadgeUrl: atomFeedField.trimmedText,
            disallowChangeUsername: disallowChangeUsername,
            disallowChangeNickname: disallowChangeNickname
        )

        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.controller.saveInstanceSettings(
                    general: general,
                    memoRelated: memoRelated,
                    workspace: workspace
                )
                self.dismiss(animated: true)
            } catch {
                self.isSaving = false
                presentError(error.localizedDescription, from: self)
            }
        }
    }
}
