# LiveCircle

LiveCircle is a small Android app plus a Node.js relay for private group location sharing.

It avoids Google Play services, uses Android `LocationManager`, and displays group members on an embedded AMap view. The Android client is split into:

- `com.codex.livecircle`: viewer UI and map.
- `com.codex.livecircle.uploader`: foreground location uploader.

## Privacy First

This repository intentionally does not include:

- A real relay server address.
- A real group code.
- A real AMap Android key.
- Server runtime data.
- SSH keys, APK outputs, Android SDK downloads, or local build artifacts.

Before building your own APK, replace the placeholders:

- `http://YOUR_RELAY_HOST:8787`
- `CHANGE_ME`
- `YOUR_AMAP_ANDROID_KEY`

Relevant files:

- `android/app/src/main/java/com/codex/livecircle/AppConfig.java`
- `android/uploader/src/main/java/com/codex/livecircle/uploader/AppConfig.java`
- `android/app/src/main/AndroidManifest.xml`

## Relay

The relay is a plain Node.js HTTP server in `server/`.

Endpoints:

- `GET /api/health`
- `POST /api/location`
- `GET /api/group?groupCode=...`
- `GET /api/events?groupCode=...`

Run locally:

```powershell
cd work\LiveCircle\server
npm install
npm start
```

By default it listens on port `8787`.

## Android Build

Requirements:

- JDK 17 or newer.
- Android SDK with `build-tools\35.0.0` and `platforms\android-35`.
- `JAVA_HOME` set, or `javac.exe` available on `PATH`.
- `ANDROID_SDK_ROOT` set, unless you have a local SDK at `work\tools\android-sdk`.

Build both APKs:

```powershell
powershell -ExecutionPolicy Bypass -File work\LiveCircle\build-all-apks.ps1
```

Outputs are written to `outputs/`, which is ignored by Git.

## Deployment

Aliyun helper scripts are in `aliyun/`.

Example:

```powershell
powershell -ExecutionPolicy Bypass -File work\LiveCircle\aliyun\deploy-livecircle.ps1 -HostName YOUR_PUBLIC_IP -KeyPath path\to\your_private_key
```

Open inbound TCP port `8787` or whichever port you configure for the relay.

## Background Running Notes

The uploader runs as a foreground location service and uploads the latest accepted location every 20 seconds.

For Huawei, OnePlus, and other Android vendors with aggressive background limits, users may still need to manually allow:

- Location permission.
- Notification permission.
- Auto-start / secondary launch / background activity.
- Battery optimization exemption.

Android does not allow a normal third-party APK to continue running after a manual Force stop from system settings.

## Consent

Use this only with people who explicitly agree to share their location. Group codes are bearer secrets: anyone who knows a group code can join that group unless you add stronger authentication.
