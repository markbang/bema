# bema

A Memos client: shared business logic, native UI per platform. Build, preview
and release details live in [README.md](./README.md).

## One state, two UIs

`app/sharedLogic` holds the business logic. Both apps render the *same*
`MemosTimelineController`:

- `app/androidApp` — Jetpack Compose
- `app/iosApp` — UIKit

Accounts, timeline, search, likes, caching, visibility and errors all come from
that controller. When a screen needs something it does not expose, the value
goes into `sharedLogic` so both platforms read one implementation — that is what
keeps the two UIs from drifting apart.

Kotlin lives with the platform it serves: `src/commonMain` for shared code,
`src/iosMain` for Swift-facing declarations, `src/androidMain` for Android-facing
ones.

iOS leans on UIKit's own controls wherever they fit, the way Android leans on
Material; hand-roll only what the shared design actually needs — the dark
chrome, the markdown editor, the media rail.

`app/iosApp/iosApp/Design/MiuixIcons.swift` is generated from the miuix icon
sources by `tools/miuix_icons.py`; change the generator and re-run it.

## The Kotlin/Native edge

`app/iosApp` links `SharedLogic` as a framework. Three facts about that boundary:

- A `suspend` function a client can trip needs `@Throws(Exception::class)`.
  Kotlin/Native terminates the process when a non-`CancellationException`
  reaches Swift from an unannotated function; `MemosUiController` carries the
  annotations for this reason.
- Swift cannot collect a `StateFlow`. `IosInterop.observeState` in `src/iosMain`
  wraps it in a callback and returns a cancellable handle; it also converts
  between `ByteArray` and `NSData`.
- Kotlin enum entries import lowercased, and one that collides with a Swift
  keyword gains a trailing underscore: `Visibility.PRIVATE` is
  `Visibility.private_`. The generated header in the framework is the
  authority; the iOS CI job prints it when the build fails.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
release-please turns them into version bumps and CHANGELOG entries.
