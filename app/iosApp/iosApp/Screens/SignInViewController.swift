import SharedLogic
import UIKit

/// The full-screen sign-in form, matching the Android `SignInScreen`.
final class SignInViewController: UIViewController {
    var onSignedIn: (() -> Void)?

    private let controller: MemosTimelineController
    private var observation: IosObservation?

    private let instanceField = DarkTextField(placeholder: "memos.example.com")
    private let usernameField = DarkTextField(placeholder: "Username")
    private let passwordField = DarkTextField(placeholder: "Password", secure: true)
    private let button = PrimaryButton(title: "Sign in")
    private let errorLabel = UILabel()

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink

        let wordmark = UILabel()
        wordmark.text = "bema"
        wordmark.font = TextStyle.title1.weight(.black)
        wordmark.textColor = Palette.textPrimary

        let tagline = UILabel()
        tagline.text = "Your Memos, in one timeline."
        tagline.font = TextStyle.headline1
        tagline.textColor = Palette.textSecondary

        errorLabel.font = TextStyle.paragraph
        errorLabel.textColor = Palette.danger
        errorLabel.numberOfLines = 0
        errorLabel.isHidden = true

        button.addAction(UIAction { [weak self] _ in self?.signIn() }, for: .touchUpInside)

        let stack = sheetStack(
            [wordmark, tagline, instanceField, usernameField, passwordField, button, errorLabel],
            spacing: 12
        )
        stack.setCustomSpacing(36, after: tagline)
        stack.setCustomSpacing(20, after: passwordField)
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 28),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -28),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
    }

    deinit {
        observation?.cancel()
    }

    private func apply(_ state: MemosAppState) {
        button.isEnabled = !state.isLoading
        button.setTitle(state.isLoading ? "Connecting…" : "Sign in", for: .normal)
        if state.activeAccount != nil {
            onSignedIn?()
            return
        }
        if let error = state.error {
            errorLabel.text = error
            errorLabel.isHidden = false
        }
    }

    private func signIn() {
        let instance = instanceField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let username = usernameField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let password = passwordField.text ?? ""
        // The shared controller rejects blanks with `require`; check here so the
        // user gets a message instead of a thrown error round-trip.
        guard !instance.isEmpty, !username.isEmpty, !password.isEmpty else {
            errorLabel.text = "Instance, username and password are required."
            errorLabel.isHidden = false
            return
        }
        errorLabel.isHidden = true
        Task { [weak self] in
            guard let self else { return }
            try? await self.controller.addAccount(instanceUrl: instance, username: username, password: password)
        }
    }
}
