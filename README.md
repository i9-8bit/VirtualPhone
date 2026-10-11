# VirtualPhone

VirtualPhone Android app downloads and source.

## Android compatibility preview

The `compat/android-4-to-17-baseline` branch builds versioned standalone APKs for Android 1 through Android 17. The APK pack includes the improved UI and prefilled Quick Tunnel server URL:

`https://clan-rounds-bug-genealogy.trycloudflare.com`

Quick Tunnel URLs are temporary and can stop working or change.

The Android 1–3 variants are experimental. They offer TLS 1.0 only on legacy API levels so the app may work through a compatible transparent local TLS proxy; certificate-chain and hostname verification inside VirtualPhone remain enabled. This does not guarantee successful communication with the modern server.

See [Android 1–3 legacy TLS guide](docs/ANDROID_1_TO_3_LEGACY_TLS.md) before testing. In particular, the current upstream [Wolfius project](https://github.com/gohoski/Wolfius) disables verification of the remote server certificate in its upstream TLS client context. Do not send sensitive data through that upstream build unless the issue is fixed and verified.

## Downloads

- [Compatibility build workflow](https://github.com/i9-8bit/VirtualPhone/actions/workflows/android-compatibility-build.yml)
- [Compatibility releases](https://github.com/i9-8bit/VirtualPhone/releases)
- [Draft compatibility pull request](https://github.com/i9-8bit/VirtualPhone/pull/1)

These are debug-signed preview APKs for testing, not the official signed Alpha release. Test on the target physical device before relying on a build.

## Official signed releases

The all-version compatibility ZIP is a debug-signed test build, not the official signed release. The repository now includes an official release workflow that produces individually downloadable signed APKs for Android 1–17 once the repository signing secrets are configured. Follow [OFFICIAL_ANDROID_RELEASE.md](docs/OFFICIAL_ANDROID_RELEASE.md) for the exact steps. The workflow deliberately fails instead of publishing unsigned APKs when the signing secrets are missing.

The Android 1–3 networking path remains experimental and has not been verified on physical devices. A stable release signature does not mean every legacy OS/device has been tested.
