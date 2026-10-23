# Testing

## Automated commands

`./gradlew :app:assembleEmulatorDebug :app:testEmulatorDebugUnitTest :app:lintEmulatorDebug`

- Domain: supported/default intervals, coordinate and timestamp validation, stale-location classification, distance including date-line crossing, DST day boundaries, and explicit service-start prerequisites.
- Robolectric: DataStore account isolation and persistence; SQLite outbox deduplication, original timestamps, bounded eviction, and persistent stop tombstones.
- Android lint and APK compilation check resources, Compose, Firebase/MapLibre integration and manifest configuration. They do not establish real-device background reliability.

`cd firebase && npm ci && npm test`

Runs Auth, Firestore and RTDB emulators with `demo-family-tracker`, then Node's test runner. Tests exercise registration/profile/sign-in/sign-out/password reset, denial of unauthenticated and cross-Circle access, pending requests, self-approval, forged identities, owner boundaries, invalid payloads, immutable/idempotent history, daily queries, history hiding, member departure/revocation and partial approval. Expected permission denials appear in emulator logs; only the final test summary determines success. No deployed Firebase project is touched.

## Required real-phone acceptance before release

Use two phones with distinct accounts and a freshly configured production test Firebase project. These checks cannot be established by JVM builds or backend emulators alone.

1. Register/sign in, restart the Activity and confirm authentication persists. Reset a password. Sign out and sign in as a different account; old maps/history must disappear.
2. Create a Circle, share its code, request entry and approve. Before approval no private data is readable. Reject another request. Remove a member while their map/history is open and verify access closes.
3. Enable sharing deliberately. Test approximate and precise permissions, denied location, and Android 13+ denied notifications. Check persistent notification and its Stop action. No collection before explicit Start.
4. Observe locations on the peer phone, names, accuracy/battery, fresh/stale status and timestamps. Select/center members, self-center, pinch zoom, refresh and attribution links.
5. Leave the app, lock the screen for 10+ minutes, inspect acquisition timestamps. Change 1→5→1 minute while active and after app restart; inspect actual updates. Repeat on Android 12, 13, 14 and 16 and at least one vendor with aggressive battery rules. Exact timing is not guaranteed.
6. Enable airplane mode while active. Check local points accumulate. Reconnect and verify one history document per UUID with original acquisition time. Stop while offline and verify disclosure, callback cleanup and eventual live deletion/history hiding after reconnect.
7. Browse today/yesterday and DST dates in different phone timezones. Verify endpoints, route point tap timestamps and approximate distance. Stop sharing; the peer's history screen must clear. Restart sharing; retained history should become visible again.
8. Delete your history while sharing; collection must stop first, pending points must not reappear, and only your records are removed. Try deletion again after interrupted connectivity.
9. Force-stop/reboot: no hidden restart; reopen and explicitly enable. Test disabled system location, network switching and low battery.
10. Interrupt a membership change between database writes. Check the visible pending operation and retry. Verify both database membership records after retry. Test device/app loss separately using the documented manual recovery.

## Results

Actual build/test results and remaining environment restrictions are recorded in `VALIDATION.md` after execution. Do not interpret this checklist as executed device testing.
