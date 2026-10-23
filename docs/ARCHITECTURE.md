# Architecture and security

Kotlin, Compose/Material 3, ViewModel + StateFlow, repositories, Firebase Android main modules, DataStore. MapLibre native MapView is hosted in Compose and uses OpenFreeMap's Liberty style. Google Play services is required on phones. Android 8 (API 26) minimum; compile/target API 36; Java 17 bytecode, Java 21 build runtime.

## Data ownership

- Firebase Auth owns email/password identities and display names.
- A Circle ID is its owner's random Firebase UID. This is an invitation identifier, not a password. Knowledge of it only permits submitting a request. It never authorizes reading location data.
- Firestore `circles/{ownerUid}` stores name and immutable ownership. `members/{uid}` stores display names, and `sharing/{uid}` stores explicit history visibility.
- Realtime Database `circles/{ownerUid}` stores name, members, pending requests and `locations/{uid}`. Its membership is its own authorization source; it cannot consult Firestore.
- Firestore `circles/{circle}/history/{uid}/points/{uuid}` stores immutable coordinates and original acquisition time plus server upload timestamp. Replaying the same UUID only updates the server upload timestamp, never creates another point.
- The UI chooses one Circle per account/device; the paths support future multiple Circles. Owners must remove the other members before leaving. Empty owner metadata is retained for recreating that owner's Circle.

Only the account whose UID equals the Circle ID may add members in either database. A member may delete their own membership. Neither a request nor a client-edited profile can self-approve. Live reads are per authorized member, never a root location listener. Coordinates, timestamps, identities, allowed fields and intervals are validated on the server. Owner status does not grant permission to write another person's location, change their sharing flag or delete their history.

## Two-database consistency

There is no cross-database transaction on Spark. Owner operations first update Firestore, then atomically update RTDB membership, request and live-record removal. A local durable operation journal precedes each operation. Pending operations are visible in Circle management, retried when that account reconnects and can be retried explicitly. UI membership requires both records.

A partially completed approval can grant history access in Firestore before RTDB grants live access. A partially completed revocation immediately denies Firestore access but may leave RTDB access until the second write succeeds. This is an explicit limitation, not atomic revocation. The owner must keep/reopen the app and complete the pending operation. A lost device/uninstalled app can lose the journal: verify/remove membership manually in both consoles. Strict simultaneous revocation requires a trusted backend or a single authorization/data store; the current free-plan design does not claim that guarantee.

## Consent and history visibility

Sharing is off by default and never starts from boot or a background receiver. Starting requires a visible Activity, explicit disclosure, runtime location permission, approved membership and network acknowledgement of history visibility. Approximate location is supported. An Android location foreground service started while visible can continue after the Activity is backgrounded without requesting ACCESS_BACKGROUND_LOCATION. No WorkManager periodic polling, wake locks or hidden tracking are used.

The persistent notification includes Stop. Every stop removes location callbacks immediately, writes a durable stop tombstone, hides Firestore history and deletes the live point. RTDB onDisconnect removal additionally clears live location on disconnection. Offline revocation is necessarily delayed: previously downloaded information cannot be recalled, and the server may continue permitting old history until the stop reaches it. The UI says this explicitly. Online sign-out waits for server acknowledgement; failed/offline sign-out retains the account so revocation can be retried. A new process proactively hides sharing for its selected Circle. Unexpected process death while offline cannot instantly revoke Firestore visibility; reopening the app reconciles it. No guaranteed background restart is claimed.

Other approved members can read history only when the target is still a member and their Firestore sharing flag is true. The history UI observes this flag and membership, clearing displayed routes upon revocation. Owners can always read/delete their own historical records, even after membership removal. Turning sharing back on exposes retained history again. Previously viewed/saved screenshots or modified clients cannot be remotely erased.

## Tracking and offline data

FusedLocationProvider requests balanced-power updates every 60s or 300s. Interval changes replace the callback while running. The original device timestamp is used; old initial fixes and too-frequent callbacks are discarded. Freshness uses acquisition time (at most max(3 minutes, twice the requested interval)), not reconnect time. Android may delay or stop updates, especially force-stop/reboot, disabled location, vendor battery rules or lost network.

A private SQLite outbox persists a maximum of 10,000 history points across accounts/Circles (about 6.9 days for a single device at one minute). Oldest unsent records are evicted when full. UUIDs deduplicate retries. Network reconnection and new points trigger synchronization with bounded waits, no polling loop. Removed-member writes are discarded rather than retried forever. Uploads continue after stop, but stopped users' history remains private. Sign-out deletes unsent local points after server revocation. Backups are disabled; no analytics SDK or credentials in the queue. Firestore uses memory cache; RTDB disk persistence is disabled to avoid durable cross-account location caches.

History queries use one user subcollection, `deviceTime >= start && deviceTime < nextStart`, ordered by acquisition time, paginated in batches of 500. Date boundaries use the viewing phone's timezone and honor DST. Straight-line haversine distance between recorded points is approximate, not road distance. Deletion batches at most 400 documents and first stops sharing and clears that Circle's outbox. Retention is manual; 30 days is a capacity target, not an automatic expiration policy.
