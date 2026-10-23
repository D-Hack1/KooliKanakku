import psycopg2, uuid, json, sys
conn = psycopg2.connect(host="127.0.0.1", port=54329, user="postgres", password="test", dbname="postgres")
conn.autocommit = True
cur = conn.cursor()

# ---- stand-ins for what Supabase provides ----
cur.execute("""
drop schema if exists public cascade; create schema public; grant usage on schema public to public;
drop schema if exists auth cascade; create schema auth;
do $$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
end $$;
grant usage on schema public to anon, authenticated;
grant usage on schema auth to anon, authenticated;
create table auth.users (id uuid primary key default gen_random_uuid(), email text, raw_user_meta_data jsonb);
create function auth.uid() returns uuid language sql stable as $$ select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid $$;
grant execute on function auth.uid() to anon, authenticated;
""")
cur.execute(open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "schema.sql")).read())
# running it a second time must be harmless
cur.execute(open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "schema.sql")).read())

passed = failed = 0
def check(name, cond):
    global passed, failed
    if cond: passed += 1; print("  ok  ", name)
    else: failed += 1; print("  FAIL", name)

def signup(role, name, phone):
    uid = str(uuid.uuid4())
    cur.execute("insert into auth.users (id, email, raw_user_meta_data) values (%s,%s,%s)", (uid, phone+"@x", json.dumps({"role":role,"name":name,"phone":phone})))
    return uid

class As:
    def __init__(self, uid): self.uid = uid
    def __enter__(self):
        cur.execute("begin"); cur.execute("set local role authenticated"); cur.execute("select set_config('request.jwt.claim.sub', %s, true)", (self.uid,)); return self
    def __exit__(self, *a):
        cur.execute("rollback" if a[0] else "commit"); cur.execute("reset role")

def run(uid, sql, args=()):
    """Run as a signed-in user. Returns (rows, error)."""
    try:
        cur.execute("begin"); cur.execute("set local role authenticated"); cur.execute("select set_config('request.jwt.claim.sub', %s, true)", (uid,))
        cur.execute(sql, args)
        rows = cur.fetchall() if cur.description else []
        cur.execute("commit"); return rows, None
    except Exception as e:
        cur.execute("rollback"); return None, str(e).strip().splitlines()[0]
    finally:
        cur.execute("reset role") if False else None

def admin(sql, args=()):
    cur.execute(sql, args); return cur.fetchall() if cur.description else []

print("accounts")
owner = signup("OWNER", "Omar", "9000000009"); other = signup("OWNER", "Eve", "9000000010")
w = signup("WORKER", "Asha", "9000000001"); w2 = signup("WORKER", "Bina", "9000000002")
check("profiles created from signup metadata", admin("select count(*) from profiles")[0][0] == 4)
try:
    signup("ADMIN", "Hax", "9000000099"); check("bad role rejected", False)
except Exception: check("bad role rejected", True)
try:
    signup("OWNER", "Dup", "9000000009"); check("duplicate phone rejected", False)
except Exception: check("duplicate phone rejected", True)

