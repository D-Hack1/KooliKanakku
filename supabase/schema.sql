-- KooliKanaku Find-a-worker: Supabase schema (extra_feature.md), on REAL Supabase Auth.
--
-- HOW TO RUN: Supabase dashboard -> project gbjeijdbjoohjiohmuih -> SQL Editor -> New query ->
-- paste this whole file -> Run. Safe to re-run (CREATE OR REPLACE / IF NOT EXISTS throughout).
--
-- REQUIRED PROJECT SETTING: Authentication -> Providers -> Email -> turn OFF "Confirm email".
-- The app signs up with a synthetic, non-deliverable address (the phone number as the local part,
-- e.g. 9123456789@kooli.koolikanaku.app) because Supabase has no SMS provider configured
-- (extra_feature.md Section 16). With email confirmation ON, that signup would be stuck waiting
-- for a link that can never arrive. The 6-digit PIN is the Auth password (meets the default
-- 6-character minimum).
--
-- WHY THIS IS NOW "REAL" AUTH (vs. the first version of this file): every RPC function below reads
-- auth.uid() - the verified identity Supabase Auth put on the request's JWT - instead of trusting a
-- client-supplied uid parameter. A client holding only the anon key cannot forge another user's
-- identity. The UI-level "phone number + 6-digit PIN" experience is unchanged; what changed is
-- what proves identity underneath it.

-- ---------------- profiles (one row per auth.users row) ----------------

create table if not exists profiles (
    uid uuid primary key references auth.users(id) on delete cascade,
    phone text not null unique,
    role text not null check (role in ('OWNER', 'WORKER')),
    name text not null,
    created_at timestamptz not null default now()
);

create table if not exists workers (
    uid uuid primary key references profiles(uid) on delete cascade,
    name text not null,
    languages text[] not null default '{}',
    services text[] not null default '{}',
    rate_min numeric not null default 0,
    rate_max numeric not null default 0,
    bio text not null default ''
);

create table if not exists availability (
    uid uuid primary key references profiles(uid) on delete cascade,
    open boolean not null default false,
    geohash text,
    area_label text,
    expires_at timestamptz,
    updated_at timestamptz not null default now()
);

create table if not exists bookings (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references profiles(uid),
    worker_id uuid not null references profiles(uid),
    owner_name text not null,
    worker_name text not null,
    status text not null default 'REQUESTED'
        check (status in ('REQUESTED','ACCEPTED','DECLINED','EXPIRED','CANCELLED','ARRIVED','IN_PROGRESS','COMPLETED','RATED')),
    requested_for text not null,
    area_label text not null,
    size_hint text not null,
    created_at timestamptz not null default now(),
    accepted_at timestamptz,
    arrived_at timestamptz,
    completed_at timestamptz,
    evidence jsonb,
    cancel_reason text
);

create table if not exists ratings (
    booking_id uuid primary key references bookings(id),
    worker_id uuid not null references profiles(uid),
    owner_id uuid not null references profiles(uid),
    stars int not null check (stars between 1 and 5),
    tags text[] not null default '{}',
    comment text not null default '',
    verified boolean not null default false,
    created_at timestamptz not null default now()
);

alter table profiles enable row level security;
alter table workers enable row level security;
alter table availability enable row level security;
alter table bookings enable row level security;
alter table ratings enable row level security;

-- A signed-in user can read (and the complete_signup function can write) only their own profile row.
drop policy if exists profiles_self_read on profiles;
create policy profiles_self_read on profiles for select using (uid = auth.uid());

-- Public, read-only: a worker's profile and rating history are meant to be browsable
-- (extra_feature.md Section 6.1: "Visible to: owners browsing" / "public on the worker").
-- Phone numbers (profiles) and raw bookings/availability are never directly selectable by anon.
drop policy if exists workers_public_read on workers;
create policy workers_public_read on workers for select using (true);

drop policy if exists ratings_public_read on ratings;
create policy ratings_public_read on ratings for select using (true);

