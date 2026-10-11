# Android 4–17 compatibility status

This is a compatibility baseline, not a claim that every OS/device combination has passed testing.

## Build range

- Minimum install API: 14 (Android 4.0).
- Compile/target SDK: 37 (Android 17 / API 37.0).
- Android Gradle Plugin: 9.4.0.
- Gradle wrapper: 9.6.0.
- Java source/target: 8.

The launcher activity uses the explicit class name `com.virtualphone.MainActivity`; the installed application ID remains `com.virtualphone.i9`. View background calls use the API-14-compatible method. AGP and Gradle are aligned with API 37.0 support.

## TLS behavior and security

The dependency remains on Java-WebSocket 1.3.9, the version already tested by the developer on Android 4.3. WSS connections use a host-aware TLS socket created off the UI thread, enable TLS 1.3 and/or 1.2 when supported by the system provider, retain the platform certificate-chain validation, and explicitly verify the TLS hostname in `onOpen()` before sending the app's authentication message.

Important security caveat: Java-WebSocket versions through 1.4.1 are listed as affected by CVE-2020-11050 when hostname validation is not performed. VirtualPhone's explicit hostname check is a compensating control for its current connection flow and must never be removed. A future dependency upgrade should use a patched library version with legacy APIs repaired, rather than removing the hostname check or accepting any certificate.

This implementation deliberately does **not** fall back to TLS 1.0 or TLS 1.1. Some Android 4.0/4.1 devices lack TLS 1.2 or sufficiently current root certificates. On those devices, the app may open but be unable to connect to modern HTTPS/WSS services. Full networking support on those OS releases requires a separately licensed, security-audited TLS provider with certificate-chain and hostname verification implemented correctly. This branch does not embed Wolfius.

## Android 17 local-network access

Apps targeting API 37 need the `ACCESS_LOCAL_NETWORK` runtime permission to connect to devices on a LAN. The manifest declares it and the app requests it on Android 17 when the entered server address looks local (private IPv4, loopback, link-local, single-label host, or common local hostname suffix). Because the app avoids DNS lookups on the UI thread, an unusual hostname that resolves to a private IP may need a future refinement of this detection logic.

## Wolfius research note

[Wolfius](https://github.com/gohoski/Wolfius) demonstrates a wolfSSL-based TLS proxy for legacy Android 1.5–4.4. Its README describes it as early/unstable and recommends root for system-wide interception. It is not embedded here. Its current native client code disables upstream certificate verification, so adopting it without substantial security changes would be unsafe. Wolfius is GPL-3.0; code reuse would also require a licensing decision for this Apache-2.0 project.

## Backend warning

The existing prototype backend uses self-chosen User IDs rather than real account authentication, and its current messages are stored in memory. Do not use it for private or production messages until authentication, authorization, persistence, rate limits, TLS deployment, abuse protections, and security testing are implemented.

## Required validation

Before publishing a new APK, test on:
- API 14/15: install and UI; confirm clear TLS-unavailable errors where appropriate.
- API 16–18: WSS, certificate chain, hostname, connect/reconnect, message round-trip, and audio.
- Current Android 17 API 37 emulator/device: local-network permission, public WSS, large-screen resizing, audio permissions, reconnect, and messaging.

A successful configuration edit is not proof that all OS/device combinations are supported.


## Safe test APK

The CI workflow produces a *compatibility test* APK with application ID `com.virtualphone.i9.compatTest`, so it can be installed alongside the existing Alpha APK without uninstalling or replacing it. It is a debug-signed test build, not a release APK. Do not publish it as the official Alpha. The workflow retains the APK artifact for 14 days.
