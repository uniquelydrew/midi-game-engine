# Google Play Release Checklist

## Project preparation

- Choose the final application ID before the first Play upload. The current ID is `com.example.midigameengine`.
- Confirm the app name, support URL, privacy-policy URL, screenshots, feature graphic, and store description.
- Review [PRIVACY_POLICY.md](PRIVACY_POLICY.md) and publish it at a stable HTTPS URL.
- Publish [privacy-policy.html](privacy-policy.html) through the repository's public web hosting or another stable HTTPS host.
- GitHub Pages deployment is defined in [pages.yml](../.github/workflows/pages.yml). Enable GitHub Pages with **GitHub Actions** as its source, then confirm the generated site URL before entering it in Play Console. [index.html](index.html) provides the support landing page and links to the policy.
- Review [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) against the resolved Gradle dependency graph.

## Signing

1. Create a Play upload keystore outside the repository.
2. Copy [keystore.properties.example](../keystore.properties.example) to Gradle user properties or set the equivalent environment variables.
3. Keep the upload key and passwords backed up securely.
4. Use Google Play App Signing when creating the Play app.

Example keystore creation command:

```powershell
keytool -genkeypair -v -keystore midi-game-engine-upload.jks `
  -alias midi-game-engine-upload -keyalg RSA -keysize 2048 `
  -validity 10000
```

The upload keystore is a permanent release credential. Store it and its passwords in a password manager and keep a secure backup.

Example release commands:

```powershell
.\gradlew.bat :app:bundleRelease
.\gradlew.bat :app:assembleRelease
```

Without release signing properties, Gradle produces an unsigned release artifact for local inspection. A Play upload must be signed with the configured upload key.

## Verification before upload

- Run `:core:testDebugUnitTest`.
- Build and inspect `app/build/outputs/bundle/release/app-release.aab`.
- Install a signed release APK on a clean Android 15/16 device.
- Test MIDI import, track selection, library restore, physical MIDI input, audio playback, rotation, scrubbing, trim, zoom, and log export.
- Run Android Studio's APK/AAB analyzer and confirm no unexpected permissions or native libraries.
- Complete Play Console Data safety, content rating, target audience, app access, and permissions declarations.

## CI

The repository workflow runs core tests and a debug APK build for pushes and pull requests. A manual release-check workflow can build a signed AAB when these GitHub Actions secrets are configured:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The Pages workflow publishes the support site whenever documentation changes on `main`. It does not make a Play Console account, create the app listing, or substitute for the required stable HTTPS privacy-policy URL verification.

## Current app data posture

- No network permission is declared.
- No broad storage permission is declared.
- MIDI documents are selected through the system picker.
- Diagnostics are opt-in and exported only by the user.
- The app currently targets Android 16 (API 36), matching the current new-app target requirement.
