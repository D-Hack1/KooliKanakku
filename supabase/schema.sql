-- KooliKanakku connected layer: Supabase schema.
-- Paste this whole file into Supabase > SQL Editor > New query > Run. Safe to run again.
-- Server-side rules live here, so a modified or buggy app cannot break them:
--   * phone numbers are revealed only after a worker accepts
--   * only the right party can move a booking to the next state
--   * one rating per booking, only by the owner, only after completion
--   * a worker's rating totals can only change through rate_booking()
--   * a worker's location is stored as a coarse geohash (at most 6 characters)

-- ---------------------------------------------------------------- tables

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  role text not null check (role in ('OWNER', 'WORKER')),
  name text not null check (char_length(name) between 1 and 60),
  phone text not null unique check (phone ~ '^[0-9]{10,15}$'),
  created_at timestamptz not null default now()
);

create table if not exists public.workers (
  id uuid primary key references public.profiles (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  languages text[] not null default '{}',
  services text[] not null default '{}',
  rate_min integer not null check (rate_min > 0),
  rate_max integer not null check (rate_max >= rate_min),
  bio text not null default '' check (char_length(bio) <= 280),
  is_demo boolean not null default false,
  open boolean not null default false,
  geohash text check (geohash is null or char_length(geohash) between 1 and 6),
  expires_at timestamptz,
  rating_count integer not null default 0,
  verified_count integer not null default 0,
  weighted_star_sum double precision not null default 0,
  weight_sum double precision not null default 0,
  completed_count integer not null default 0,
  cancel_count integer not null default 0,
  updated_at timestamptz not null default now(),
  check (not open or (geohash is not null and expires_at is not null))
);
create index if not exists workers_open_geohash_idx on public.workers (geohash text_pattern_ops) where open;

create table if not exists public.bookings (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id),
  worker_id uuid not null references public.profiles (id),
  owner_name text not null,
  worker_name text not null,
  status text not null default 'REQUESTED'
    check (status in ('REQUESTED','ACCEPTED','DECLINED','EXPIRED','CANCELLED','ARRIVED','IN_PROGRESS','COMPLETED','RATED')),
  requested_for text not null check (char_length(requested_for) <= 40),
  area_label text not null check (char_length(area_label) <= 80),
  size_hint text not null default '' check (char_length(size_hint) <= 80),
  owner_phone text,
  worker_phone text,
  cancelled_by text check (cancelled_by in ('OWNER', 'WORKER')),
  evidence jsonb,
  job_id text check (job_id is null or char_length(job_id) <= 64),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (owner_id <> worker_id)
);
create index if not exists bookings_owner_idx on public.bookings (owner_id, created_at desc);
create index if not exists bookings_worker_idx on public.bookings (worker_id, created_at desc);

create table if not exists public.ratings (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null unique references public.bookings (id),
  worker_id uuid not null references public.profiles (id),
  owner_id uuid not null references public.profiles (id),
  stars integer not null check (stars between 1 and 5),
  tags text[] not null default '{}',
  comment text not null default '' check (char_length(comment) <= 280),
  verified boolean not null,
  created_at timestamptz not null default now()
);
create index if not exists ratings_worker_idx on public.ratings (worker_id, created_at desc);

-- ---------------------------------------------------------------- new accounts

-- Supabase calls this when a user signs up. The app sends name, role and phone as user metadata.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
begin
  insert into public.profiles (id, role, name, phone)
  values (new.id, meta ->> 'role', meta ->> 'name', meta ->> 'phone');
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- guards

-- A visible worker must have an expiry within the next 24 hours.
create or replace function public.workers_guard()
returns trigger language plpgsql as $$
begin
  if new.open and (new.expires_at is null or new.expires_at <= now() or new.expires_at > now() + interval '24 hours') then
    raise exception 'Availability must expire within the next 24 hours';
  end if;
  if not new.open then
    new.geohash := null;
    new.expires_at := null;
  end if;
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists workers_guard_trg on public.workers;
create trigger workers_guard_trg before insert or update on public.workers
  for each row execute function public.workers_guard();

-- Worker goes open or closed. The app uses this instead of editing the row (PATCH) directly.
create or replace function public.set_availability(p_open boolean, p_geohash text, p_expires timestamptz)
returns public.workers language plpgsql set search_path = public as $$
declare
  w public.workers;
begin
  update public.workers
     set open = p_open,
         geohash = case when p_open then p_geohash end,
         expires_at = case when p_open then p_expires end
   where id = auth.uid()
   returning * into w;
  if not found then raise exception 'Create your profile first'; end if;
  return w;
end;
$$;

-- ---------------------------------------------------------------- booking rules

