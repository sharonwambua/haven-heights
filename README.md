# Haven Heights

Property management for self-managing landlords and tenants in Machakos.
Android app (Kotlin, Jetpack Compose) plus a FastAPI backend.

```
haven-heights/
  backend/   FastAPI + SQLAlchemy (SQLite in dev, PostgreSQL in production)
  android/   Kotlin + Jetpack Compose, MVVM, Retrofit
```

## What the app does

| Feature | Landlord | Tenant |
|---|---|---|
| Sign in with phone + password, change password | yes | yes |
| Profile page (details, emergency contact; estate and payment details for the landlord) | yes | yes |
| Tenant home dashboard: amount due, lease, water this month, latest notices, landlord contact | | yes |
| Lease: rent, deposit, due day, end date, key terms, signed agreement (PDF or photo) | edit and attach | view |
| Bills: rent, water, other. Water bills are created automatically from readings; rent bills in one tap | create, approve or reject payments, mark cash payments | see what is due and how to pay |
| Payment approval: tenant taps "I've paid" with the M-Pesa code, landlord checks and approves or rejects with a reason; the tenant sees the result immediately (and by notification) | decide | submit |
| Meter photos from the gallery, with the date they were taken (reading can be dated earlier) | yes | |
| In-app notification inbox with unread badge | yes | yes |
| Register tenants (unit, move-in date, starting meter reading, rent, deposit) | yes | |
| Record a water meter reading by hand, with a photo as proof | yes | |
| Guards: lower-than-last is rejected, large jumps need confirmation | yes | |
| View the kept meter photo for any reading | yes | own readings |
| Delete a mistaken latest reading and re-record it | yes | |
| Share a water statement (WhatsApp, SMS, email) | yes | |
| Usage history and this month's bill | per tenant | yes |
| Maintenance requests (LOW / MEDIUM / HIGH; OPEN / IN_PROGRESS / RESOLVED) | track, update, resolve | log and follow |
| Estate notices (water, maintenance, garbage, general) | post, delete | read |
| Push notifications | new requests | notices, repair updates, new readings |
| Works offline | readings queue and upload later | repair requests queue and upload later |

**Offline behaviour.** Every screen opens with the last data saved on the phone. Readings and
repair requests made with no signal are stored on the phone and uploaded automatically, oldest
first, when a connection returns, keeping the time they were really taken. If the server
refuses one (for example a big jump), it waits on the Meters tab with "Save anyway" or "Discard".
Registering tenants and posting notices need a connection.

**AI meter reading.** The record-reading screen can send a selected meter photo to
`POST /readings/scan`. The backend runs the exported U-Net display locator, orientation CNN,
and individual-digit CNN, then returns a suggested eight-digit reading with confidence values.
The landlord must review the suggestion and tap Save; scanning never creates a bill by itself.
If the model files are not installed, manual readings continue to work and the scan endpoint
returns a clear 503 response.

## Install the water-meter CNN

Run the final project notebook through Part 7.10. It exports these files into its `WORK_DIR`:

- `unet_window_locator.keras`
- `orientation_cnn.keras`
- `single_digit_cnn.keras`
- `meter_reader_config.json`

Create `backend/models/meter_reader/` and copy those four files there. The notebook's
`CNN_Base_Improved_v2_HavenHeights_Export.ipynb` includes the matching export configuration.
Do not rename the files. If you store them elsewhere, set `METER_MODEL_DIR` to that directory.

The backend loads the models on the first scan request, not at server startup. Install the
additional CPU inference dependencies from `backend/requirements.txt`. TensorFlow is large;
allow time for the first installation and first model load.

The AI only suggests the digits. The landlord must verify the value before saving, and the
existing lower-reading and large-jump checks still apply.

## Run the backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate      # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
cp .env.example .env                                   # change SECRET_KEY before real use
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API docs: http://localhost:8000/docs
- A demo landlord account is already configured on a fresh installation: **0700000000 / changeme123**.
- On first startup, Haven Heights automatically creates the landlord account and starter records: three occupied units, three demo tenants, rent and water bills, a meter reading, a maintenance request, estate payment details and an announcement. Seeding is safe to repeat and does not duplicate existing demo tenants.
- Demo tenant logins: **0712000001 / tenant123** (Mary Mutua), **0712000002 / tenant123** (John Kioko), **0712000003 / tenant123** (Grace Nduku).
- You can also run `python -m app.seed_demo` manually if you need to restore missing starter data.
- Database tables are created and upgraded automatically on start (Alembic). After changing a model: `alembic revision --autogenerate -m "what changed"`.
- Tests: `python -m pytest`
- Water rate and the "large jump" threshold are in `.env` (`WATER_RATE_PER_UNIT`, `LARGE_JUMP_UNITS`).

