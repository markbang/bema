import CryptoKit
import PhotosUI
import Security
import UIKit
import UniformTypeIdentifiers

struct NativeMemo: Codable, Hashable {
    var name: String = ""
    var creator: String = ""
    var createTime: String? = nil
    var content: String = ""
    var visibility: String = "PRIVATE"
    var tags: [String] = []
    var pinned: Bool = false
    var attachments: [NativeAttachment] = []
    var reactions: [NativeReaction] = []
    var parent: String? = nil
    var snippet: String = ""
}

struct NativeAttachment: Codable, Hashable {
    var name: String = ""
    var filename: String = ""
    var externalLink: String = ""
    var type: String = ""

    var uid: String { name.split(separator: "/").last.map(String.init) ?? "" }
    var isImage: Bool { type.hasPrefix("image/") }
}

struct NativeReaction: Codable, Hashable {
    var name: String = ""
    var creator: String = ""
    var reactionType: String = ""
}

struct NativeUser: Codable, Hashable {
    var name: String = ""
    var username: String = ""
    var displayName: String = ""
    var avatarUrl: String = ""

    var visibleName: String { displayName.isEmpty ? username : displayName }
}

struct NativeAccount: Codable, Hashable {
    var id: String
    var instanceUrl: String
    var username: String
    var userName: String
    var displayName: String
    var avatarUrl: String
    var siteTitle: String = "Memos"
    var siteLogoUrl: String = ""

    var visibleName: String { displayName.isEmpty ? username : displayName }
}

private struct StoredNativeState: Codable {
    var activeAccountId: String = ""
    var accounts: [NativeAccount] = []
}

private struct PasswordCredentials: Codable {
    var username: String
    var password: String
}

private struct SignInBody: Codable {
    var passwordCredentials: PasswordCredentials
}

struct SignInResponse: Codable {
    var user: NativeUser
    var accessToken: String
    var accessTokenExpiresAt: String?
}

struct RefreshTokenResponse: Codable {
    var accessToken: String
    var expiresAt: String?
}

struct ListMemosResponse: Codable {
    var memos: [NativeMemo]?
    var nextPageToken: String?
}

struct ListCommentsResponse: Codable {
    var memos: [NativeMemo]?
    var nextPageToken: String?
}

private struct NativeAttachmentUpload: Codable {
    var filename: String
    var content: Data
    var type: String
}

private struct NativeAttachmentReference: Codable {
    var name: String
}

private struct MemoBody: Codable {
    var content: String
    var visibility: String
    var attachments: [NativeAttachmentReference] = []
}

struct PendingNativeAttachment {
    var filename: String
    var data: Data
    var type: String
}

private struct NativeCustomProfile: Codable {
    var title: String?
    var description: String?
    var logoUrl: String?
}

private struct NativeGeneralSetting: Codable {
    var customProfile: NativeCustomProfile?
}

private struct NativeInstanceSetting: Codable {
    var name: String
    var generalSetting: NativeGeneralSetting?
}

private struct BatchUsersBody: Codable {
    var usernames: [String]
}

private struct BatchUsersResponse: Codable {
    var users: [NativeUser]
}

private struct ReactionBody: Codable {
    var reaction: ReactionInput
}

private struct ReactionInput: Codable {
    var reactionType: String
}

private enum NativeClientError: Error, LocalizedError {
    case invalidURL
    case http(Int, String)
    case missingAccount

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid instance URL"
        case .http(let code, let message): return "HTTP \(code): \(message)"
        case .missingAccount: return "No active account"
        }
    }
}

final class NativeAccountStore {
    private let key = "bema.native.memos.state"
    private var state: StoredNativeState

    init() {
        if let data = UserDefaults.standard.data(forKey: key), let decoded = try? JSONDecoder().decode(StoredNativeState.self, from: data) {
            state = decoded
        } else {
            state = StoredNativeState()
        }
    }

    var accounts: [NativeAccount] { state.accounts }
    var activeAccount: NativeAccount? { state.accounts.first { $0.id == state.activeAccountId } ?? state.accounts.first }
    var activeAccountId: String { activeAccount?.id ?? "" }

    func upsert(_ account: NativeAccount) {
        state.accounts.removeAll { $0.id == account.id }
        state.accounts.append(account)
        state.accounts.sort { $0.instanceUrl + $0.username < $1.instanceUrl + $1.username }
        state.activeAccountId = account.id
        save()
    }

    func select(_ account: NativeAccount) {
        state.activeAccountId = account.id
        save()
    }

    func remove(_ account: NativeAccount) {
        state.accounts.removeAll { $0.id == account.id }
        KeychainCookieStore.save(nil, accountId: account.id)
        if state.activeAccountId == account.id {
            state.activeAccountId = state.accounts.first?.id ?? ""
        }
        save()
    }

    func cookie(for account: NativeAccount) -> String? {
        KeychainCookieStore.load(accountId: account.id)
    }

