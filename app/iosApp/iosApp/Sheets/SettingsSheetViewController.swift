import UIKit

/// A single-choice list dialog, the equivalent of `SettingsOptionPicker`.
final class OptionPickerViewController: MemosDialogViewController {
    private let options: [SettingOption]
    private let selected: String
    private let onSelect: (String) -> Void

    init(title: String, options: [SettingOption], selected: String, onSelect: @escaping (String) -> Void) {
        self.options = options
        self.selected = selected
        self.onSelect = onSelect
        super.init(title: title, maxHeightRatio: 0.6)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        let stack = sheetStack(options.map { option in
            let active = option.value == selected
            let label = UILabel()
            label.text = option.label
            label.font = TextStyle.paragraph
            label.textColor = active ? Palette.accent : Palette.textPrimary

            let row = UIStackView(arrangedSubviews: [label])
            row.axis = .horizontal
            row.alignment = .center
            row.spacing = 8
            row.isLayoutMarginsRelativeArrangement = true
            row.layoutMargins = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
            row.backgroundColor = active ? Palette.accent.withAlphaComponent(0.18) : .clear
            row.layer.cornerRadius = 12
            row.isUserInteractionEnabled = true
            if active {
                row.addArrangedSubview(UIImageView(image: MiuixIcons.image(.ok, size: 18, color: Palette.accent)))
            }
            row.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(rowTapped(_:))))
            row.restorationIdentifier = option.value
            return row
        }, spacing: 4)
        setContent(stack)
    }

    @objc private func rowTapped(_ recognizer: UITapGestureRecognizer) {
        guard let value = recognizer.view?.restorationIdentifier else { return }
        onSelect(value)
        dismiss(animated: true)
    }
}

/// The instance settings form. Mirrors `SettingsSheet`: load, edit a draft, then
/// confirm before applying to the whole instance.
final class SettingsSheetViewController: MemosDialogViewController {
    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var loadedAccountId = ""

    private let container = UIView()
    private var loaded: InstanceSetting?

    private let titleField = DarkTextField(placeholder: "Instance title")
    private let descriptionField = DarkTextField(placeholder: "Description")
    private let logoField = DarkTextField(placeholder: "Logo URL")
    private let announcementField = DarkTextField(placeholder: "Announcement")
    private let atomFeedField = DarkTextField(placeholder: "Atom feed badge URL")

    private lazy var languageRow = OptionRow(label: "Language", value: "") { [weak self] in self?.pickLanguage() }
    private lazy var themeRow = OptionRow(label: "Theme", value: "") { [weak self] in self?.pickTheme() }
    private lazy var uploadRow = OptionRow(label: "Upload size limit", value: "") { [weak self] in self?.pickUploadSize() }

