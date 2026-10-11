# VirtualPhone Phase 3 — real audio prototype

This is a working-source prototype for an app-only Wi-Fi phone service.

## What is real here
- App-only virtual identities (not carrier phone numbers)
- Text messaging
- WebSocket signaling
- Real microphone capture/playback
- Binary PCM audio relay through the server
- Android 4.3-compatible native audio path can be added using the included protocol
- Modern Android/iPhone can use the web client in Safari/Chrome

## Important limitation
This prototype uses 16 kHz mono PCM through WebSocket. It is intentionally simple and is NOT production-grade VoIP. It needs HTTPS/WSS, authentication tokens, abuse controls, a proper jitter buffer, Opus/WebRTC for production, and a TURN server for difficult networks.

## Run server
1. Install Node.js 18+.
2. In `backend`, run `npm install`.
3. Run `npm start`.
4. Open `http://SERVER-IP:8080/web/` on two modern devices on the same Wi-Fi.
5. Allow microphone permission.
6. Register each device, exchange the displayed virtual numbers, and call.

The old Android 4.3 client should use the same `/ws` binary protocol after authentication. The protocol is documented below.

## Binary audio protocol
After an authenticated user is in an active call, binary WebSocket frames are raw little-endian signed 16-bit PCM, mono, 16,000 samples/second. The server forwards the binary frame to the other call participant.

## Production upgrade
Use WSS behind HTTPS, JWT/session authentication, WebRTC/Opus for modern clients, STUN/TURN, a legacy media gateway for API-18 Android, rate limits, call timeouts, push notifications, and persistent storage.

## Compatibility notes

See [ANDROID-4-17-COMPATIBILITY.md](ANDROID-4-17-COMPATIBILITY.md) for the build baseline, TLS constraints, Android 17 local-network permission, and the required device test matrix.

## Android 4–17 APK pack

See [ANDROID-4-17-COMPATIBILITY.md](ANDROID-4-17-COMPATIBILITY.md) for the 14 standalone APK variants and the downloadable source/package layout. The official Alpha release is kept separate from compatibility preview builds.