    func setCookie(_ cookie: String?, for account: NativeAccount) {
        KeychainCookieStore.save(cookie, accountId: account.id)
    }

    private func save() {
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}

private enum KeychainCookieStore {
    private static let service = "dev.bema.ios.memos-refresh"

    static func load(accountId: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: accountId,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func save(_ cookie: String?, accountId: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: accountId
        ]
        SecItemDelete(query as CFDictionary)
        guard let cookie, let data = cookie.data(using: .utf8) else { return }
        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }
}

final class NativeMemosAPI {
    private let account: NativeAccount
    private let store: NativeAccountStore
    private let baseURL: URL
    private var accessToken: String?
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let session: URLSession

    init(account: NativeAccount, store: NativeAccountStore) throws {
        guard let url = URL(string: normalizeInstanceURL(account.instanceUrl)) else { throw NativeClientError.invalidURL }
        self.account = account
        self.store = store
        self.baseURL = url
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        self.session = URLSession(configuration: configuration)
    }

    func signIn(username: String, password: String) async throws -> SignInResponse {
        let response: SignInResponse = try await send(
            method: "POST",
            path: "/api/v1/auth/signin",
            body: SignInBody(passwordCredentials: PasswordCredentials(username: username, password: password)),
            authenticated: false,
            allowRefresh: false
        )
        accessToken = response.accessToken
        return response
    }

    func refreshToken() async throws {
        let response: RefreshTokenResponse = try await send(
            method: "POST",
            path: "/api/v1/auth/refresh",
            body: EmptyBody(),
            authenticated: false,
            allowRefresh: false
        )
        accessToken = response.accessToken
    }

    func generalSetting() async throws -> NativeInstanceSetting {
        try await send(method: "GET", path: "/api/v1/instance/settings/GENERAL", authenticated: true)
    }

    func batchGetUsers(_ usernames: [String]) async throws -> [NativeUser] {
        guard !usernames.isEmpty else { return [] }
        let response: BatchUsersResponse = try await send(method: "POST", path: "/api/v1/users:batchGet", body: BatchUsersBody(usernames: Array(Set(usernames)).prefix(100).map { $0 }), authenticated: true)
        return response.users
    }

    func userAvatarURL(username: String) throws -> URL {
        try makeURL(path: "/file/users/\(username)/avatar")
    }

    func assetURL(_ value: String) throws -> URL {
        if value.hasPrefix("http://") || value.hasPrefix("https://") || value.hasPrefix("data:") {
            guard let url = URL(string: value) else { throw NativeClientError.invalidURL }
            return url
        }
        return try makeURL(path: value)
    }

    func listMemos(pageToken: String = "") async throws -> ListMemosResponse {
        try await send(
            method: "GET",
            path: "/api/v1/memos",
            query: [
                URLQueryItem(name: "pageSize", value: "30"),
                URLQueryItem(name: "orderBy", value: "pinned desc, create_time desc"),
                URLQueryItem(name: "pageToken", value: pageToken.isEmpty ? nil : pageToken)
            ].filter { $0.value != nil },
            authenticated: true
        )
    }

    func getMemo(_ name: String) async throws -> NativeMemo {
        try await send(method: "GET", path: "/api/v1/\(name)", authenticated: true)
    }

    func uploadAttachment(_ attachment: PendingNativeAttachment) async throws -> NativeAttachment {
        try await send(method: "POST", path: "/api/v1/attachments", body: NativeAttachmentUpload(filename: attachment.filename, content: attachment.data, type: attachment.type), authenticated: true)
    }

    func createMemo(content: String, visibility: String, attachments: [NativeAttachment] = []) async throws -> NativeMemo {
        try await send(
            method: "POST",
            path: "/api/v1/memos",
            body: MemoBody(content: content, visibility: visibility, attachments: attachments.map { NativeAttachmentReference(name: $0.name) }),
            authenticated: true
        )
    }

    func listComments(for memoName: String) async throws -> ListCommentsResponse {
        try await send(
            method: "GET",
            path: "/api/v1/\(memoName)/comments",
            query: [URLQueryItem(name: "pageSize", value: "100"), URLQueryItem(name: "orderBy", value: "create_time asc")],
            authenticated: true
        )
    }

    func createComment(for memoName: String, content: String, visibility: String) async throws -> NativeMemo {
        try await send(
            method: "POST",
            path: "/api/v1/\(memoName)/comments",
            body: MemoBody(content: content, visibility: visibility),
            authenticated: true
        )
    }

    func react(to memoName: String, reaction: String) async throws -> NativeReaction {
        try await send(
            method: "POST",
            path: "/api/v1/\(memoName)/reactions",
            body: ReactionBody(reaction: ReactionInput(reactionType: reaction)),
            authenticated: true
        )
    }

    func attachmentURL(_ attachment: NativeAttachment, thumbnail: Bool) throws -> URL {
        if !attachment.externalLink.isEmpty, let url = URL(string: attachment.externalLink) { return url }
        return try makeURL(
            path: "/file/attachments/\(attachment.uid)/\(attachment.filename)",
            query: thumbnail ? [URLQueryItem(name: "thumbnail", value: "true")] : []
        )
    }