## Run the Android app

1. Open the `android/` folder in Android Studio (Koala or newer, JDK 17). Let Gradle sync.
2. Emulator: nothing to change. It reaches your PC through `http://10.0.2.2:8000/`.
   Real phone: set the debug `API_BASE_URL` in `android/app/build.gradle.kts` to your PC's
   LAN address (for example `http://192.168.1.20:8000/`) and keep both on the same Wi-Fi.
3. Run, then sign in with the demo landlord credentials above. For real deployment, change the demo password and `SECRET_KEY` in `.env`.

Release builds must point at an `https://` address.

## Turn on push notifications (optional, free)

1. Create a project at https://console.firebase.google.com and add an **Android app** with package name `com.havenheights.app`.
2. Download `google-services.json` into `android/app/` and rebuild. The app detects it automatically.
3. In Firebase: Project settings, Service accounts, **Generate new private key**. Save the JSON on the server and set
   `FIREBASE_CREDENTIALS_FILE=/path/to/that.json` in the backend `.env`.

Without these two files the app and server run normally; notifications are simply off.

## Deploy

`backend/docker-compose.yml` runs the API with PostgreSQL:

```bash
cd backend
cp .env.example .env     # set SECRET_KEY, POSTGRES_PASSWORD, landlord details
docker compose up -d --build
```

Put HTTPS in front (Caddy or Nginx with a free Let's Encrypt certificate) and set the release
`API_BASE_URL` in `android/app/build.gradle.kts` to that address. Then build a signed release in Android Studio
(Build, Generate Signed Bundle / APK).

## Design

| Role | Colour | Hex |
|---|---|---|
| Background | Cream | `#F7EFE7` |
| Cards, chips, nav indicator | Nude | `#FFFAF5`, `#E6D3C2` |
| Secondary text, borders | Nude brown | `#7A5740`, `#B89377` |
| Primary actions, high urgency | Burgundy | `#6B1E2E` |
| Calm actions, resolved, water | Sage green | `#55694B`, `#9DB08C`, `#E1E8D9` |

Colours live in `ui/theme/Theme.kt`. Headings use the system serif; body text uses the system sans.

## API summary

| Method | Path | Who |
|---|---|---|
| POST | `/auth/login` | all |
| GET | `/overview` | landlord |
| POST / GET | `/tenants` | landlord |
| GET | `/tenants/{id}`, `/tenants/{id}/readings` | landlord (readings also the tenant themself) |
| PATCH | `/tenants/{id}/move-out` | landlord |
| POST | `/readings` (multipart: tenancy_id, value, confirm_large_jump, photo) | landlord |
| POST | `/readings/scan` | landlord (AI suggestion only; no reading or bill is saved) |
| GET / PATCH | `/me` (profile) | all |
| GET / PATCH | `/estate` | all read, landlord edits |
| GET | `/dashboard` | tenant |
| GET / POST | `/bills` | tenant (own) / landlord (all) |
| POST | `/bills/generate-rent` | landlord (idempotent) |
| POST | `/bills/{id}/submit-payment` | tenant (own bill) |
| POST | `/bills/{id}/review` (approve or reject) | landlord |
| PATCH / DELETE | `/bills/{id}/status`, `/bills/{id}` | landlord (paid or submitted bills can't be deleted) |
| GET | `/notifications`; POST `/notifications/read` | all (own inbox) |
| PATCH / POST / GET | `/leases/{id}`, `/leases/{id}/file` (x2) | landlord edits; tenant views own |
| GET / DELETE | `/readings/{id}/photo`, `/readings/{id}` | landlord or owner / landlord (latest only) |
| POST | `/auth/change-password` | all |
| POST | `/devices`, `/devices/unregister` | all (push tokens) |
| GET | `/readings/mine` | tenant |
| POST / GET | `/requests` | tenant (own) / landlord (all) |
| PATCH | `/requests/{id}/status` | landlord |
| POST / DELETE | `/announcements` | landlord |
| GET | `/announcements` | all |

## Next steps

1. Run the backend test suite and validate all three exported models with fresh, held-out meter photographs on the target machine. The Android scan button and authenticated API route are already connected; AI results remain suggestions until the landlord reviews and saves them.
2. M-Pesa (Daraja) so bills are marked paid automatically instead of by the landlord.
3. A short in-app tour and a Swahili translation of the strings.

## Project documentation

- [Final Development Setup Guide](docs/FINAL_SETUP_GUIDE.md) — Windows/Python 3.11 backend setup, Android connectivity, demo accounts, AI model setup, troubleshooting and verification checklist.
