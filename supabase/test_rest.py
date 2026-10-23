"""Checks the exact HTTP requests the Android app makes against a real PostgREST + Postgres.
Run (needs docker): see supabase/SETUP.md, section "Testing the server rules yourself"."""
import json, sys, time, uuid, urllib.request, urllib.error, urllib.parse
import jwt, psycopg2

BASE = "http://127.0.0.1:3000"
SECRET = "kk-test-secret-kk-test-secret-kk-test-secret"
conn = psycopg2.connect(host="127.0.0.1", port=54329, user="postgres", password="test", dbname="postgres"); conn.autocommit = True
cur = conn.cursor()

cur.execute("""
drop schema if exists public cascade; create schema public; grant usage on schema public to public;
drop schema if exists auth cascade; create schema auth;
do $$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
end $$;
grant usage on schema public to anon, authenticated; grant usage on schema auth to anon, authenticated;
create table auth.users (id uuid primary key default gen_random_uuid(), email text, raw_user_meta_data jsonb);
create function auth.uid() returns uuid language sql stable as $$ select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid $$;
grant execute on function auth.uid() to anon, authenticated;
""")
cur.execute(open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "schema.sql")).read())
cur.execute("notify pgrst, 'reload schema'"); time.sleep(1.5)

def signup(role, name, phone):
    uid = str(uuid.uuid4())
    cur.execute("insert into auth.users (id, email, raw_user_meta_data) values (%s,%s,%s)", (uid, phone + "@x", json.dumps({"role": role, "name": name, "phone": phone})))
    return uid, jwt.encode({"sub": uid, "role": "authenticated", "aud": "authenticated", "exp": int(time.time()) + 3600}, SECRET, algorithm="HS256")

def call(method, path, token=None, body=None, headers=None):
    path = path.replace("/rest/v1", "", 1)   # Supabase serves PostgREST under /rest/v1; a bare PostgREST serves from the root
    req = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items(): req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode(); return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode(); 
        try: return e.code, json.loads(raw)
        except Exception: return e.code, raw

passed = failed = 0
def check(name, cond):
    global passed, failed
    if cond: passed += 1; print("  ok  ", name)
    else: failed += 1; print("  FAIL", name)

owner, ot = signup("OWNER", "Omar", "9100000001")
worker, wt = signup("WORKER", "Asha", "9100000002")

# 1. profile upsert (twice: insert then update)
prof = {"id": worker, "name": "Asha", "languages": ["Malayalam", "English"], "services": ["KITCHEN", "GENERAL"], "rate_min": 200, "rate_max": 300, "bio": "hi"}
s, r = call("POST", "/rest/v1/workers?on_conflict=id", wt, prof, {"Prefer": "resolution=merge-duplicates,return=minimal"}); check("upsert worker profile (insert)", s in (200, 201, 204))
prof["bio"] = "updated"; prof["rate_max"] = 320
s, r = call("POST", "/rest/v1/workers?on_conflict=id", wt, prof, {"Prefer": "resolution=merge-duplicates,return=minimal"}); check("upsert worker profile (update)", s in (200, 201, 204))
s, r = call("GET", f"/rest/v1/workers?id=eq.{worker}&select=*", ot); check("owner reads profile back", s == 200 and r[0]["bio"] == "updated" and r[0]["services"] == ["KITCHEN", "GENERAL"])
s, r = call("POST", "/rest/v1/workers?on_conflict=id", wt, dict(prof, rating_count=99), {"Prefer": "resolution=merge-duplicates,return=minimal"}); check("rating totals cannot be sent", s >= 400)
s, r = call("POST", "/rest/v1/workers?on_conflict=id", ot, dict(prof, id=owner), {"Prefer": "resolution=merge-duplicates,return=minimal"}); check("owner cannot create a worker profile", s >= 400)