    func loadAssetImage(_ value: String) async -> UIImage? {
        guard let url = try? makeURL(path: value) else { return nil }
        var request = URLRequest(url: url)
        if let cookie = store.cookie(for: account) { request.setValue(cookie, forHTTPHeaderField: "Cookie") }
        if let token = accessToken { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        guard let (data, _) = try? await session.data(for: request) else { return nil }
        return UIImage(data: data)
    }

    func loadUserAvatar(_ user: NativeUser) async -> UIImage? {
        guard let url = try? userAvatarURL(username: user.username) else { return nil }
        var request = URLRequest(url: url)
        if let cookie = store.cookie(for: account) { request.setValue(cookie, forHTTPHeaderField: "Cookie") }
        if let token = accessToken { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        guard let (data, _) = try? await session.data(for: request) else { return nil }
        return UIImage(data: data)
    }

    func loadAttachmentImage(_ attachment: NativeAttachment) async -> UIImage? {
        guard let url = try? attachmentURL(attachment, thumbnail: true) else { return nil }
        var request = URLRequest(url: url)
        if let cookie = store.cookie(for: account) { request.setValue(cookie, forHTTPHeaderField: "Cookie") }
        if let token = accessToken { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        guard let (data, _) = try? await session.data(for: request) else { return nil }
        return UIImage(data: data)
    }

    private func send<T: Decodable, Body: Encodable>(
        method: String,
        path: String,
        query: [URLQueryItem] = [],
        body: Body,
        authenticated: Bool,
        allowRefresh: Bool = true
    ) async throws -> T {
        try await send(method: method, path: path, query: query, bodyData: try encoder.encode(body), authenticated: authenticated, allowRefresh: allowRefresh)
    }

    private func send<T: Decodable>(
        method: String,
        path: String,
        query: [URLQueryItem] = [],
        authenticated: Bool,
        allowRefresh: Bool = true
    ) async throws -> T {
        try await send(method: method, path: path, query: query, bodyData: nil, authenticated: authenticated, allowRefresh: allowRefresh)
    }

    private func send<T: Decodable>(
        method: String,
        path: String,
        query: [URLQueryItem],
        bodyData: Data?,
        authenticated: Bool,
        allowRefresh: Bool
    ) async throws -> T {
        if authenticated && accessToken == nil {
            try? await refreshToken()
        }
        do {
            return try await perform(method: method, path: path, query: query, bodyData: bodyData, authenticated: authenticated)
        } catch NativeClientError.http(let status, _) where status == 401 && authenticated && allowRefresh {
            try await refreshToken()
            return try await perform(method: method, path: path, query: query, bodyData: bodyData, authenticated: authenticated)
        }
    }

    private func perform<T: Decodable>(
        method: String,
        path: String,
        query: [URLQueryItem],
        bodyData: Data?,
        authenticated: Bool
    ) async throws -> T {
        var request = URLRequest(url: try makeURL(path: path, query: query))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bodyData {
            request.httpBody = bodyData
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if authenticated, let token = accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let cookie = store.cookie(for: account) {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NativeClientError.http(0, "Missing HTTP response") }
        captureRefreshCookie(from: http)
        guard (200..<300).contains(http.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? HTTPURLResponse.localizedString(forStatusCode: http.statusCode)
            throw NativeClientError.http(http.statusCode, message)
        }
        if T.self == EmptyResponse.self {
            return EmptyResponse() as! T
        }
        return try decoder.decode(T.self, from: data)
    }

    private func captureRefreshCookie(from response: HTTPURLResponse) {
        let header = response.allHeaderFields.first { key, _ in
            (key as? String)?.lowercased() == "set-cookie"
        }?.value as? String
        guard let header, let range = header.range(of: "memos_refresh=") else { return }
        let tail = header[range.lowerBound...]
        let pair = tail.split(separator: ";", maxSplits: 1).first.map(String.init)
        if let pair, !pair.hasSuffix("=") {
            store.setCookie(pair, for: account)
        } else {
            store.setCookie(nil, for: account)
        }
    }

    private func makeURL(path: String, query: [URLQueryItem] = []) throws -> URL {
        guard var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else { throw NativeClientError.invalidURL }
        let basePath = components.percentEncodedPath == "/" ? "" : components.percentEncodedPath
        let encodedPath = path.split(separator: "/").map { "/" + encodePathSegment(String($0)) }.joined()
        components.percentEncodedPath = basePath + encodedPath
        components.queryItems = query.isEmpty ? nil : query
        guard let url = components.url else { throw NativeClientError.invalidURL }
        return url
    }
}

private struct EmptyBody: Encodable {}
private struct EmptyResponse: Decodable {}

final class ViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let store = NativeAccountStore()
    private var api: NativeMemosAPI?
    private var memos: [NativeMemo] = []
    private var usersByUsername: [String: NativeUser] = [:]
    private var nextPageToken = ""
    private var isLoading = false
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let composerView = ComposerView()
    private let floatingPostButton = UIButton(type: .system)
    private let tabBar = UITabBar()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Bema"
        view.backgroundColor = UIColor.black
        configureNavigation()
        configureTable()
        configureFloatingNavigation()
        activateCurrentAccount()
    }

    private func configureNavigation() {
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Accounts", style: .plain, target: self, action: #selector(showAccounts))
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(barButtonSystemItem: .refresh, target: self, action: #selector(refreshTapped)),
            UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(addTapped))
        ]
    }

    private func configureTable() {
        composerView.onPost = { [weak self] content, visibility, attachments in
            Task { await self?.post(content: content, visibility: visibility, attachments: attachments) }
        }
        composerView.translatesAutoresizingMaskIntoConstraints = false
        tableView.translatesAutoresizingMaskIntoConstraints = false
        composerView.isHidden = true
        tableView.dataSource = self
        tableView.delegate = self
        tableView.separatorInset = .zero
        tableView.backgroundColor = .black
        tableView.keyboardDismissMode = .interactive
        tableView.register(MemoTableViewCell.self, forCellReuseIdentifier: MemoTableViewCell.reuseIdentifier)
        view.addSubview(composerView)
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            composerView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            composerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        ])
    }

