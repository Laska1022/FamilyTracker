# Family Tracker
Native Android, Kotlin, Compose, Firebase. Default UI language is Russian; keep visible text in string resources. Consent is mandatory; sharing defaults off. Never add hidden tracking or analytics.

Use the existing checkout: each cloud task is isolated, so do not create worktrees unless requested. Read docs/ARCHITECTURE.md before changing membership rules. Realtime Database and Firestore each independently authorize membership. Preserve restrictive validation and test denial paths in both emulators.

Checks: `./gradlew :app:assembleEmulatorDebug :app:testEmulatorDebugUnitTest :app:lintEmulatorDebug`; `cd firebase && npm ci && npm test`. Android SDK 36 and Java 21 are used in cloud. No production credentials are needed for these checks. Never commit google-services.json, keys, keystores or emulator data. Do not deploy Firebase rules without a selected owner-controlled project.