# 2. availability
exp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + 3600))
s, r = call("POST", "/rest/v1/rpc/set_availability", ot, {"p_open": True, "p_geohash": "ezs42e", "p_expires": exp}); check("an owner has no worker row to open", s == 400 and "profile" in r["message"])
s, r = call("POST", "/rest/v1/rpc/set_availability", wt, {"p_open": True, "p_geohash": "ezs42ezs42", "p_expires": exp}); check("too-precise location refused", s >= 400)
s, r = call("POST", "/rest/v1/rpc/set_availability", wt, {"p_open": True, "p_geohash": "ezs42e", "p_expires": exp}); check("worker goes open", s == 200 and r["open"] is True and r["geohash"] == "ezs42e")
now = urllib.parse.quote(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
q = urllib.parse.quote("(geohash.like.ezs42*,geohash.like.ezs43*)")
s, r = call("GET", f"/rest/v1/workers?select=*&open=eq.true&expires_at=gt.{now}&or={q}", ot); check("nearby search finds the open worker", s == 200 and [x["id"] for x in r] == [worker])
q2 = urllib.parse.quote("(geohash.like.u4pru*)")
s, r = call("GET", f"/rest/v1/workers?select=*&open=eq.true&expires_at=gt.{now}&or={q2}", ot); check("search elsewhere finds nobody", s == 200 and r == [])
s, r = call("GET", "/rest/v1/workers?select=*", None); check("anonymous requests are refused", s >= 400)

# 3. booking
s, r = call("POST", "/rest/v1/rpc/request_booking", ot, {"p_worker": worker, "p_requested_for": "now", "p_area": "Edappally", "p_size": "2BHK"})
check("request_booking returns one booking object", s == 200 and isinstance(r, dict) and r["status"] == "REQUESTED" and r["worker_phone"] is None)
bid = r["id"]
s, r = call("POST", "/rest/v1/rpc/request_booking", ot, {"p_worker": worker, "p_requested_for": "now", "p_area": "x", "p_size": "y"}); check("duplicate request gives a readable error", s == 400 and "already have a booking" in r["message"])
s, r = call("POST", "/rest/v1/rpc/expire_stale_bookings", ot, {}); check("expire_stale_bookings callable", s in (200, 204))
s, r = call("GET", "/rest/v1/bookings?order=created_at.desc", wt); check("worker sees the incoming request", s == 200 and len(r) == 1 and r[0]["owner_name"] == "Omar")
s, r = call("POST", "/rest/v1/rpc/booking_action", ot, {"p_booking": bid, "p_action": "ACCEPT"}); check("owner cannot accept (readable error)", s == 400 and "not allowed" in r["message"])
s, r = call("POST", "/rest/v1/rpc/booking_action", wt, {"p_booking": bid, "p_action": "ACCEPT"}); check("worker accepts, phones appear", s == 200 and r["status"] == "ACCEPTED" and r["owner_phone"] == "9100000001" and r["worker_phone"] == "9100000002")
for who, act in ((ot, "ARRIVE"), (wt, "START")):
    s, r = call("POST", "/rest/v1/rpc/booking_action", who, {"p_booking": bid, "p_action": act, "p_job": "job-9" if act == "START" else None}); check(act.lower(), s == 200)
ev = {"billFingerprint": "a" * 64, "totalPriceLowInr": 400, "totalPriceHighInr": 520, "roomCount": 1, "photoCount": 4, "afterPhotoHashes": ["b" * 64], "generatedAtIso": "2026-10-24T05:00:00Z"}
s, r = call("POST", "/rest/v1/rpc/booking_action", wt, {"p_booking": bid, "p_action": "COMPLETE", "p_evidence": ev, "p_job": None}); check("complete with an evidence summary", s == 200 and r["evidence"]["afterPhotoHashes"] == ["b" * 64])
s, r = call("POST", "/rest/v1/rpc/rate_booking", ot, {"p_booking": bid, "p_stars": 5, "p_tags": ["on_time", "careful"], "p_comment": "Great"}); check("rate_booking returns a verified rating", s == 200 and r["verified"] is True and r["tags"] == ["on_time", "careful"])
s, r = call("GET", f"/rest/v1/ratings?worker_id=eq.{worker}&order=created_at.desc", ot); check("ratings list readable", s == 200 and len(r) == 1 and r[0]["stars"] == 5)
s, r = call("GET", f"/rest/v1/workers?id=eq.{worker}&select=*", ot); check("worker totals updated", r[0]["rating_count"] == 1 and r[0]["verified_count"] == 1 and r[0]["completed_count"] == 1 and r[0]["weight_sum"] == 1.0)
s, r = call("GET", "/rest/v1/bookings?order=created_at.desc", ot); check("final status RATED in owner's list", r[0]["status"] == "RATED")
print(f"\n{passed} passed, {failed} failed"); sys.exit(1 if failed else 0)
