# Haven Heights — Final Development Setup Guide

**Version:** 1.1 · November 2026  
**Project:** Haven Heights — Rental and Property Management Application  
**Technology stack:** Python 3.11, FastAPI, Kotlin, Jetpack Compose, SQLite and three trained CNN models.

This guide covers local development setup and verification. The demo credentials and HTTP configuration are for development only; secure the application before deployment.

## 1. Requirements

Install:

- Python 3.11 (64-bit)
- Android Studio
- Visual Studio Code (recommended for backend development)
- Git
- An Android phone
- A Wi-Fi network shared by your computer and phone

Use Python 3.11 for the backend environment because compatibility with machine-learning dependencies such as TensorFlow can vary across newer Python versions.

## 2. Backend setup

Open the project root in Visual Studio Code, then run these commands in PowerShell:

```powershell
cd backend
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python --version
python -m pip install --upgrade pip
pip install -r requirements.txt
```

If PowerShell blocks virtual-environment activation:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

Confirm that Python reports version 3.11.x (the guide was prepared with Python 3.11.9).

Verify key dependencies:

```powershell
python -c "import tensorflow as tf; print(tf.__version__)"
python -c "import fastapi, cv2, numpy; print('Dependencies imported successfully')"
```

Resolve dependency errors before continuing.

## 3. Start the backend

Keep the virtual environment active and run this from the `backend` directory:

```powershell
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Open the API documentation on the computer at:

- http://127.0.0.1:8000/docs

Keep the backend terminal open while using the Android application.

## 4. Demo login credentials

These accounts are for development/demo use only. Change passwords and secrets before deployment or exposing the application to other users.

| Account | Name | Phone | Password | Unit |
|---|---|---|---|---|
| Landlord | Haven Heights Admin | 0700000000 | changeme123 | — |
| Tenant | Mary Mutua | 0712000001 | tenant123 | A1 |
| Tenant | John Kioko | 0712000002 | tenant123 | A2 |
| Tenant | Grace Nduku | 0712000003 | tenant123 | B1 |

Log in using the phone number and password. Demo tenants are seeded only when their phone numbers do not already exist. The landlord account is created on startup only if no landlord exists; default details can be overridden through backend environment settings.

## 5. Android Studio backend configuration

### Configure the API URL for a physical phone

On the computer, run:

```powershell
ipconfig
```

Find the IPv4 address under the active network adapter. The address used when this guide was prepared was `192.168.100.25`; your address may be different.

Open `android/app/build.gradle.kts` and set the debug API URL to your computer's current LAN address:

```kotlin
debug {
    buildConfigField(
        "String",
        "API_BASE_URL",
        "\"http://192.168.100.25:8000/\""
    )
}
```

Replace the example IP with your current address. For an Android emulator, use:

```
http://10.0.2.2:8000/
```

Keep the release configuration pointed at a real HTTPS production API URL; do not ship a local HTTP address in a release build.

### Sync and rebuild

1. Save the Gradle file.
2. Click **Sync Now** in Android Studio.
3. Wait for synchronization to finish.
4. Select **Build → Rebuild Project**.
5. Run the application on your phone or emulator.

## 6. Android internet permissions

In `android/app/src/main/AndroidManifest.xml`, ensure this permission appears before the `<application>` element:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

For local HTTP development, the application may need `android:usesCleartextTraffic="true"` on the `<application>` element. The current repository manifest should be checked before adding it. This setting permits cleartext connections and should not be left unrestricted in a production build; use HTTPS in production.

## 7. Connect a physical phone

1. Connect the computer to Wi-Fi.
2. Connect the phone to the same Wi-Fi network.
3. Start the FastAPI backend.
4. Allow Python or the backend through Windows Defender Firewall on private networks.
5. Open Chrome on the phone and visit `http://<computer-LAN-IP>:8000/docs`.

If the documentation does not load, check that the backend is running, the IP address has not changed, Windows Firewall permits the connection, and the router does not isolate wireless clients.

## 8. Water-meter AI setup

The water-meter workflow uses three trained models:

| Model | Purpose |
|---|---|
| `unet_window_locator.keras` | Locates the meter display |
| `orientation_cnn.keras` | Determines display orientation |
| `single_digit_cnn.keras` | Recognizes individual digits |

The configuration file is:

`backend/models/meter_reader/meter_reader_config.json`

The intended workflow is to locate the display, determine orientation, extract individual digit regions, predict the digits, generate a suggested meter reading and confidence values, and allow the user to review and edit the prediction.

Successful dependency installation does not prove that the models load or predict accurately. Test model loading, real meter images and the complete Android-to-backend workflow before declaring the AI feature production-ready.

## 9. Troubleshooting

| Problem | What to check |
|---|---|
| Can't reach server | Backend status, API URL, Wi-Fi and firewall |
| Connection refused | Confirm Uvicorn is running on port 8000 |
| API docs unavailable on phone | IP address and network connectivity |
| TensorFlow installation fails | Confirm Python 3.11 is active |
| Android build fails | Inspect the complete Gradle error; investigate Kotlin and Gradle plugin compatibility |
| Unexpected JVM signature V | Inspect the Room/KSP/Kotlin processor compatibility and the complete stack trace |
| Register Tenant button does nothing | Check the click handler, navigation and API request |
| AI model loading fails | Check model paths and TensorFlow compatibility |

### Tenant registration troubleshooting

1. Check whether tapping **Register Tenant** opens the registration screen.
2. If submission fails, inspect the backend terminal.
3. Check Android Studio Logcat for exceptions.
4. Verify the registration API endpoint and request format.
5. Confirm the correct backend URL is configured.

Do not consider registration complete until a new tenant can be registered and successfully logged in.

## 10. Starting the project again

### Backend

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Android

1. Open the `android` folder in Android Studio.
2. Wait for Gradle synchronization.
3. Check the API URL.
4. Connect the phone to the same Wi-Fi network as the computer.
5. Click **Run**.

To stop the backend, press **Ctrl+C** in its terminal. To stop the Android app, use the **Stop** button in Android Studio.

## 11. Final verification checklist

- [ ] Python 3.11 environment created.
- [ ] Backend dependencies installed.
- [ ] TensorFlow imports successfully.
- [ ] FastAPI server starts.
- [ ] API documentation opens on the computer.
- [ ] Android API URL is correct.
- [ ] Android internet permission configured.
- [ ] Phone connects to the backend.
- [ ] Android application builds.
- [ ] Landlord login works.
- [ ] Tenant login works.
- [ ] Register Tenant works.
- [ ] Tenant dashboard loads.
- [ ] Landlord dashboard loads.
- [ ] Water-meter image upload works.
- [ ] All three CNN models load.
- [ ] AI predictions can be edited.
- [ ] Confirmed readings save correctly.

**Completion status:** The environment and connection configuration are documented. Items in the final checklist must be tested before the project can be considered fully functional.
