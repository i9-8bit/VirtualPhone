# Android 4.3 / API 18 client

The included native client is designed around API 18-compatible `AudioRecord` + `AudioTrack` and does not require AndroidX.

### Flow
1. Register a user with the server's HTTP `/api/register` endpoint using the MVP/Phase 2 client or another client.
2. Put the server WebSocket URL into the Android app, e.g. `ws://192.168.1.5:8080`.
3. Enter the user ID and the other user's ID.
4. Connect and call.
5. Audio is raw PCM 16 kHz mono in binary WebSocket frames.

### Important
This is a prototype transport. For Internet deployment, put the server behind TLS/WSS and authentication. Do not expose an unauthenticated raw-audio relay to the public Internet.
