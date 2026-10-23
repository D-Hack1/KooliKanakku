# Connecting KooliKanakku to your own Supabase project

The Find-a-worker feature needs a small server. Without one, the app runs a built-in **demo backend**
(sample workers, saved on the phone, nothing sent anywhere), so you can demo with no setup at all.
Follow these steps only when you want real accounts, shared between two phones. It takes about 10 minutes.

## 1. Create the project
1. Go to https://supabase.com, sign in, and click **New project**. Pick any name and a strong database password.
   Choose the region nearest you (for Kerala, Mumbai or Singapore).
2. Wait until the project says it is ready.

## 2. Create the tables and rules
1. In the left menu open **SQL Editor** > **New query**.
2. Open `supabase/schema.sql` from this repo, copy **all** of it, paste it into the editor and press **Run**.
   You should see "Success. No rows returned". It is safe to run again.

## 3. Allow sign-up without an email link
The app signs people in with phone number + a 6-digit PIN (no SMS). Internally that is an email-style login, so:
1. **Authentication** > **Sign In / Providers** (or **Providers**) > **Email**.
2. Turn **Confirm email** **off**, and make sure the Email provider is **on**. Save.

## 4. Give the app the project address
1. **Project Settings** > **API**. Copy the **Project URL** and the **anon public** key.
   Never use the **service_role** key in the app.
2. In the project root (next to `settings.gradle.kts`) copy `.env.example` to a new file called `.env` and fill it in:
   ```
   SUPABASE_URL=https://xxxxxxxx.supabase.co
   SUPABASE_ANON_KEY=eyJhbGciOi...
   ```
   `.env` is ignored by git, so the key is not published.
3. Rebuild and install the app. The yellow "Demo mode" line on the sign-in screen disappears when the real
   server is in use. Delete `.env` (or empty it) to go back to the demo backend.

## 5. Try it with two phones
1. Phone A: Find a worker or get booked > Worker > sign up > save profile > switch **Open for work** on.
2. Phone B: same screen > Owner > sign up > **Find workers near me** > tap the worker > **Send request**.
3. Phone A accepts. Both phones now show a **Call** button. When the worker arrives, either phone taps
   **Start the estimate**.

## Limits to know about
- Login is phone + PIN with no SMS check, so anyone who knows a number and guesses its PIN could sign in as that
  person. A real launch needs SMS verification (Supabase phone provider with Twilio or similar).
- Updates arrive by checking the server every few seconds. There are no push notifications when the app is closed.
- The "verified rating" means the job carried a bill fingerprint and an after-photo hash. The server cannot look at
  the photos, so this is as honest as the phone that sent it.
- A free Supabase project pauses after a week without use. Open the dashboard and press **Restore** before a demo.

## Testing the server rules yourself (needs Docker)
These scripts run the SQL and the exact web requests the app makes against a throwaway Postgres and PostgREST.
```
docker run -d --rm --name kk-pg -e POSTGRES_PASSWORD=test -p 127.0.0.1:54329:5432 postgres:16-alpine
pip install psycopg2-binary pyjwt
python3 supabase/test_schema.py      # 55 checks on the SQL rules

docker run -d --rm --name kk-rest --network host -e PGRST_DB_URI=postgres://postgres:test@127.0.0.1:54329/postgres \
  -e PGRST_DB_SCHEMAS=public -e PGRST_DB_ANON_ROLE=anon \
  -e PGRST_JWT_SECRET=kk-test-secret-kk-test-secret-kk-test-secret postgrest/postgrest
python3 supabase/test_rest.py        # 24 checks on the web requests
docker rm -f kk-pg kk-rest
```
