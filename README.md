# NET CLOUD SL V2 — Android VLESS/Xray client

Architecture:

`VLESS URI → Xray JSON → Android VpnService TUN → libXray/Xray-core → VLESS outbound`

## Included
- NET CLOUD SL V2 redesigned Android UI
- Branded logo extracted from the supplied NET CLOUD SL artwork
- VLESS URI import and local profiles
- TCP / WS / gRPC transport fields
- TLS / REALITY fields
- Xray JSON generation
- Android VpnService TUN setup
- libXray Invoke bridge
- Connect/disconnect lifecycle
- Real tunnel connectivity verification before showing PROTECTED
- Android libXray socket-protection controller + protected DNS integration
- Live session traffic counters
- VLESS config-file import
- GitHub Actions build pipeline

## Native core
The repository does not bundle the large native AAR. The workflow clones the official XTLS/libXray source and builds `libXray.aar`, then builds the APK. Current libXray uses structured `Invoke` requests with `apiVersion: 3`; `runXray` receives the Xray JSON as `payload.xrayJson`, and runtime TUN FD values belong inside the Xray JSON root `env`.

## Build locally
1. Install Android Studio/SDK, NDK, Java 17, Go and Python 3.
2. Run `./native/build-libxray.sh`.
3. Put the resulting AAR at `app/libs/libXray.aar`.
4. Build with Android Studio or Gradle.

## Build without local Android/Go setup
Push this project to a GitHub repository and run **Actions → Build NET CLOUD SL APK**. The workflow builds the native AAR and uploads the debug APK as an artifact.

## Important runtime note
A working VPN client needs the native Xray core plus the Android socket-protection/controller integration so Xray's own server connection is not captured by the VPN tunnel. libXray documents Android socket protection and DNS handling as part of its controller integration.

This source package therefore does not claim that the current environment has produced a tested production APK. The reproducible build path is included instead.

Official project: https://github.com/XTLS/libXray
