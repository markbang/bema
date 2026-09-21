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
    private let appThemeRow = MenuRow(label: "App theme")
    private let uploadRow = MenuRow(label: "Upload size limit")

    private var appThemeValue = "system"
    private var uploadValue = "0"
    private var disallowRegistration = false
    private var disallowPasswordAuth = false
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
        save.isEnabled = false
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

        buildAppearanceSection()
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
        loaded = nil
        navigationItem.rightBarButtonItem?.isEnabled = false
        contentStack.arrangedSubviews.forEach {
            contentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        buildAppearanceSection()
        Task { [weak self] in
            guard let self else { return }
            do {
                let settings = try await self.controller.loadInstanceSettings()
                self.loaded = settings
                self.applyLoaded(settings)
                self.navigationItem.rightBarButtonItem?.isEnabled = true
            } catch {
                let message = UILabel()
                message.text = "Instance settings could not be loaded. App theme is still available.\n\(error.localizedDescription)"
                message.textColor = Palette.danger
                message.numberOfLines = 0
                self.contentStack.addArrangedSubview(message)
            }
        }
    }

    private func applyLoaded(_ settings: InstanceSetting) {
        let profile = settings.generalSetting?.customProfile
        titleField.text = profile?.title ?? ""
        descriptionField.text = profile?.description ?? ""
        logoField.text = profile?.logoUrl ?? ""
        disallowRegistration = settings.generalSetting?.disallowUserRegistration ?? false
        disallowPasswordAuth = settings.generalSetting?.disallowPasswordAuth ?? false
        uploadValue = String(settings.storageSetting?.uploadSizeLimitMb ?? 0)
        disallowChangeUsername = settings.generalSetting?.disallowChangeUsername ?? false
        disallowChangeNickname = settings.generalSetting?.disallowChangeNickname ?? false
        buildForm()
    }

    private func buildForm() {
        contentStack.arrangedSubviews.forEach {
            contentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        buildAppearanceSection()
        uploadRow.setOptions(tuples(InstanceSettingsPresentationKt.uploadSizeOptionsFor(current: uploadValue)), selected: uploadValue)
        uploadRow.onSelect = { [weak self] value in
            self?.uploadValue = value
            self?.refreshMenuRows()
        }

        let rows: [UIView] = [
            sectionLabel("General"),
            titleField,
            descriptionField,
            logoField,
            switchRow("Disallow user registration", isOn: disallowRegistration) { [weak self] value in
                self?.disallowRegistration = value
            },
            switchRow("Disallow password sign-in", isOn: disallowPasswordAuth) { [weak self] value in
                self?.disallowPasswordAuth = value
            },
            switchRow("Disallow changing username", isOn: disallowChangeUsername) { [weak self] value in
                self?.disallowChangeUsername = value
            },
            switchRow("Disallow changing nickname", isOn: disallowChangeNickname) { [weak self] value in
                self?.disallowChangeNickname = value
            },
            sectionLabel("Storage"),
            uploadRow
        ]
        rows.forEach(contentStack.addArrangedSubview)
    }

    private func buildAppearanceSection() {
        appThemeRow.setOptions(tuples(ThemePreferenceKt.ThemeModeOptions), selected: appThemeValue)
        appThemeRow.onSelect = { [weak self] value in
            guard let self else { return }
            self.appThemeValue = value
            self.controller.setThemeMode(mode: ThemePreferenceKt.canonicalThemeMode(raw: value))
        }
        contentStack.addArrangedSubview(sectionLabel("Appearance"))
        contentStack.addArrangedSubview(appThemeRow)
    }

    private func refreshMenuRows() {
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
        guard loaded != nil else { return }
        let alert = UIAlertController(
            title: "Apply to instance?",
            message: "These changes apply to \(siteTitle) and affect every user.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Apply", style: .default) { [weak self] _ in
            self?.performSave()
        })
        present(alert, animated: true)
    }

    /// The API merges editable fields into the current server resource.
    private func performSave() {
        guard !isSaving, let uploadSize = Int64(uploadValue), uploadSize >= 0 else { return }
        isSaving = true
        navigationItem.rightBarButtonItem?.isEnabled = false
        navigationItem.leftBarButtonItem?.isEnabled = false
        navigationController?.isModalInPresentation = true

        let general = GeneralSetting(
            customProfile: CustomProfile(
                title: titleField.trimmedText,
                description: descriptionField.trimmedText,
                logoUrl: logoField.trimmedText
            ),
            disallowUserRegistration: disallowRegistration,
            disallowPasswordAuth: disallowPasswordAuth,
            disallowChangeUsername: disallowChangeUsername,
            disallowChangeNickname: disallowChangeNickname
        )
        let storage = StorageSetting(uploadSizeLimitMb: uploadSize)

        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.controller.saveInstanceSettings(
                    general: general,
                    storage: storage
                )
                self.dismiss(animated: true)
            } catch {
                self.isSaving = false
                self.navigationItem.rightBarButtonItem?.isEnabled = true
                self.navigationItem.leftBarButtonItem?.isEnabled = true
                self.navigationController?.isModalInPresentation = false
                presentError(error.localizedDescription, from: self)
            }
        }
    }
}
