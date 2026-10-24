# Execution results — review on 2026-10-24 (Europe/Istanbul)

Executed in the Codex cloud workspace with Temurin JDK 21.0.13, SDK/build-tools 36, Gradle 8.13 and Node 24.19.0.

| Check | Actual result |
|---|---|
| Emulator and production debug APK builds | Passed, version 0.1.1 / code 2 |
| Android/JVM regression suite | 15 passed, 0 failed/skipped: 6 domain, 2 storage, 5 consent/fix-timing, 2 UI-flow/map-selection |
| Android lint | Passed, 0 errors; nonblocking dependency/API convenience warnings remain |
| Complete Firebase Emulator Suite | 18 passed, 0 failed/skipped: 17 security tests plus Auth integration |
| Production config + emulator build isolation | Passed with a temporary demo-only production config: production Google Services processing executed; emulator processing skipped. Fixture removed before final APK rebuild. |
| Real-phone / Android runtime acceptance | Not executed: no Android runtime/device attached |
| Production Firebase deployment | Not performed: owner configuration is still absent |

Commands executed:

```sh
source scripts/cloud-env.sh
./gradlew :app:assembleEmulatorDebug :app:assembleProductionDebug :app:testEmulatorDebugUnitTest :app:lintEmulatorDebug
cd firebase
npm test
```

The prior emulator download restriction is resolved. Auth, Firestore and RTDB emulators executed the security tests, including unauthenticated/cross-Circle denial, forged identities/timestamps, ownership, join/leave, revocation, daily history queries, stopped-sharing privacy and re-approval consent reset. Expected permission-denied logs are assertions, not failed tests.

The Firebase Node WebSocket client ignored NO_PROXY; a loopback-validated test-only launcher removes proxy variables in the test child. Emulator downloads retain normal proxy/TLS/artifact verification. No production endpoint or credential is used.

Generated artifacts are Git-ignored under `app/build/outputs/apk/{emulator,production}/debug/`. Both are debug-signed. The production artifact has no Firebase configuration and intentionally shows setup instructions. Firebase setup and real-phone acceptance remain necessary before actual family use.

A tagged GitHub Actions workflow independently checks Android and Firebase before publishing a development preview. The public run/release pages are the source for remote CI/publication status; local results alone do not prove publication succeeded.
