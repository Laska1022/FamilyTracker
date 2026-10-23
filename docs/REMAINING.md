# Remaining release work

- Create/configure the owner's Firebase project and deploy verified rules/indexes; provide local google-services.json. No production project exists yet.
- Complete emulator security tests once storage.googleapis.com is allowed in the cloud environment. Do not treat authored tests as passed.
- Run the two-phone acceptance matrix in TESTING.md, especially locked-screen Android behavior, real permissions, map rendering and offline recovery. No physical phones/Android runtime are attached to this task.
- Configure release signing outside Git, distribute a signed APK, and review applicable Android distribution/privacy requirements.
- Possible later improvements: email verification/App Check, multi-Circle selection, adaptive intervals, configurable retention and circle migration. These are not implemented and are not needed to run the first end-to-end flow.

The architecture intentionally has no server-side atomic cross-database membership operation. Strict simultaneous revocation would require revisiting that product constraint. Pending membership retries and explicit offline disclosure are implemented, but cannot remove already downloaded data or guarantee delivery from a force-stopped/uninstalled app.