-- Mirrors the Kotlin BookingStateMachine. Returns null when the move is not allowed.
create or replace function public.next_booking_status(p_status text, p_action text, p_actor text)
returns text language sql immutable as $$
  select case
    when p_status = 'REQUESTED' and p_action = 'ACCEPT'  and p_actor = 'WORKER' then 'ACCEPTED'
    when p_status = 'REQUESTED' and p_action = 'DECLINE' and p_actor = 'WORKER' then 'DECLINED'
    when p_status = 'REQUESTED' and p_action = 'CANCEL'  and p_actor = 'OWNER'  then 'CANCELLED'
    when p_status = 'REQUESTED' and p_action = 'EXPIRE'  and p_actor = 'SYSTEM' then 'EXPIRED'
    when p_status = 'ACCEPTED'  and p_action = 'ARRIVE'  and p_actor in ('OWNER', 'WORKER') then 'ARRIVED'
    when p_status = 'ACCEPTED'  and p_action = 'CANCEL'  and p_actor in ('OWNER', 'WORKER') then 'CANCELLED'
    when p_status = 'ARRIVED'   and p_action = 'START'   and p_actor in ('OWNER', 'WORKER') then 'IN_PROGRESS'
    when p_status = 'IN_PROGRESS' and p_action = 'COMPLETE' and p_actor in ('OWNER', 'WORKER') then 'COMPLETED'
    when p_status = 'COMPLETED' and p_action = 'RATE'    and p_actor = 'OWNER'  then 'RATED'
    else null
  end;
$$;

-- Marks unanswered requests (older than 10 minutes) as expired. The app calls this before reading.
create or replace function public.expire_stale_bookings()
returns void language sql security definer set search_path = public as $$
  update public.bookings
     set status = 'EXPIRED', updated_at = now()
   where status = 'REQUESTED' and created_at < now() - interval '10 minutes';
$$;

create or replace function public.request_booking(p_worker uuid, p_requested_for text, p_area text, p_size text)
returns public.bookings language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  owner_row public.profiles;
  worker_row public.workers;
  b public.bookings;
begin
  if uid is null then raise exception 'Sign in first'; end if;
  select * into owner_row from public.profiles where id = uid;
  if not found or owner_row.role <> 'OWNER' then raise exception 'Only an owner account can book'; end if;
  select * into worker_row from public.workers where id = p_worker;
  if not found then raise exception 'Worker not found'; end if;
  if not worker_row.open or worker_row.expires_at is null or worker_row.expires_at <= now() then
    raise exception 'This worker is no longer available';
  end if;
  if exists (select 1 from public.bookings x
              where x.owner_id = uid and x.worker_id = p_worker
                and x.status in ('REQUESTED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS')) then
    raise exception 'You already have a booking with this worker';
  end if;
  insert into public.bookings (owner_id, worker_id, owner_name, worker_name, requested_for, area_label, size_hint)
  values (uid, p_worker, owner_row.name, worker_row.name, left(coalesce(p_requested_for, 'now'), 40), left(coalesce(p_area, ''), 80), left(coalesce(p_size, ''), 80))
  returning * into b;
  return b;
end;
$$;

create or replace function public.booking_action(p_booking uuid, p_action text, p_evidence jsonb default null, p_job text default null)
returns public.bookings language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  b public.bookings;
  actor text;
  nxt text;
  reveal boolean;
