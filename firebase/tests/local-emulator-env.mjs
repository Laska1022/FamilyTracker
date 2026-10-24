// The Firebase Node RTDB SDK's WebSocket transport ignores NO_PROXY. Keep the
// platform proxy for CLI artifact downloads, but bypass it for this local-only
// test process after verifying the emulator destinations supplied by the CLI.
for (const name of ['FIREBASE_AUTH_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST', 'FIREBASE_DATABASE_EMULATOR_HOST']) {
  const endpoint = process.env[name];
  if (!endpoint || !/^(127\.0\.0\.1|localhost):\d+$/.test(endpoint)) {
    throw new Error(`Tests require a loopback ${name}; received ${endpoint ?? '(unset)'}`);
  }
}
for (const name of ['HTTP_PROXY', 'http_proxy', 'HTTPS_PROXY', 'https_proxy']) delete process.env[name];
