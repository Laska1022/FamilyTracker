# Production Firebase setup

No Firebase project or billing account is provisioned by this repository. Local emulator builds work without production credentials. Keep test and production projects separate.

1. In Firebase Console create a project (disable Google Analytics). Spark is sufficient to start; do not enable paid services or deploy Cloud Functions.
2. Register Android package **com.familytracker.app**. Download `google-services.json` into `app/google-services.json`; Git ignores it. It contains public client configuration, not an admin credential, but is intentionally kept deployment-specific. Never upload service-account keys or signing keystores.
3. Enable Authentication → Email/Password. Configure a password policy and email templates (Russian recommended). Consider email verification and App Check as a later abuse-hardening step; neither replaces the rules.
4. Create Cloud Firestore in Native mode and Realtime Database in **locked mode**. Choose a region appropriate for the family before creating them; database locations cannot simply be changed later. No default country/region has been assumed.
5. Download a fresh `google-services.json` after creating Realtime Database. Confirm it contains the `firebase_url` for the actual database (regional database hostnames are normal). The Android app uses this default database URL. Do not invent keys or database URLs.
6. Install Node 22+ and Java 21, run `cd firebase && npm ci`. Use Firebase CLI authentication locally (`npx firebase login`). You do not need admin/service-account credentials in the Android application.
7. Review the dual-membership and delayed-offline-revocation contract in ARCHITECTURE.md. Test rules first with `npm test`.
8. Deploy to your explicit project ID: `npx firebase deploy --project YOUR_PROJECT_ID --only database,firestore:rules,firestore:indexes`. Never select a production project for emulator tests. The repository does not auto-deploy.
9. No paid map API key is required. `MAP_STYLE_URL` in `app/build.gradle.kts` uses `https://tiles.openfreemap.org/styles/liberty`. OpenFreeMap is an OSM-based public map provider: https://openfreemap.org/ . Keep MapLibre's on-map attribution/links enabled, including OpenStreetMap contributors and style/provider attribution. Review provider terms before wide distribution; the public service has no availability SLA. Ordinary tile-server requests disclose requested map areas/network address to the provider; no Firebase credentials are sent to it. Android OS location services also involve Google Play services.
10. With SDK 36 installed, run `./gradlew :app:assembleProductionDebug`. Output: `app/build/outputs/apk/production/debug/app-production-debug.apk`. The build can compile without config but will show a configuration-required screen; it cannot connect to production until step 2/5 is done.
11. Install on two Play-services Android phones: `adb install -r app/build/outputs/apk/production/debug/app-production-debug.apk`. For distribution create a properly signed release with your own keystore kept outside Git. Debug APKs are for development and private acceptance tests.
12. Register separate accounts. Owner creates a Circle and sends its code via Android's share sheet. Other user requests entry; owner approves. Both manually start sharing, granting location while using the app and notifications. Check map and history, then stop and verify history becomes inaccessible to the peer.

## Local emulator workflow

`cd firebase && npm ci && npm run emulators` starts Auth (9099), RTDB (9000), Firestore (8080), bound to localhost. In another shell `./gradlew :app:assembleEmulatorDebug`. The emulator flavor uses `demo-family-tracker` and no production resources. Android Emulator reaches the host through `10.0.2.2` by default. For a USB-connected phone run `adb reverse tcp:9099 tcp:9099`, and likewise for 9000 and 8080; build with `-PemulatorHost=127.0.0.1`. Do not expose unauthenticated Firebase emulators to the public Internet. Cleartext is allowed only in the emulator flavor.

## Cloud settings

Build dependencies need standard Google/Maven/Gradle/GitHub/npm package hosts. Emulator binaries additionally require `storage.googleapis.com`; CLI metadata uses `firebase-public.firebaseio.com`; map checks use `tiles.openfreemap.org`. Production authentication/network access on real phones is separate from the Codex build environment. No production secret is needed to compile or run the local tests.
