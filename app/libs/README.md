# libXray

`libXray.aar` is intentionally not committed to this source package.

Build it with:

```bash
./native/build-libxray.sh
```

Or use the included GitHub Actions workflow, which builds the AAR and then the APK.

The app's current Invoke contract uses **apiVersion 3**, matching the current XTLS/libXray API. Keep the AAR and app bridge on the same libXray generation.
