# TalkRoom Kotlin

Native Kotlin rewrite of the original Flutter/Node TalkRoom prototype.

## What Changed

- Android app is Kotlin + Jetpack Compose.
- Backend is Kotlin + Ktor.
- Supabase Auth stores users.
- Supabase Postgres stores profiles, rooms, and room membership.
- Room passwords are BCrypt-hashed on the backend.
- Agora RTC tokens are generated only by the backend with the Agora App Certificate.
- No mock users, mock rooms, local password store, or test-only fixtures are used.

## Configure Supabase

1. Create a Supabase project.
2. Run `supabase/schema.sql` in the Supabase SQL editor.
3. Enable email/password auth in Supabase Auth.
4. Copy the project URL, anon key, and service-role key.

## Configure Android

Copy `local.properties.example` to `local.properties` and fill:

```properties
sdk.dir=C:\\Users\\xeazr\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-key
BACKEND_BASE_URL=https://your-talkroom-api.example.com
AGORA_APP_ID=your-agora-app-id
```

Build:

```powershell
gradle :app:assembleDebug
```

This machine does not have global `gradle`; use the cached Gradle binary or generate a wrapper.

## Configure Backend

Set the values from `server/.env.example` as environment variables:

```powershell
$env:PORT = "8080"
$env:SUPABASE_URL = "https://your-project.supabase.co"
$env:SUPABASE_ANON_KEY = "your-public-anon-key"
$env:SUPABASE_SERVICE_ROLE_KEY = "your-service-role-key"
$env:AGORA_APP_ID = "your-agora-app-id"
$env:AGORA_APP_CERTIFICATE = "your-agora-app-certificate"
gradle :server:run
```

Health check:

```powershell
curl http://localhost:8080/health
```

## Production Notes

- Do not put `SUPABASE_SERVICE_ROLE_KEY` or `AGORA_APP_CERTIFICATE` in the Android app.
- Put the Ktor backend behind HTTPS.
- Set `BACKEND_BASE_URL` to that HTTPS endpoint before release builds.
- Add Play billing before enabling real diamond purchases; the current screen intentionally has no fake purchase button.
