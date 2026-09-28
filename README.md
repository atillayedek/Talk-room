# TalkRoom Kotlin

Native Kotlin rewrite of the original Flutter/Node TalkRoom prototype.

## What Changed

- Android app is Kotlin + Jetpack Compose.
- Backend runs on Supabase Edge Functions.
- Supabase Auth stores users.
- Supabase Postgres stores profiles, rooms, and room membership.
- Room passwords are BCrypt-hashed in the Supabase Edge Function.
- Agora RTC tokens are generated only in the Supabase Edge Function with the Agora App Certificate.
- No mock users, mock rooms, local password store, or test-only fixtures are used.

## Configure Supabase

1. Use the created Supabase project: `xcqcaaejpjumhhaqqwmd`.
2. Run `supabase/schema.sql` in the Supabase SQL editor.
3. Enable email/password auth in Supabase Auth.
4. Copy the project URL, anon key, and service-role key.

## Configure Supabase Edge Function

Install the Supabase CLI, link the project, then set function secrets:

```powershell
supabase link --project-ref your-project-ref
supabase secrets set SUPABASE_URL=https://your-project.supabase.co
supabase secrets set SUPABASE_ANON_KEY=your-public-anon-key
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
supabase secrets set AGORA_APP_ID=your-agora-app-id
supabase secrets set AGORA_APP_CERTIFICATE=your-agora-app-certificate
supabase secrets set AGORA_TOKEN_TTL_SECONDS=3600
supabase functions deploy talkroom
```

Function base URL:

```text
https://xcqcaaejpjumhhaqqwmd.supabase.co/functions/v1/talkroom
```

## Configure Android

Copy `local.properties.example` to `local.properties` and fill:

```properties
sdk.dir=C:\\Users\\xeazr\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-key
BACKEND_BASE_URL=https://your-project-ref.supabase.co/functions/v1/talkroom
AGORA_APP_ID=your-agora-app-id
```

Build:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Use the checked-in Gradle wrapper with JDK 21. CI reads `SUPABASE_URL` and
`BACKEND_BASE_URL` from repository variables and `SUPABASE_ANON_KEY` from a
repository secret. Only a publishable/anon key belongs in the Android app.

## Authentication

- HTTP error statuses are checked before decoding success models. Supabase's
  `msg`, `message`, `error_description`, or `error` is shown instead of a serializer error.
- With email confirmation enabled, signup can return only a user. The app then
  stays signed out and asks the user to confirm the email before password login.
- Sessions are encrypted with an Android Keystore AES-GCM key and restored on
  startup. Passwords are never persisted. Android backup is disabled.
- Authenticated profile, room, and Agora-token requests refresh expiring sessions
  under a mutex. A 401 is retried once after refresh; network/server failures do
  not discard the stored session. Invalid refresh tokens require a new login.
- A successful login is retained when loading a profile or rooms fails. Refreshing
  rooms retries both loads. Logout clears the device session even when offline;
  server revocation requires connectivity and failure is reported.
- Email confirmation and recovery delivery depend on the Supabase project's URL,
  email templates, and SMTP configuration. Recovery emails use the configured
  Supabase redirect; this change does not add an in-app reset-password callback.

## Production Notes

- Do not put `SUPABASE_SERVICE_ROLE_KEY` or `AGORA_APP_CERTIFICATE` in the Android app.
- Keep `SUPABASE_SERVICE_ROLE_KEY` and `AGORA_APP_CERTIFICATE` only as Supabase Function secrets.
- Set `BACKEND_BASE_URL` to the deployed Supabase Function URL before release builds.
- Add Play billing before enabling real diamond purchases; the current screen intentionally has no fake purchase button.
