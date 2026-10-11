# VirtualPhone on Android 1.x, 2.x, and 3.x

This is **experimental compatibility**, not a promise that every device will connect.

## What this build changes

- Android 1: API 1–3, package flavor `android1`
- Android 2: API 4–10, package flavor `android2`
- Android 3: API 11–13, package flavor `android3`

For API levels below 16, VirtualPhone offers TLS 1.0 so it can communicate with a compatible *transparent local TLS proxy*. The app still checks the certificate chain presented to it and verifies the host name. It does not turn off those checks.

Without a proxy, the modern Cloudflare Quick Tunnel is expected to reject TLS 1.0. Building an APK does not prove the network will work on the actual old phone.

## About Wolfius

Wolfius is a separate third-party project, not bundled into VirtualPhone:

https://github.com/gohoski/Wolfius

The project README states its intended range is Android 1.5–4.4 and describes the proxy as early/unstable. Android 1.0 and 1.1 (API 1–2) are outside that stated range.

**Security warning:** the current Wolfius source sets `wolfSSL_CTX_set_verify(ctx, WOLFSSL_VERIFY_NONE, 0)` for its upstream TLS client context ([source line](https://github.com/gohoski/Wolfius/blob/main/app/src/main/jni/wolfssl_bridge.c#L371)). That disables verification of the remote server certificate in that context. As a result, the app-side certificate check alone cannot protect a connection carried through that proxy from an upstream impersonation attack. Do not use that upstream build with real accounts, private messages, passwords, or other sensitive data unless this behavior is fixed and independently verified.

VirtualPhone does not embed, fork, or silently install Wolfius. Its legacy TLS setting only makes the app-side handshake compatible with a configured local proxy; it does not repair the proxy's upstream TLS validation.

## Testing checklist

1. Use a dedicated test User ID and non-sensitive test messages.
2. Confirm the local TLS proxy is installed, enabled, and transparently capturing the device's outbound TLS traffic.
3. Keep the VirtualPhone server address editable in Settings. Cloudflare Quick Tunnel addresses are temporary and can change.
4. Test connecting, messaging, and voice on the physical device. An APK that installs successfully may still fail because of device-specific TLS roots, CPU/ABI, or network restrictions.
