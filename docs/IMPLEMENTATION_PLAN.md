# Implementation sequence

1. Create native Android/Compose project with production and local-emulator builds.
2. Add Firebase Auth, owner-controlled Circle membership in each database, live map and consent controls.
3. Add balanced-power foreground tracking, per-account/device 60s/300s settings and bounded idempotent outbox.
4. Add calendar-day history, map routes, distance, deletion and server-enforced history visibility.
5. Exercise security rules in both database emulators, Auth emulator, domain/storage tests, Android compilation and lint.
6. Document production provisioning, partial membership failures, costs and real-phone acceptance checks.

Owner decisions: no existing Firebase project; Russian default; Family Tracker / com.familytracker.app; stopping sharing also hides history from peers. Re-enabling sharing reveals retained history again. No paid backend, Cloud Functions or analytics.
