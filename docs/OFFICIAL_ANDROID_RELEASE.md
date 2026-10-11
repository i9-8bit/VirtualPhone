# Publishing official signed Android 1–17 builds

The compatibility ZIP is a debug-signed test package, not the official signed release. To publish the real official APKs, use the tagged-release workflow in `.github/workflows/android-official-release.yml`.

## 1. Prepare a release keystore

Use the existing project release keystore if you have it. The private key cannot be recovered from an APK file. If you must create a new key for these new package IDs, back it up securely; future updates of each variant need the same signing key.

Do **not** commit the keystore to this repository or send the private keystore/passwords in chat.

## 2. Add GitHub Actions repository secrets

In the repository on GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `ANDROID_KEYSTORE_BASE64`: Base64 text for the `.jks` or `.keystore` file
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password
- `ANDROID_KEY_ALIAS`: the alias of the signing key
- `ANDROID_KEY_PASSWORD`: the signing-key password

On Windows PowerShell, copy your keystore file as Base64 without printing or uploading the binary itself elsewhere:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\your-release-key.jks")) | Set-Clipboard
```

Paste that clipboard content into the value for `ANDROID_KEYSTORE_BASE64`. Replace the example path with your actual keystore path. Keep the keystore file and passwords backed up securely.

If you do not have the keystore that signed the existing Alpha, do not assume the APK contains it. The new Android-version variants use distinct application IDs, but each variant still needs a stable signing key for future updates.

## 3. Merge the release workflow to the default branch

The official release workflow only runs for a Git tag matching `v*` on a commit that is on `main`. Review and merge the compatibility PR into `main` after the signing secrets are configured and the code has been tested as far as your devices allow.

## 4. Tag the release

After the workflow is present on `main`, create and push a tag such as `v1.2.0` from the reviewed commit. The workflow will:

- build signed Release APKs for Android 1 through Android 17;
- upload all 17 APKs as separate GitHub Release assets;
- attach the combined ZIP, full-source ZIP, and SHA-256 checksums;
- publish a normal release (not a prerelease).

A missing/invalid secret makes the workflow fail rather than silently publish unsigned APKs.

## Compatibility caveat

A release signature only establishes a stable app identity; it does not prove the app works on every Android version. Android 1–3 networking is experimental and must be tested on real hardware. Do not use a legacy TLS proxy that disables verification of the remote server certificate. The server URL in the app is a Cloudflare Quick Tunnel and can expire or change.