print("worker profile and availability")
_, e = run(w, "insert into workers (id, name, rate_min, rate_max, services) values (%s,'Asha',200,300,'{GENERAL}')", (w,)); check("worker creates own profile", e is None)
_, e = run(owner, "insert into workers (id, name, rate_min, rate_max) values (%s,'Omar',200,300)", (owner,)); check("owner cannot create a worker profile", e is not None)
_, e = run(w2, "insert into workers (id, name, rate_min, rate_max) values (%s,'Hijack',200,300)", (w,)); check("cannot create a profile for someone else", e is not None)
_, e = run(w, "insert into workers (id, name, rate_min, rate_max, rating_count) values (%s,'x',1,2,99)", (w2,)); check("cannot set rating totals on insert", e is not None)
_, e = run(w, "update workers set rating_count = 500, weighted_star_sum = 2500, weight_sum = 500 where id=%s", (w,)); check("cannot edit own rating totals", e is not None)
_, e = run(w, "update workers set is_demo = true where id=%s", (w,)); check("cannot flag self as demo", e is not None)
_, e = run(w, "update workers set open=true where id=%s", (w,)); check("open needs location and expiry", e is not None)
_, e = run(w, "update workers set open=true, geohash='ezs42ezs42', expires_at=now()+interval '1 hour' where id=%s", (w,)); check("geohash longer than 6 rejected", e is not None)
_, e = run(w, "update workers set open=true, geohash='ezs42e', expires_at=now()+interval '3 days' where id=%s", (w,)); check("expiry beyond 24h rejected", e is not None)
_, e = run(w, "update workers set open=true, geohash='ezs42e', expires_at=now()+interval '1 hour' where id=%s", (w,)); check("worker goes open", e is None)
_, e = run(w2, "update workers set name='Pwned' where id=%s", (w,)); rows = admin("select name from workers where id=%s", (w,)); check("another user cannot edit my profile", rows[0][0] == "Asha")
rows, e = run(owner, "select name, geohash from workers where open and geohash like 'ezs4%%'"); check("owner can search open workers", e is None and len(rows) == 1)
rows, e = run(owner, "select phone from profiles where id=%s", (w,)); check("owner cannot read a worker's phone from profiles", e is None and rows == [])
rows, e = run(w, "select phone from profiles"); check("a user sees only their own profile", e is None and len(rows) == 1)
_, e = run(w, "update workers set open=false where id=%s", (w,)); rows = admin("select geohash, expires_at from workers where id=%s", (w,)); check("closing clears location", rows[0] == (None, None))
_, e = run(w, "update workers set open=true, geohash='ezs42e', expires_at=now()+interval '1 hour' where id=%s", (w,))

print("booking flow")
_, e = run(w, "select * from request_booking(%s,'now','Edappally','2BHK')", (w,)); check("a worker cannot book", e is not None)
_, e = run(owner, "select * from request_booking(%s,'now','x','y')", (w2,)); check("cannot book a worker who is not open", e is not None)
rows, e = run(owner, "select id, status, worker_phone, owner_phone from request_booking(%s,'now','Edappally','2BHK')", (w,)); bid = rows[0][0]
check("request created", e is None and rows[0][1] == "REQUESTED")
check("no phones before acceptance", rows[0][2] is None and rows[0][3] is None)
_, e = run(owner, "select * from request_booking(%s,'now','x','y')", (w,)); check("no duplicate active booking", e is not None)
rows, e = run(other, "select * from bookings"); check("a stranger cannot see the booking", rows == [])
_, e = run(other, "select * from booking_action(%s,'CANCEL')", (bid,)); check("a stranger cannot act on it", e is not None)
_, e = run(owner, "select * from booking_action(%s,'ACCEPT')", (bid,)); check("owner cannot accept", e is not None)
_, e = run(owner, "update bookings set status='COMPLETED' where id=%s", (bid,)); check("direct status edits are blocked", e is not None)
rows, e = run(w, "select status, worker_phone, owner_phone from booking_action(%s,'ACCEPT')", (bid,)); check("worker accepts", e is None and rows[0][0] == "ACCEPTED")
check("phones revealed after acceptance", rows[0][1] == "9000000001" and rows[0][2] == "9000000009")
_, e = run(owner, "select * from booking_action(%s,'START')", (bid,)); check("cannot skip arrival", e is not None)
rows, e = run(owner, "select status from booking_action(%s,'ARRIVE')", (bid,)); check("owner marks arrival", rows[0][0] == "ARRIVED")
_, e = run(w, "select * from booking_action(%s,'CANCEL')", (bid,)); check("no cancelling after arrival", e is not None)
rows, e = run(w, "select status, job_id from booking_action(%s,'START', null, 'job-1')", (bid,)); check("worker starts the job", rows[0] == ("IN_PROGRESS", "job-1"))
_, e = run(owner, "select * from rate_booking(%s,5,'{x}','hi')", (bid,)); check("cannot rate before completion", e is not None)
evidence = json.dumps({"billFingerprint": "a"*64, "afterPhotoHashes": ["b"*64], "roomCount": 1})
_, e = run(w, "select * from booking_action(%s,'COMPLETE', %s::jsonb)", (bid, "[1,2]")); check("evidence must be an object", e is not None)
rows, e = run(w, "select status from booking_action(%s,'COMPLETE', %s::jsonb)", (bid, evidence)); check("job completed", rows[0][0] == "COMPLETED")
_, e = run(w, "select * from rate_booking(%s,5,'{x}','self')", (bid,)); check("worker cannot rate themselves", e is not None)
_, e = run(other, "select * from rate_booking(%s,5,'{x}','x')", (bid,)); check("stranger cannot rate", e is not None)
_, e = run(owner, "select * from rate_booking(%s,6,'{x}','x')", (bid,)); check("six stars rejected", e is not None)
rows, e = run(owner, "select stars, verified from rate_booking(%s,5,'{on_time}','Great')", (bid,)); check("owner rates with a verified rating", e is None and rows[0] == (5, True))
_, e = run(owner, "select * from rate_booking(%s,4,'{}','again')", (bid,)); check("only one rating per booking", e is not None)
rows = admin("select rating_count, verified_count, weighted_star_sum, weight_sum, completed_count from workers where id=%s", (w,))
check("worker totals updated by the rating", rows[0] == (1, 1, 5.0, 1.0, 1))
check("booking is rated", admin("select status from bookings where id=%s", (bid,))[0][0] == "RATED")