    private func configureFloatingNavigation() {
        tabBar.translatesAutoresizingMaskIntoConstraints = false
        tabBar.items = [
            UITabBarItem(tabBarSystemItem: .favorites, tag: 0),
            UITabBarItem(tabBarSystemItem: .search, tag: 1),
            UITabBarItem(tabBarSystemItem: .bookmarks, tag: 2),
            UITabBarItem(tabBarSystemItem: .more, tag: 3)
        ]
        tabBar.selectedItem = tabBar.items?.first
        view.addSubview(tabBar)
        NSLayoutConstraint.activate([
            tabBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tabBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tabBar.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        floatingPostButton.translatesAutoresizingMaskIntoConstraints = false
        floatingPostButton.setTitle("+", for: .normal)
        floatingPostButton.titleLabel?.font = .systemFont(ofSize: 32, weight: .medium)
        floatingPostButton.tintColor = .white
        floatingPostButton.backgroundColor = .systemBlue
        floatingPostButton.layer.cornerRadius = 28
        floatingPostButton.addTarget(self, action: #selector(composeTapped), for: .touchUpInside)
        view.addSubview(floatingPostButton)
        NSLayoutConstraint.activate([
            floatingPostButton.widthAnchor.constraint(equalToConstant: 56),
            floatingPostButton.heightAnchor.constraint(equalToConstant: 56),
            floatingPostButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            floatingPostButton.bottomAnchor.constraint(equalTo: tabBar.topAnchor, constant: -18)
        ])
    }

    private func activateCurrentAccount() {
        guard let account = store.activeAccount else {
            composerView.isHidden = true
            presentSignIn(animated: false)
            return
        }
        composerView.isHidden = true
        navigationItem.titleView = SiteTitleView(title: account.siteTitle, logoURL: account.siteLogoUrl)
        api = try? NativeMemosAPI(account: account, store: store)
        Task {
            await loadBranding()
            await loadFirstPage()
        }
    }

    private func loadFirstPage() async {
        guard !isLoading, let api else { return }
        isLoading = true
        do {
            let response = try await api.listMemos()
            memos = response.memos ?? []
            nextPageToken = response.nextPageToken ?? ""
            await loadProfiles(for: memos)
            tableView.reloadData()
        } catch {
            showError(error)
        }
        isLoading = false
    }

    private func loadMoreIfNeeded(row: Int) {
        guard row >= memos.count - 5, !nextPageToken.isEmpty, !isLoading, let api else { return }
        isLoading = true
        Task {
            do {
                let response = try await api.listMemos(pageToken: nextPageToken)
                let newMemos = response.memos ?? []
                memos.append(contentsOf: newMemos)
                nextPageToken = response.nextPageToken ?? ""
                await loadProfiles(for: newMemos)
                tableView.reloadData()
            } catch {
                showError(error)
            }
            isLoading = false
        }
    }

    private func loadBranding() async {
        guard let api, let setting = try? await api.generalSetting() else { return }
        guard let profile = setting.generalSetting?.customProfile else { return }
        guard let account = store.activeAccount else { return }
        let resolvedLogo = profile.logoUrl.flatMap { try? api.assetURL($0) }?.absoluteString ?? ""
        let updated = NativeAccount(id: account.id, instanceUrl: account.instanceUrl, username: account.username, userName: account.userName, displayName: account.displayName, avatarUrl: account.avatarUrl, siteTitle: profile.title ?? "Memos", siteLogoUrl: resolvedLogo)
        store.upsert(updated)
        navigationItem.titleView = SiteTitleView(title: updated.siteTitle, logoURL: updated.siteLogoUrl)
    }

    private func loadProfiles(for memos: [NativeMemo]) async {
        guard let api else { return }
        let names = memos.map { $0.creator.split(separator: "/").last.map(String.init) ?? "" }
        if let users = try? await api.batchGetUsers(names) { users.forEach { usersByUsername[$0.username] = $0 } }
    }

    private func post(content: String, visibility: String, attachments: [PendingNativeAttachment] = []) async {
        guard let api else { return }
        do {
            var uploaded: [NativeAttachment] = []
            for attachment in attachments {
                uploaded.append(try await api.uploadAttachment(attachment))
            }
            let memo = try await api.createMemo(content: content, visibility: visibility, attachments: uploaded)
            composerView.isHidden = true
            floatingPostButton.isHidden = false
            memos.insert(memo, at: 0)
            tableView.reloadData()
        } catch {
            showError(error)
        }
    }

    @objc private func refreshTapped() {
        Task { await loadFirstPage() }
    }

    @objc private func addTapped() {
        presentSignIn(animated: true)
    }

    @objc private func composeTapped() {
        composerView.isHidden = false
        floatingPostButton.isHidden = true
        view.bringSubviewToFront(composerView)
    }

    @objc private func showAccounts() {
        let sheet = UIAlertController(title: "Accounts", message: nil, preferredStyle: .actionSheet)
        store.accounts.forEach { account in
            sheet.addAction(UIAlertAction(title: "\(account.siteTitle) · \(account.visibleName)", style: .default) { [weak self] _ in
                self?.store.select(account)
                self?.memos.removeAll()
                self?.tableView.reloadData()
                self?.activateCurrentAccount()
            })
        }
        sheet.addAction(UIAlertAction(title: "Add account", style: .default) { [weak self] _ in self?.presentSignIn(animated: true) })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        present(sheet, animated: true)
    }

    private func presentSignIn(animated: Bool) {
        let signIn = SignInViewController(store: store)
        signIn.onSignedIn = { [weak self] account in
            self?.store.select(account)
            self?.activateCurrentAccount()
        }
        let navigation = UINavigationController(rootViewController: signIn)
        present(navigation, animated: animated)
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { memos.count }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: MemoTableViewCell.reuseIdentifier, for: indexPath) as! MemoTableViewCell
        let memo = memos[indexPath.row]
        let username = memo.creator.split(separator: "/").last.map(String.init) ?? ""
        cell.configure(memo: memo, user: usersByUsername[username], api: api) { [weak self] reaction in
            Task { await self?.react(to: memo, reaction: reaction) }
        }
        return cell
    }

    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        loadMoreIfNeeded(row: indexPath.row)
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let memo = memos[indexPath.row]
        let detail = MemoDetailViewController(memo: memo, api: api)
        if #available(iOS 18.0, *) {
            let memoName = memo.name
            detail.preferredTransition = .zoom { [weak self] _ in
                guard let self, let row = self.memos.firstIndex(where: { $0.name == memoName }) else { return nil }
                return self.tableView.cellForRow(at: IndexPath(row: row, section: 0))
            }
        }
        navigationController?.pushViewController(detail, animated: true)
    }

