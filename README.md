# TizenSmart

A [SmartTube](https://github.com/yuliskov/SmartTube) fork for Android TV with a **TizenTube / YouTube TV style UI**:
dark theme, white focus pills, mini sidebar with the current user, new channel page (banner, avatar, description, Play mix),
redesigned player controls and dialogs.

Playback, sign-in and the SmartTube core are unchanged. This is not an official SmartTube build.

## Download

Every push to `main` builds the APKs with GitHub Actions: open the **Actions** tab, pick the latest run and download the
artifact for your device (`arm64-v8a` for most modern boxes, `armeabi-v7a` for old 32-bit TVs, `universal` if unsure).
Pushing a tag like `v1.0.0` also publishes a GitHub Release with the APKs.

## Build

```
./gradlew :smarttubetv:assembleStbetaDebug
```

Requires JDK 17 and the Android SDK. Output: `smarttubetv/build/outputs/apk/stbeta/debug/`.

### Signed builds (optional)

To get APKs signed with your own key (needed to update an installed build in place), add these repository secrets:
`SIGNING_KEY` (base64 of the .jks), `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`.
Without them the CI signs with a temporary debug key, so each build must be installed over a fresh uninstall.

## Credits and license

Based on SmartTube by yuliskov (MIT). `MediaServiceCore` and `SharedModules` are vendored from the same author.
See [LICENSE](LICENSE).
