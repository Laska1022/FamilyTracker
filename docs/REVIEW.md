# Review — 2026-10-24

## Fixed

| Finding | Impact and correction |
|---|---|
| History consent survived removal/re-approval | High: a former member's old history could become visible without fresh consent. Membership changes now batch-reset Firestore sharing to false; rules reject recreating membership with stale enabled consent. Owners may revoke but cannot grant another person's consent. |
| Late callbacks and asynchronous stop/start races | High: stale work could enqueue points after stopping, or an old stop could hide a new session. Device-local consent generations reject old callbacks, and consent writes/tombstones are serialized separately from history uploads. |
| Account and Circle streams combined independently | High: a new account could temporarily inherit the preceding Circle selection. Account-scoped selection clears immediately before loading the new account's preference. |
| Map features have different schemas | Medium: member/route clicks could request absent or null properties. Typed selection handles both schemas and invalid properties safely. |
| Strict second interval filter | Medium: jitter or delayed batches were discarded even when Fused Location Provider obeyed the requested interval. The app now rejects pre-consent/duplicate fixes and lets the provider control acquisition frequency. |
| History/loading/retry lifecycle | Medium: unnecessary history refetches, stale loading indicators, or concurrent Start during destructive actions. Distinct visibility, request generation guards, bounded retry waits and busy-state gating correct these. |
| Production Firebase configuration affected emulator builds | Medium: adding a production google-services.json could break emulator package matching. Emulator Google Services processing is disabled; its demo Firebase initialization remains independent. |
| Local RTDB emulator test connection used the external proxy | Test blocker: Firebase Node WebSocket code ignored NO_PROXY. The test child verifies all emulator endpoints are loopback before clearing proxy settings; artifact downloads retain proxy and TLS/checksum verification. |
| Publication had no repeatable path | Tags matching v*-preview.* now build both APKs, run Android and Firebase checks, and publish a prerelease with checksums only after both jobs succeed. |

## Remaining release prerequisites

Provision production Firebase and test on two actual phones, including Android background restrictions, offline stop/reconnect, notifications and map rendering. Establish a stable private release-signing key before distributing production builds. Strict atomic cross-database revocation still needs a different backend consistency model; the current free-plan retry contract remains explicit.

See VALIDATION.md for executed checks. These prerequisites are not represented as completed device or production testing.