    private func react(to memo: NativeMemo, reaction: String) async {
        guard let api else { return }
        do {
            _ = try await api.react(to: memo.name, reaction: reaction)
            if let index = memos.firstIndex(where: { $0.name == memo.name }) {
                let refreshed = try await api.getMemo(memo.name)
                memos[index] = refreshed
                tableView.reloadRows(at: [IndexPath(row: index, section: 0)], with: .none)
            }
        } catch {
            showError(error)
        }
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(title: "Memos", message: error.localizedDescription, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

final class SignInViewController: UIViewController {
    var onSignedIn: ((NativeAccount) -> Void)?
    private let store: NativeAccountStore
    private let instanceField = UITextField()
    private let usernameField = UITextField()
    private let passwordField = UITextField()
    private let button = UIButton(type: .system)

    init(store: NativeAccountStore) {
        self.store = store
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Add Memos Account"
        view.backgroundColor = .systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .cancel, target: self, action: #selector(cancel))
        configureForm()
    }

    private func configureForm() {
        [instanceField, usernameField, passwordField].forEach {
            $0.borderStyle = .roundedRect
            $0.autocapitalizationType = .none
            $0.autocorrectionType = .no
        }
        instanceField.placeholder = "https://memos.example.com"
        usernameField.placeholder = "Username"
        passwordField.placeholder = "Password"
        passwordField.isSecureTextEntry = true
        button.setTitle("Sign in", for: .normal)
        button.addTarget(self, action: #selector(signIn), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [instanceField, usernameField, passwordField, button])
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    @objc private func cancel() { dismiss(animated: true) }

    @objc private func signIn() {
        guard let instance = instanceField.text, let username = usernameField.text, let password = passwordField.text else { return }
        let normalized = normalizeInstanceURL(instance)
        let account = NativeAccount(id: accountId(instance: normalized, username: username), instanceUrl: normalized, username: username, userName: "", displayName: "", avatarUrl: "")
        button.isEnabled = false
        Task {
            do {
                let api = try NativeMemosAPI(account: account, store: store)
                let response = try await api.signIn(username: username, password: password)
                let signedIn = NativeAccount(
                    id: account.id,
                    instanceUrl: normalized,
                    username: username,
                    userName: response.user.name,
                    displayName: response.user.visibleName,
                    avatarUrl: response.user.avatarUrl
                )
                store.upsert(signedIn)
                dismiss(animated: true) { [weak self] in self?.onSignedIn?(signedIn) }
            } catch {
                button.isEnabled = true
                showError(error)
            }
        }
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(title: "Sign in failed", message: error.localizedDescription, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

final class SiteTitleView: UIView {
    private let imageView = UIImageView()
    private let titleLabel = UILabel()
    private var task: Task<Void, Never>?

    init(title: String, logoURL: String) {
        super.init(frame: .zero)
        titleLabel.text = title
        titleLabel.font = .preferredFont(forTextStyle: .headline)
        titleLabel.textColor = .label
        imageView.layer.cornerRadius = 10
        imageView.clipsToBounds = true
        imageView.backgroundColor = .secondarySystemFill
        imageView.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView(arrangedSubviews: [imageView, titleLabel])
        stack.axis = .horizontal
        stack.spacing = 8
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            imageView.widthAnchor.constraint(equalToConstant: 22),
            imageView.heightAnchor.constraint(equalToConstant: 22),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
        if let url = URL(string: logoURL), !logoURL.isEmpty {
            task = Task { [weak self] in
                guard let (data, _) = try? await URLSession.shared.data(from: url), let image = UIImage(data: data) else { return }
                guard !Task.isCancelled else { return }
                self?.imageView.image = image
            }
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class MemoDetailViewController: UIViewController, UITableViewDataSource {
    private var memo: NativeMemo
    private let api: NativeMemosAPI?
    private var comments: [NativeMemo] = []
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let commentField = UITextField()

    init(memo: NativeMemo, api: NativeMemosAPI?) {
        self.memo = memo
        self.api = api
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Memo"
        view.backgroundColor = .systemBackground
        configureViews()
        Task { await loadComments() }
    }

    private func configureViews() {
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.register(MemoTableViewCell.self, forCellReuseIdentifier: MemoTableViewCell.reuseIdentifier)
        let header = MemoHeaderView(memo: memo)
        header.frame.size = header.systemLayoutSizeFitting(CGSize(width: view.bounds.width, height: UIView.layoutFittingCompressedSize.height))
        tableView.tableHeaderView = header

        commentField.borderStyle = .roundedRect
        commentField.placeholder = "Write a reply"
        let send = UIButton(type: .system)
        send.setTitle("Reply", for: .normal)
        send.addTarget(self, action: #selector(sendComment), for: .touchUpInside)
        let bar = UIStackView(arrangedSubviews: [commentField, send])
        bar.axis = .horizontal
        bar.spacing = 10
        bar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        view.addSubview(bar)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: bar.topAnchor, constant: -8),
            bar.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            bar.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            bar.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -8)
        ])
    }

    private func loadComments() async {
        guard let api else { return }
        do {
            comments = try await api.listComments(for: memo.name).memos ?? []
            tableView.reloadData()
        } catch {
            showError(error)
        }
    }

    @objc private func sendComment() {
        let content = commentField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !content.isEmpty, let api else { return }
        commentField.text = ""
        Task {
            do {
                let comment = try await api.createComment(for: memo.name, content: content, visibility: memo.visibility)
                comments.append(comment)
                tableView.reloadData()
            } catch {
                showError(error)
            }
        }
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { comments.count }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: MemoTableViewCell.reuseIdentifier, for: indexPath) as! MemoTableViewCell
        cell.configure(memo: comments[indexPath.row], user: nil, api: api, onReact: nil)
        return cell
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(title: "Memo", message: error.localizedDescription, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

final class ComposerView: UIView, PHPickerViewControllerDelegate {
    var onPost: ((String, String, [PendingNativeAttachment]) -> Void)?
    private let textView = UITextView()
    private let visibility = UISegmentedControl(items: ["Private", "Protected", "Public"])
    private let postButton = UIButton(type: .system)
    private let photoButton = UIButton(type: .system)
    private let previewScroll = UIScrollView()
    private let previewStack = UIStackView()
    private var selectedAttachments: [PendingNativeAttachment] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .systemBackground
        configure()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func configure() {
        textView.font = .preferredFont(forTextStyle: .body)
        textView.layer.borderColor = UIColor.separator.cgColor
        textView.layer.borderWidth = 1
        textView.layer.cornerRadius = 12
        visibility.selectedSegmentIndex = 0
        postButton.setTitle("Post", for: .normal)
        postButton.addTarget(self, action: #selector(post), for: .touchUpInside)
        photoButton.setTitle("Add photos", for: .normal)
        photoButton.addTarget(self, action: #selector(addPhotos), for: .touchUpInside)
        let controls = UIStackView(arrangedSubviews: [photoButton, visibility, postButton])
        controls.axis = .horizontal
        controls.spacing = 12
        previewScroll.showsHorizontalScrollIndicator = false
        previewStack.axis = .horizontal
        previewStack.spacing = 8
        previewStack.translatesAutoresizingMaskIntoConstraints = false
        previewScroll.addSubview(previewStack)
        NSLayoutConstraint.activate([
            previewStack.leadingAnchor.constraint(equalTo: previewScroll.leadingAnchor),
            previewStack.trailingAnchor.constraint(equalTo: previewScroll.trailingAnchor),
            previewStack.topAnchor.constraint(equalTo: previewScroll.topAnchor),
            previewStack.bottomAnchor.constraint(equalTo: previewScroll.bottomAnchor),
            previewStack.heightAnchor.constraint(equalToConstant: 72),
            previewScroll.heightAnchor.constraint(equalToConstant: 72)
        ])
        previewScroll.isHidden = true
        let stack = UIStackView(arrangedSubviews: [textView, previewScroll, controls])
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            textView.heightAnchor.constraint(equalToConstant: 96),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12)
        ])
    }

    @objc private func post() {
        let text = textView.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || !selectedAttachments.isEmpty else { return }
        let value = ["PRIVATE", "PROTECTED", "PUBLIC"][max(visibility.selectedSegmentIndex, 0)]
        let attachments = selectedAttachments
        textView.text = ""
        selectedAttachments.removeAll()
        previewStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        previewScroll.isHidden = true
        onPost?(text, value, attachments)
    }

    @objc private func addPhotos() {
        guard let viewController = parentViewController else { return }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 9
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        viewController.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        selectedAttachments.removeAll()
        for result in results {
            result.itemProvider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
                guard let image = object as? UIImage, let data = image.jpegData(compressionQuality: 0.88) else { return }
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.selectedAttachments.append(PendingNativeAttachment(filename: "photo-\(self.selectedAttachments.count).jpg", data: data, type: "image/jpeg"))
                    let imageView = UIImageView(image: image)
                    imageView.contentMode = .scaleAspectFill
                    imageView.clipsToBounds = true
                    imageView.layer.cornerRadius = 10
                    imageView.translatesAutoresizingMaskIntoConstraints = false
                    imageView.widthAnchor.constraint(equalToConstant: 72).isActive = true
                    imageView.heightAnchor.constraint(equalToConstant: 72).isActive = true
                    self.previewStack.addArrangedSubview(imageView)
                    self.previewScroll.isHidden = false
                }
            }
        }
    }
}

final class MemoTableViewCell: UITableViewCell {
    static let reuseIdentifier = "MemoTableViewCell"
    private let stack = UIStackView()
    private let contentStack = UIStackView()
    private let avatarView = UIImageView()
    private let metaLabel = UILabel()
    private let bodyLabel = UILabel()
    private let tagsLabel = UILabel()
    private let mediaScroll = UIScrollView()
    private let mediaStack = UIStackView()
    private let reactionStack = UIStackView()
    private var imageTasks: [Task<Void, Never>] = []

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .default
        backgroundColor = .black
        contentView.backgroundColor = .black
        configureViews()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func prepareForReuse() {
        super.prepareForReuse()
        imageTasks.forEach { $0.cancel() }
        imageTasks.removeAll()
        avatarView.image = nil
        mediaStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        reactionStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
    }

    private func configureViews() {
        stack.axis = .horizontal
        stack.alignment = .top
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        avatarView.contentMode = .scaleAspectFill
        avatarView.clipsToBounds = true
        avatarView.layer.cornerRadius = 23
        avatarView.backgroundColor = UIColor(white: 0.15, alpha: 1)
        avatarView.translatesAutoresizingMaskIntoConstraints = false
        avatarView.widthAnchor.constraint(equalToConstant: 46).isActive = true
        avatarView.heightAnchor.constraint(equalToConstant: 46).isActive = true
        contentStack.axis = .vertical
        contentStack.spacing = 8
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        metaLabel.font = .preferredFont(forTextStyle: .subheadline)
        metaLabel.textColor = .secondaryLabel
        bodyLabel.font = .preferredFont(forTextStyle: .body)
        bodyLabel.numberOfLines = 0
        tagsLabel.font = .preferredFont(forTextStyle: .caption1)
        tagsLabel.textColor = .systemBlue
        tagsLabel.numberOfLines = 0
        mediaScroll.showsHorizontalScrollIndicator = false
        mediaScroll.translatesAutoresizingMaskIntoConstraints = false
        mediaStack.axis = .horizontal
        mediaStack.spacing = 8
        mediaStack.translatesAutoresizingMaskIntoConstraints = false
        mediaScroll.addSubview(mediaStack)
        NSLayoutConstraint.activate([
            mediaStack.topAnchor.constraint(equalTo: mediaScroll.topAnchor),
            mediaStack.leadingAnchor.constraint(equalTo: mediaScroll.leadingAnchor),
            mediaStack.trailingAnchor.constraint(equalTo: mediaScroll.trailingAnchor),
            mediaStack.bottomAnchor.constraint(equalTo: mediaScroll.bottomAnchor),
            mediaStack.heightAnchor.constraint(equalToConstant: 210),
            mediaScroll.heightAnchor.constraint(equalToConstant: 210)
        ])
        reactionStack.axis = .horizontal
        reactionStack.distribution = .equalSpacing
        [avatarView, contentStack].forEach(stack.addArrangedSubview)
        contentStack.addArrangedSubview(metaLabel)
        contentStack.addArrangedSubview(bodyLabel)
        contentStack.addArrangedSubview(tagsLabel)
        contentStack.addArrangedSubview(mediaScroll)
        contentStack.addArrangedSubview(reactionStack)
        contentView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -14),
            contentStack.trailingAnchor.constraint(equalTo: stack.trailingAnchor)
        ])
    }

    func configure(memo: NativeMemo, user: NativeUser?, api: NativeMemosAPI?, onReact: ((String) -> Void)?) {
        let visibleName = user?.visibleName ?? (memo.creator.isEmpty ? "Memos" : memo.creator.split(separator: "/").last.map(String.init) ?? "Memos")
        metaLabel.text = "\(visibleName)  @\(user?.username ?? "memos")  · \(formatTime(memo.createTime))"
        bodyLabel.text = memo.content
        tagsLabel.text = memo.tags.map { "#\($0)" }.joined(separator: "  ")
        tagsLabel.isHidden = memo.tags.isEmpty
        reactionStack.isHidden = onReact == nil
        let counts = Dictionary(grouping: memo.reactions, by: { $0.reactionType }).mapValues(\.count)
        ["\u{1F44D}", "\u{2764}\u{FE0F}", "\u{1F604}", "\u{1F680}"].forEach { reaction in
            let button = UIButton(type: .system)
            button.setTitle(reaction + (counts[reaction].map { " \($0)" } ?? ""), for: .normal)
            if let onReact {
                button.addAction(UIAction { _ in onReact(reaction) }, for: .touchUpInside)
            }
            reactionStack.addArrangedSubview(button)
        }
        mediaScroll.isHidden = memo.attachments.filter { $0.isImage }.isEmpty
        memo.attachments.filter { $0.isImage }.forEach { attachment in
            let imageView = UIImageView()
            imageView.contentMode = .scaleAspectFill
            imageView.clipsToBounds = true
            imageView.layer.cornerRadius = 14
            imageView.backgroundColor = UIColor(white: 0.12, alpha: 1)
            imageView.translatesAutoresizingMaskIntoConstraints = false
            imageView.widthAnchor.constraint(equalToConstant: 260).isActive = true
            imageView.heightAnchor.constraint(equalToConstant: 210).isActive = true
            mediaStack.addArrangedSubview(imageView)
            if let api {
                imageTasks.append(Task { [weak imageView] in
                    let image = await api.loadAttachmentImage(attachment)
                    guard !Task.isCancelled else { return }
                    imageView?.image = image
                })
            }
        }
        if let user, let api {
            imageTasks.append(Task { [weak self] in
                let image = await api.loadUserAvatar(user)
                guard !Task.isCancelled else { return }
                self?.avatarView.image = image
            })
        }
    }
}

final class MemoHeaderView: UIView {
    init(memo: NativeMemo) {
        super.init(frame: .zero)
        let label = UILabel()
        label.numberOfLines = 0
        label.font = .preferredFont(forTextStyle: .body)
        label.text = memo.content
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            label.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            label.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

private func normalizeInstanceURL(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") { return trimmed }
    return "https://\(trimmed)"
}

private func accountId(instance: String, username: String) -> String {
    let raw = "\(normalizeInstanceURL(instance)):\(username.lowercased())"
    let digest = SHA256.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
    return String(digest.prefix(24)) + "-" + username.lowercased().replacingOccurrences(of: "[^a-z0-9._-]", with: "_", options: .regularExpression)
}

private func shortHost(_ value: String) -> String {
    URL(string: value)?.host ?? value
}

private func formatTime(_ value: String?) -> String {
    guard let value else { return "" }
    return value.replacingOccurrences(of: "T", with: " ").components(separatedBy: ".").first?.replacingOccurrences(of: "Z", with: "") ?? value
}

private extension UIView {
    var parentViewController: UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController { return viewController }
            responder = current.next
        }
        return nil
    }
}

private func encodePathSegment(_ value: String) -> String {
    var allowed = CharacterSet.urlPathAllowed
    allowed.remove(charactersIn: "/")
    return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
}