begin
  if uid is null then raise exception 'Sign in first'; end if;
  select * into b from public.bookings where id = p_booking for update;
  if not found then raise exception 'Booking not found'; end if;
  if uid <> b.owner_id and uid <> b.worker_id then raise exception 'Not your booking'; end if;
  if p_action = 'RATE' then raise exception 'Use rate_booking to rate'; end if;
  actor := case when uid = b.owner_id then 'OWNER' else 'WORKER' end;

  if b.status = 'REQUESTED' and b.created_at <= now() - interval '10 minutes' then
    raise exception 'This request has expired';
  end if;
  nxt := public.next_booking_status(b.status, p_action, actor);
  if nxt is null then
    raise exception '% is not allowed while %', lower(p_action), lower(b.status);
  end if;

  if p_action = 'ACCEPT' and exists (
       select 1 from public.bookings x
        where x.worker_id = b.worker_id and x.id <> b.id and x.status in ('ACCEPTED', 'ARRIVED', 'IN_PROGRESS')) then
    raise exception 'Finish your current job before accepting another';
  end if;
  if p_action = 'COMPLETE' and p_evidence is not null
     and (jsonb_typeof(p_evidence) <> 'object' or length(p_evidence::text) > 4096) then
    raise exception 'Invalid evidence summary';
  end if;

  reveal := nxt in ('ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'RATED');
  update public.bookings set
    status = nxt,
    updated_at = now(),
    owner_phone = case when reveal then (select phone from public.profiles where id = b.owner_id) else owner_phone end,
    worker_phone = case when reveal then (select phone from public.profiles where id = b.worker_id) else worker_phone end,
    cancelled_by = case when nxt = 'CANCELLED' then actor else cancelled_by end,
    job_id = case when p_action = 'START' and p_job is not null then left(p_job, 64) else job_id end,
    evidence = case when p_action = 'COMPLETE' then p_evidence else evidence end
  where id = b.id
  returning * into b;

  if nxt = 'CANCELLED' and actor = 'WORKER' and b.cancelled_by = 'WORKER' and p_action = 'CANCEL' then
    -- only a cancellation after accepting counts against reliability (a worker cannot cancel a plain request)
    update public.workers set cancel_count = cancel_count + 1 where id = b.worker_id;
  end if;
  if nxt = 'COMPLETED' then
    update public.workers set completed_count = completed_count + 1 where id = b.worker_id;
  end if;
  return b;
end;
$$;

create or replace function public.rate_booking(p_booking uuid, p_stars integer, p_tags text[], p_comment text)
returns public.ratings language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  b public.bookings;
  is_verified boolean;
  w double precision;
  r public.ratings;
begin
  if uid is null then raise exception 'Sign in first'; end if;
  if p_stars is null or p_stars not between 1 and 5 then raise exception 'Stars must be 1 to 5'; end if;
  select * into b from public.bookings where id = p_booking for update;
  if not found then raise exception 'Booking not found'; end if;
  if b.owner_id <> uid then raise exception 'Only the owner can rate this worker'; end if;
  if exists (select 1 from public.ratings where booking_id = p_booking) then raise exception 'Already rated'; end if;
  if public.next_booking_status(b.status, 'RATE', 'OWNER') is null then
    raise exception 'You can rate after the job is completed';
  end if;

  -- documented job: a 64-character bill fingerprint and at least one after-photo hash
  is_verified := b.evidence is not null
    and length(coalesce(b.evidence ->> 'billFingerprint', '')) = 64
    and jsonb_typeof(b.evidence -> 'afterPhotoHashes') = 'array'
    and jsonb_array_length(b.evidence -> 'afterPhotoHashes') > 0;
  w := case when is_verified then 1.0 else 0.5 end;

  insert into public.ratings (booking_id, worker_id, owner_id, stars, tags, comment, verified)
  values (p_booking, b.worker_id, uid, p_stars, coalesce(p_tags, '{}'), left(coalesce(p_comment, ''), 280), is_verified)
  returning * into r;

  update public.workers set
    rating_count = rating_count + 1,
    verified_count = verified_count + case when is_verified then 1 else 0 end,
    weighted_star_sum = weighted_star_sum + w * p_stars,
    weight_sum = weight_sum + w
  where id = b.worker_id;

  update public.bookings set status = 'RATED', updated_at = now() where id = p_booking;
  return r;
end;
$$;

-- ---------------------------------------------------------------- access (row level security)

alter table public.profiles enable row level security;
alter table public.workers  enable row level security;
alter table public.bookings enable row level security;
alter table public.ratings  enable row level security;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles for select to authenticated using (id = auth.uid());

drop policy if exists workers_select_all on public.workers;
create policy workers_select_all on public.workers for select to authenticated using (true);
drop policy if exists workers_insert_own on public.workers;
create policy workers_insert_own on public.workers for insert to authenticated
  with check (id = auth.uid() and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'WORKER'));
drop policy if exists workers_update_own on public.workers;
create policy workers_update_own on public.workers for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists bookings_select_parties on public.bookings;
create policy bookings_select_parties on public.bookings for select to authenticated
  using (owner_id = auth.uid() or worker_id = auth.uid());

drop policy if exists ratings_select_all on public.ratings;
create policy ratings_select_all on public.ratings for select to authenticated using (true);

-- Clients get only the columns they may touch. Rating totals, demo flag and bookings are changed
-- only by the functions above.
revoke all on public.profiles, public.workers, public.bookings, public.ratings from anon, authenticated;

grant select on public.profiles to authenticated;
grant select on public.workers to authenticated;
grant insert (id, name, languages, services, rate_min, rate_max, bio, open, geohash, expires_at) on public.workers to authenticated;
-- id is included only so the app can save its profile with one "upsert" request; row level security still
-- forces id to equal the signed-in user.
grant update (id, name, languages, services, rate_min, rate_max, bio, open, geohash, expires_at) on public.workers to authenticated;
grant select on public.bookings to authenticated;
grant select on public.ratings to authenticated;

revoke all on function public.set_availability(boolean, text, timestamptz) from public, anon;
grant execute on function public.set_availability(boolean, text, timestamptz) to authenticated;
revoke all on function public.request_booking(uuid, text, text, text) from public, anon;
revoke all on function public.booking_action(uuid, text, jsonb, text) from public, anon;
revoke all on function public.rate_booking(uuid, integer, text[], text) from public, anon;
revoke all on function public.expire_stale_bookings() from public, anon;
grant execute on function public.request_booking(uuid, text, text, text) to authenticated;
grant execute on function public.booking_action(uuid, text, jsonb, text) to authenticated;
grant execute on function public.rate_booking(uuid, integer, text[], text) to authenticated;
grant execute on function public.expire_stale_bookings() to authenticated;
