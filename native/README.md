# Xray / libXray integration

NET CLOUD SL now uses the official XTLS/libXray integration contract. libXray exposes a structured `Invoke` API; Android builds are produced with gomobile. The app creates the Android `VpnService` TUN and passes its file descriptor to Xray through the root `env` key `xray.tun.fd`. citeturn2search0turn0search3

## Build the native core

1. Install Git, Go, Python 3, Android SDK/NDK and gomobile on your build PC.
2. Clone `https://github.com/XTLS/libXray` into `native/libXray`.
3. Run:

```bash
./native/build-libxray.sh
```

The official project documents `python3 build/main.py android` for Android builds and currently pins Xray-core to its supported release line. citeturn2search0

4. The script places `libXray.aar` at `app/libs/libXray.aar`.
5. Build the Android project with Gradle.

The app intentionally uses reflection for the `LibXray` class so the Java project can still be inspected before the AAR is present. With the AAR installed, it calls `LibXray.invoke()` for `runXray`, `stopXray`, and `getXrayState`, and uses the Android DNS controller when available. citeturn3search0

## Important

Do not copy a desktop Linux `xray` executable into the APK. Android requires an Android-compatible native build. The official libXray project produces Android artifacts for this purpose. citeturn0search0