-- `availability` has no public policy (it would leak full-precision geohashes). open_workers
-- exposes only a 5-character geohash prefix (~5 km cell - Section 5.1's own precision guidance),
-- which is all the app needs to compute a coarse "under 1 km / about 3 km" distance band.
create or replace view open_workers as
    select
        w.uid, w.name, w.languages, w.services, w.rate_min, w.rate_max, w.bio,
        left(a.geohash, 5) as geohash5,
        coalesce(r.rating_avg, 0) as rating_avg,
        coalesce(r.rating_count, 0) as rating_count,
        coalesce(r.verified_count, 0) as verified_count
    from workers w
    join availability a on a.uid = w.uid
    left join (
        select worker_id, avg(stars)::float8 as rating_avg, count(*) as rating_count,
               count(*) filter (where verified) as verified_count
        from ratings group by worker_id
    ) r on r.worker_id = w.uid
    where a.open and (a.expires_at is null or a.expires_at > now());

grant select on open_workers to anon, authenticated;

-- ---------------- RPC functions (called via POST /rest/v1/rpc/<name>, with the user's own JWT) ----------------
-- None of these take an actor/owner/uid parameter for "who is asking" anymore - every one reads
-- auth.uid(), which PostgREST sets from the caller's verified Supabase Auth JWT.

-- Lets the sign-up form tell the user "this number already has an account" before they submit,
-- without exposing anything about the account itself (no phone/profile data leaves this function -
-- Section 5.1 "Phone numbers ... are never directly selectable by anon").
create or replace function phone_exists(p_phone text)
returns boolean language sql security definer as $$
    select exists(select 1 from profiles where phone = p_phone);
$$;
grant execute on function phone_exists(text) to anon, authenticated;

create or replace function complete_signup(p_phone text, p_role text, p_name text)
returns profiles language plpgsql security definer as $$
declare p profiles;
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    insert into profiles (uid, phone, role, name) values (auth.uid(), p_phone, p_role, p_name)
        on conflict (uid) do update set phone = excluded.phone, role = excluded.role, name = excluded.name
        returning * into p;
    if p_role = 'WORKER' then
        insert into workers (uid, name) values (auth.uid(), p_name) on conflict (uid) do nothing;
        insert into availability (uid, open) values (auth.uid(), false) on conflict (uid) do nothing;
    end if;
    return p;
end; $$;

create or replace function my_profile()
returns profiles language sql security definer as $$
    select * from profiles where uid = auth.uid();
$$;

-- Lets a worker's own client know whether it's still listed as open after a restart, without
-- needing a public-read policy on `availability` (which would otherwise leak geohashes - Section 5.1).
create or replace function my_availability()
returns availability language sql security definer as $$
    select * from availability where uid = auth.uid();
$$;

create or replace function set_availability(p_open boolean, p_geohash text, p_area_label text, p_expires_at timestamptz)
returns void language plpgsql security definer as $$
begin
    if not exists (select 1 from profiles where uid = auth.uid() and role = 'WORKER') then
        raise exception 'Only a worker can set availability';
    end if;
    insert into availability (uid, open, geohash, area_label, expires_at, updated_at)
        values (auth.uid(), p_open, p_geohash, p_area_label, p_expires_at, now())
        on conflict (uid) do update set
            open = excluded.open, geohash = excluded.geohash, area_label = excluded.area_label,
            expires_at = excluded.expires_at, updated_at = now();
end; $$;

create or replace function save_worker_profile(p_name text, p_languages text[], p_services text[], p_rate_min numeric, p_rate_max numeric, p_bio text)
returns void language plpgsql security definer as $$
begin
    insert into workers (uid, name, languages, services, rate_min, rate_max, bio)
        values (auth.uid(), p_name, p_languages, p_services, p_rate_min, p_rate_max, p_bio)
        on conflict (uid) do update set
            name = excluded.name, languages = excluded.languages, services = excluded.services,
            rate_min = excluded.rate_min, rate_max = excluded.rate_max, bio = excluded.bio;
end; $$;

create or replace function get_worker_profile(p_uid uuid)
returns workers language sql security definer as $$
    select * from workers where uid = p_uid;
$$;

create or replace function request_booking(p_worker_id uuid, p_when text, p_area_label text, p_size_hint text)
returns bookings language plpgsql security definer as $$
declare b bookings; v_owner_name text; v_worker_name text;
begin
    select name into v_owner_name from profiles where uid = auth.uid();
    select name into v_worker_name from profiles where uid = p_worker_id;
    if v_owner_name is null or v_worker_name is null then
        raise exception 'Unknown owner or worker';
    end if;
    insert into bookings (owner_id, worker_id, owner_name, worker_name, status, requested_for, area_label, size_hint)
        values (auth.uid(), p_worker_id, v_owner_name, v_worker_name, 'REQUESTED', p_when, p_area_label, p_size_hint)
        returning * into b;
    return b;
end; $$;

create or replace function get_bookings_for_user()
returns setof bookings language sql security definer as $$
    select * from bookings where owner_id = auth.uid() or worker_id = auth.uid() order by created_at desc;
$$;

create or replace function get_booking(p_booking_id uuid)
returns bookings language sql security definer as $$
    select * from bookings where id = p_booking_id and (owner_id = auth.uid() or worker_id = auth.uid());
$$;

create or replace function booking_action(p_booking_id uuid, p_action text)
returns bookings language plpgsql security definer as $$
declare b bookings;
begin
    select * into b from bookings where id = p_booking_id;
    if not found then raise exception 'Booking not found'; end if;
    if b.owner_id <> auth.uid() and b.worker_id <> auth.uid() then
        raise exception 'Not a party to this booking';
    end if;

    if p_action = 'accept' and b.worker_id = auth.uid() then
        update bookings set status = 'ACCEPTED', accepted_at = now() where id = p_booking_id returning * into b;
    elsif p_action = 'decline' and b.worker_id = auth.uid() then
        update bookings set status = 'DECLINED' where id = p_booking_id returning * into b;
    elsif p_action = 'arrived' then
        update bookings set status = 'ARRIVED', arrived_at = now() where id = p_booking_id returning * into b;
    elsif p_action = 'in_progress' then
        update bookings set status = 'IN_PROGRESS' where id = p_booking_id returning * into b;
    else
        raise exception 'Unknown or unauthorized action: %', p_action;
    end if;
    return b;
end; $$;

create or replace function cancel_booking(p_booking_id uuid, p_reason text)
returns bookings language plpgsql security definer as $$
declare b bookings;
begin
    select * into b from bookings where id = p_booking_id and (owner_id = auth.uid() or worker_id = auth.uid());
    if not found then raise exception 'Booking not found'; end if;
    update bookings set status = 'CANCELLED', cancel_reason = p_reason where id = p_booking_id returning * into b;
    return b;
end; $$;

create or replace function complete_booking(p_booking_id uuid, p_evidence jsonb)
returns bookings language plpgsql security definer as $$
declare b bookings;
begin
    update bookings set status = 'COMPLETED', completed_at = now(), evidence = p_evidence
        where id = p_booking_id and (owner_id = auth.uid() or worker_id = auth.uid())
        returning * into b;
    if not found then raise exception 'Booking not found'; end if;
    return b;
end; $$;

create or replace function rate_booking(p_booking_id uuid, p_stars int, p_tags text[], p_comment text)
returns ratings language plpgsql security definer as $$
declare b bookings; r ratings;
begin
    select * into b from bookings where id = p_booking_id and owner_id = auth.uid();
    if not found then raise exception 'Not this owner''s booking'; end if;
    insert into ratings (booking_id, worker_id, owner_id, stars, tags, comment, verified)
        values (p_booking_id, b.worker_id, auth.uid(), p_stars, p_tags, p_comment, b.evidence is not null)
        on conflict (booking_id) do update set
            stars = excluded.stars, tags = excluded.tags, comment = excluded.comment, verified = excluded.verified
        returning * into r;
    update bookings set status = 'RATED' where id = p_booking_id;
    return r;
end; $$;

create or replace function get_counterpart_phone(p_booking_id uuid)
returns text language plpgsql security definer as $$
declare b bookings; counterpart uuid;
begin
    select * into b from bookings where id = p_booking_id;
    if not found then raise exception 'Booking not found'; end if;
    if b.status not in ('ACCEPTED','ARRIVED','IN_PROGRESS','COMPLETED','RATED') then
        raise exception 'Phone number is shared only after acceptance';
    end if;
    if b.owner_id = auth.uid() then counterpart := b.worker_id;
    elsif b.worker_id = auth.uid() then counterpart := b.owner_id;
    else raise exception 'Not a party to this booking'; end if;
    return (select phone from profiles where uid = counterpart);
end; $$;

grant execute on all functions in schema public to anon, authenticated;
grant usage on schema public to anon, authenticated;

-- No sample-worker seed block here: under this schema, `workers.uid` -> `profiles.uid` ->
-- `auth.users.id`, so a seeded worker would need a real Supabase Auth account, which a plain
-- INSERT can't fake safely. Nearby Workers isn't empty on a fresh phone anyway - the on-device
-- demo backend (DemoConnectRepository) already seeds its own clearly-labelled sample workers for
-- that cold-start case (extra_feature.md Section 2), and this Supabase backend only takes over
-- once you've created at least one real worker account through the app itself.

-- ---------------- migrating from the first (non-Auth) version of this file ----------------
-- If you already ran the earlier schema.sql, drop its tables first so the new `profiles`-based
-- ones (which reference auth.users) can be created cleanly:
--   drop table if exists ratings, bookings, availability, workers, users cascade;
--   drop function if exists sign_up, sign_in cascade;
-- then run this whole file.