    private var instanceLocale = "en"
    private var instanceAppearance = "system"
    private var maxUploadSizeMiB = "0"
    private var disallowRegistration = false
    private var disallowPasswordLogin = false
    private var enableLinkMetadata = true
    private var displayWithUpdateTime = false
    private var disallowChangeUsername = false
    private var disallowChangeNickname = false
    private var isSaving = false
    private var siteTitle = "the instance"

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(title: "Settings", maxHeightRatio: 0.85)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        setContent(container)
        showLoading()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            guard let self else { return }
            self.siteTitle = state.activeAccount?.siteTitle ?? "the instance"
            // Compose reloads whenever the active account changes.
            guard state.activeAccountId != self.loadedAccountId else { return }
            self.loadedAccountId = state.activeAccountId
            self.load()
        }
    }

    deinit {
        observation?.cancel()
    }

    private func replaceContent(with view: UIView) {
        container.subviews.forEach { $0.removeFromSuperview() }
        view.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(view)
        NSLayoutConstraint.activate([
            view.topAnchor.constraint(equalTo: container.topAnchor),
            view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            view.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
    }

    private func showLoading() {
        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = Palette.accent
        spinner.startAnimating()
        let holder = UIView()
        holder.addSubview(spinner)
        spinner.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            holder.heightAnchor.constraint(equalToConstant: 160),
            spinner.centerXAnchor.constraint(equalTo: holder.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: holder.centerYAnchor)
        ])
        replaceContent(with: holder)
    }

    private func showError(_ message: String) {
        let label = UILabel()
        label.text = message
        label.font = TextStyle.paragraph
        label.textColor = Palette.danger
        label.numberOfLines = 0
        let close = TextActionButton(title: "Close")
        close.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        replaceContent(with: sheetStack([label, close], spacing: 14))
    }

    private func load() {
        showLoading()
        Task { [weak self] in
            guard let self else { return }
            do {
                let settings = try await self.controller.loadInstanceSettings()
                self.loaded = settings
                self.applyLoaded(settings)
                self.showForm()
            } catch {
                self.showError(error.localizedDescription)
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
        instanceLocale = InstanceSettingsPresentationKt.canonicalLocale(raw: profile?.locale ?? "")
        instanceAppearance = InstanceSettingsPresentationKt.canonicalAppearance(raw: profile?.appearance ?? "")
        disallowRegistration = settings.generalSetting?.disallowUserRegistration ?? false
        disallowPasswordLogin = settings.generalSetting?.disallowPasswordLogin ?? false
        enableLinkMetadata = settings.memoRelatedSetting?.enableLinkMetadata ?? true
        displayWithUpdateTime = settings.memoRelatedSetting?.displayWithUpdateTime ?? false
        announcementField.text = settings.workspaceSetting?.announcement ?? ""
        maxUploadSizeMiB = String(settings.workspaceSetting?.maxUploadSizeMiB ?? 0)
        atomFeedField.text = settings.workspaceSetting?.atomFeedBadgeUrl ?? ""
        disallowChangeUsername = settings.workspaceSetting?.disallowChangeUsername ?? false
        disallowChangeNickname = settings.workspaceSetting?.disallowChangeNickname ?? false
        refreshOptionRows()
    }

    private func refreshOptionRows() {
        languageRow.setValue(InstanceSettingsPresentationKt.localeLabel(value: instanceLocale))
        themeRow.setValue(InstanceSettingsPresentationKt.appearanceLabel(value: instanceAppearance))
        uploadRow.setValue(InstanceSettingsPresentationKt.uploadSizeLabel(value: maxUploadSizeMiB))
    }

    private func showForm() {
        let cancel = TextActionButton(title: "Cancel")
        cancel.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        let save = TextActionButton(title: "Save", primary: true)
        save.addAction(UIAction { [weak self] _ in self?.confirmSave() }, for: .touchUpInside)
        let buttons = UIStackView(arrangedSubviews: [cancel, save])
        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        let stack = sheetStack([
            SectionLabel("General"),
            titleField,
            descriptionField,
            logoField,
            languageRow,
            themeRow,
            SwitchRow(label: "Disallow user registration", isOn: disallowRegistration) { [weak self] value in
                self?.disallowRegistration = value
            },
            SwitchRow(label: "Disallow password sign-in", isOn: disallowPasswordLogin) { [weak self] value in
                self?.disallowPasswordLogin = value
            },

            SectionLabel("Memo"),
            SwitchRow(label: "Fetch link metadata", isOn: enableLinkMetadata) { [weak self] value in
                self?.enableLinkMetadata = value
            },
            SwitchRow(label: "Sort by update time", isOn: displayWithUpdateTime) { [weak self] value in
                self?.displayWithUpdateTime = value
            },

            SectionLabel("Workspace"),
            announcementField,
            uploadRow,
            atomFeedField,
            SwitchRow(label: "Disallow changing username", isOn: disallowChangeUsername) { [weak self] value in
                self?.disallowChangeUsername = value
            },
            SwitchRow(label: "Disallow changing nickname", isOn: disallowChangeNickname) { [weak self] value in
                self?.disallowChangeNickname = value
            },

            buttons
        ])
        replaceContent(with: stack)
    }

    private func pickLanguage() {
        let options = InstanceSettingsPresentationKt.localeOptionsFor(current: instanceLocale)
        let picker = OptionPickerViewController(title: "Language", options: options, selected: instanceLocale) { [weak self] value in
            self?.instanceLocale = value
            self?.refreshOptionRows()
        }
        present(picker, animated: true)
    }

    private func pickTheme() {
        let picker = OptionPickerViewController(
            title: "Theme",
            options: InstanceSettingsPresentationKt.AppearanceOptions,
            selected: instanceAppearance
        ) { [weak self] value in
            self?.instanceAppearance = value
            self?.refreshOptionRows()
        }
        present(picker, animated: true)
    }

    private func pickUploadSize() {
        let options = InstanceSettingsPresentationKt.uploadSizeOptionsFor(current: maxUploadSizeMiB)
        let picker = OptionPickerViewController(title: "Upload size limit", options: options, selected: maxUploadSizeMiB) { [weak self] value in
            self?.maxUploadSizeMiB = value
            self?.refreshOptionRows()
        }
        present(picker, animated: true)
    }

    private func confirmSave() {
        guard let loaded else { return }
        let message = UILabel()
        message.text = "These changes apply to \(siteTitle) and affect every user."
        message.font = TextStyle.paragraph
        message.textColor = Palette.textSecondary
        message.numberOfLines = 0

        let cancel = TextActionButton(title: "Cancel")
        let apply = TextActionButton(title: "Apply", primary: true)
        let buttons = UIStackView(arrangedSubviews: [cancel, apply])
        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        let dialog = MemosDialogViewController(title: "Apply to instance?")
        cancel.addAction(UIAction { [weak dialog] _ in dialog?.dismiss(animated: true) }, for: .touchUpInside)
        apply.addAction(UIAction { [weak self, weak dialog] _ in
            dialog?.dismiss(animated: true)
            self?.performSave(loaded)
        }, for: .touchUpInside)
        dialog.setContent(sheetStack([message, buttons], spacing: 14))
        present(dialog, animated: true)
    }

    /// Rebuilds the whole setting from the loaded copy, so fields the form does
    /// not edit (memo reactions) survive the replace.
    private func performSave(_ loaded: InstanceSetting) {
        guard !isSaving else { return }
        isSaving = true

        func trimmed(_ field: UITextField) -> String {
            field.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }

        let general = GeneralSetting(
            customProfile: CustomProfile(
                title: trimmed(titleField),
                description: trimmed(descriptionField),
                logoUrl: trimmed(logoField),
                locale: instanceLocale.trimmingCharacters(in: .whitespacesAndNewlines),
                appearance: instanceAppearance.trimmingCharacters(in: .whitespacesAndNewlines)
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
            announcement: trimmed(announcementField),
            maxUploadSizeMiB: Int64(maxUploadSizeMiB.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
            atomFeedBadgeUrl: trimmed(atomFeedField),
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
                self.showError(error.localizedDescription)
            }
        }
    }
}
