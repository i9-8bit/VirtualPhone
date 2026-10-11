# Android 4–17 compatibility APK pack

The GitHub Actions workflow builds a separate, full standalone APK for each Android major version from Android 4 through Android 17. Each APK has a distinct package ID suffix (for example, `com.virtualphone.i9.android4`) so testing it will not replace the official Alpha app (`com.virtualphone.i9`).

## APK mapping

| APK variant | Android API range | minSdk | targetSdk | maxSdk |
|---|---:|---:|---:|---:|
| Android 4 | 14–20 | 14 | 20 | 20 |
| Android 5 | 21–22 | 21 | 22 | 22 |
| Android 6 | 23 | 23 | 23 | 23 |
| Android 7 | 24–25 | 24 | 25 | 25 |
| Android 8 | 26–27 | 26 | 27 | 27 |
| Android 9 | 28 | 28 | 28 | 28 |
| Android 10 | 29 | 29 | 29 | 29 |
| Android 11 | 30 | 30 | 30 | 30 |
| Android 12 | 31–32 | 31 | 32 | 32 |
| Android 13 | 33 | 33 | 33 | 33 |
| Android 14 | 34 | 34 | 34 | 34 |
| Android 15 | 35 | 35 | 35 | 35 |
| Android 16 | 36 | 36 | 36 | 36 |
| Android 17 | 37 | 37 | 37 | 37 |

The package ZIP contains an `APKs/` directory with all 14 standalone APKs and a `Source/VirtualPhone-Full-Source.zip` archive with the complete source tree used to build them. Each APK is a full installable app, not an Android split-APK fragment.

These are debug-signed compatibility builds for testing, not the official signed Alpha release. A successful automated build is not proof that every OS/device can run every feature. Android 4.x devices may still have system TLS/root-certificate limitations; certificate validation must remain enabled. The Android 17 variant targets API 37 and explicitly requests local-network permission when its entered server looks like a LAN host.
