# Family Tracker 0.1.1 — development preview

Review fixes close several privacy and reliability gaps: stale callbacks cannot save points after Stop or into a later sharing session; account changes clear Circle selection; re-approving a removed member does not restore previous history-sharing consent; and map selection safely handles different marker schemas. Location callbacks preserve legitimate delayed fixes and normal timing jitter. Membership retries, map loading/lifecycle, and history loading are more robust.

Both Android debug variants build. Regression coverage includes consent invalidation, account isolation and map selection, plus the full Firebase Auth/Realtime Database/Firestore security suite. Publication is gated on successful Android tests/lint and Firebase emulator tests in GitHub Actions.

## APK downloads

- **emulator-debug.apk**: functional development build targeting Firebase emulators at `10.0.2.2`. Start the emulator backend as described in [Firebase setup](docs/FIREBASE_SETUP.md). It cannot connect to a live family backend.
- **production-config-required-debug.apk**: installable development build for `com.familytracker.app`. This release contains no production Firebase configuration, so it displays setup instructions rather than login/tracking. Create Firebase, deploy verified rules and indexes, add your own `google-services.json`, then rebuild to connect real phones.
- **SHA256SUMS**: checksums of both APK assets.

These are debug-signed prerelease APKs, not production-signed releases. A future build may use a different debug certificate and require uninstalling the old development build before installation. Preserve needed data and allow pending points/revocations to synchronize before uninstalling. No production signing key or backend credentials are committed.

No real-device acceptance test has run. Android update timing and survival under vendor battery restrictions are not guaranteed. Offline revocation can be delayed, and membership revocation across the two databases is retried rather than atomic. Review [architecture](docs/ARCHITECTURE.md) and the [two-phone checklist](docs/TESTING.md) before sharing private live locations.