print("unverified rating, cancellations, busy worker, expiry")
rows, _ = run(other, "select id from request_booking(%s,'now','Kakkanad','1 room')", (w,)); b2 = rows[0][0]
run(w, "select * from booking_action(%s,'ACCEPT')", (b2,)); run(w, "select * from booking_action(%s,'ARRIVE')", (b2,)); run(w, "select * from booking_action(%s,'START')", (b2,))
run(w, "select * from booking_action(%s,'COMPLETE')", (b2,))
rows, e = run(other, "select verified from rate_booking(%s,4,'{}','')", (b2,)); check("no evidence gives an unverified rating", e is None and rows[0][0] is False)
rows = admin("select rating_count, verified_count, weighted_star_sum, weight_sum from workers where id=%s", (w,)); check("unverified rating counts half", rows[0] == (2, 1, 5.0 + 0.5*4, 1.5))

rows, _ = run(owner, "select id from request_booking(%s,'now','a','b')", (w,)); b3 = rows[0][0]
rows, _ = run(other, "select id from request_booking(%s,'now','a','b')", (w,)); b4 = rows[0][0]
_, e = run(w, "select * from booking_action(%s,'ACCEPT')", (b3,)); check("accept first", e is None)
_, e = run(w, "select * from booking_action(%s,'ACCEPT')", (b4,)); check("busy worker cannot accept a second job", e is not None)
run(w, "select * from booking_action(%s,'CANCEL')", (b3,))
check("worker cancel after accepting counts against reliability", admin("select cancel_count from workers where id=%s", (w,))[0][0] == 1)
_, e = run(w, "select * from booking_action(%s,'ACCEPT')", (b4,)); check("free again after cancelling", e is None)
run(other, "select * from booking_action(%s,'CANCEL')", (b4,))
check("an owner cancelling does not hurt the worker", admin("select cancel_count from workers where id=%s", (w,))[0][0] == 1)

rows, _ = run(owner, "select id from request_booking(%s,'now','a','b')", (w,)); b5 = rows[0][0]
admin("update bookings set created_at = now() - interval '11 minutes' where id=%s", (b5,))
_, e = run(w, "select * from booking_action(%s,'ACCEPT')", (b5,)); check("late accept refused", e is not None and "expired" in e)
run(owner, "select expire_stale_bookings()")
check("stale request marked expired", admin("select status from bookings where id=%s", (b5,))[0][0] == "EXPIRED")

print("ratings are public, bookings private")
rows, e = run(other, "select count(*) from ratings where worker_id=%s", (w,)); check("any signed-in user can read a worker's ratings", rows[0][0] == 2)
rows, e = run(other, "select count(*) from bookings where owner_id <> auth.uid() and worker_id <> auth.uid()"); check("other users' bookings stay hidden", rows[0][0] == 0)

print("anonymous access")
try:
    cur.execute("begin"); cur.execute("set local role anon"); cur.execute("select * from workers"); cur.execute("commit"); check("anonymous users cannot read workers", False)
except Exception: cur.execute("rollback"); check("anonymous users cannot read workers", True)
print(f"\n{passed} passed, {failed} failed"); sys.exit(1 if failed else 0)
