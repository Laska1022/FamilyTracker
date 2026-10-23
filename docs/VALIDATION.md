# Execution results — 2026-10-24 (Europe/Istanbul)

Executed in the Codex cloud workspace with Temurin JDK 21.0.13, Android SDK/build-tools 36, Gradle 8.13 and Node 24.19.0. No production Firebase configuration or Android device was supplied.

| Check | Actual result |
|---|---|
| `:app:assembleEmulatorDebug` | Passed; APK generated |
| `:app:assembleProductionDebug` | Passed; APK generated, but production config is absent and startup intentionally shows setup instructions |
| `:app:testEmulatorDebugUnitTest` | 8 tests passed, 0 failed, 0 skipped: 6 domain + 2 Robolectric storage tests |
| `:app:lintEmulatorDebug` | Passed with 0 errors and 8 nonblocking warnings (dependency updates and Kotlin convenience APIs) |
| Firebase Auth emulator integration | 1 test passed, 0 failed: registration, profile, sign-out/sign-in, auth events and password reset |
| Full `firebase/npm test` | Blocked before database tests ran: emulator binary download from storage.googleapis.com received HTTP 403 “Domain forbidden” |
| Firestore/RTDB security suite | 13 tests authored; NOT EXECUTED because the emulator download is blocked |
| `scripts/cloud-install.sh` | Executed successfully: reusable SDK/JDK/cache activation, frozen npm install, Android build/tests/lint |
| Real Android runtime / two-phone acceptance | Not executed; no emulator/device attached |
| Production Firebase deployment | Not attempted; owner has no Firebase project yet |

The final Gradle verification built both debug variants and reran unit tests/lint after the last code changes. A build verifies compilation and packaging, not real-phone permission flows, background tracking reliability or rendered map behavior.

Artifacts (generated and Git-ignored):

- `app/build/outputs/apk/emulator/debug/app-emulator-debug.apk`
- `app/build/outputs/apk/production/debug/app-production-debug.apk`
- `app/build/reports/tests/testEmulatorDebugUnitTest/index.html`
- `app/build/reports/lint-results-emulatorDebug.html`

Network domain additions were saved to the cloud environment draft, but saving a draft alone does not apply or publish it. Review/save network settings to permit storage.googleapis.com (emulator downloads), firebase-public.firebaseio.com (CLI metadata) and tiles.openfreemap.org (map checks). Then rerun `cd firebase && npm test`. Do not deploy the rules or treat the app as release-ready until the security suite and two-phone checklist pass.

GitHub Actions is configured to run the same Android and complete emulator checks on pushes/PRs. A configured workflow is not evidence that a CI run succeeded.
