# Family Tracker

Private, consent-based Android location sharing for a small family or trusted Circle. Native Kotlin, Jetpack Compose Material 3, Russian UI, Firebase Auth/Realtime Database/Firestore, and MapLibre/OpenFreeMap. Android 8+, Google Play services required. No analytics, advertising, custom backend or paid map key.

Implemented paths: email registration/sign-in/reset; owner-approved Circle requests and invitations; live map/member details; visible location foreground service; per-device/account 1- or 5-minute interval (default one minute); bounded offline history outbox; date-based routes and distance; history deletion; and server-enforced history visibility. Sharing starts only with explicit consent. Stopping sharing hides history from peers when the revocation reaches Firebase; the user retains their own history.

## Downloads

Development APKs and checksums are published under [GitHub Releases](https://github.com/Laska1022/FamilyTracker/releases). Read the release notes: the emulator APK requires a local backend, and the production-config-required APK shows setup instructions until rebuilt with your Firebase configuration.

## Build and test

Use Java **21 JDK**, Android SDK **36**, and Node **22+**.

```sh
./gradlew :app:assembleEmulatorDebug :app:testEmulatorDebugUnitTest :app:lintEmulatorDebug
cd firebase
npm ci
npm test
```

Local tests use the Firebase Emulator Suite and a `demo-` project, without production credentials. To run the app against emulators: `cd firebase && npm run emulators`, then install `app/build/outputs/apk/emulator/debug/app-emulator-debug.apk` on an Android emulator. Its backend host is `10.0.2.2`; USB phone instructions are in [Firebase setup](docs/FIREBASE_SETUP.md).

Production requires a Firebase project and `app/google-services.json`. Follow [Firebase setup](docs/FIREBASE_SETUP.md), deploy both rule sets and indexes to your explicitly chosen project, then `./gradlew :app:assembleProductionDebug`. Never deploy development emulators or debug configuration as public services.

## Documentation

- [Architecture, privacy and consistency](docs/ARCHITECTURE.md)
- [Firebase provisioning and installation](docs/FIREBASE_SETUP.md)
- [Test commands and real-phone acceptance checks](docs/TESTING.md)
- [Costs and 30-day storage capacity](docs/COSTS.md)
- [Implementation sequence and owner decisions](docs/IMPLEMENTATION_PLAN.md)

Android does not guarantee exact intervals or survival of force-stop, reboot or vendor battery restrictions. Reopen the app and explicitly restart sharing after process loss. Revocation across two databases is retried but not atomic; see the documented partial-failure limitation. No automatic retention deletion is claimed.
