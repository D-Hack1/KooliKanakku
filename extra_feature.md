# KooliKanakku: Extra Feature Spec — "Find a Worker" (connected layer)

**Status:** proposal / spec, nothing built yet. Written for Team JangoMar.
**One line:** an optional online layer on top of the offline estimator. Workers mark themselves open for work, owners find and book nearby workers, and when the worker arrives the existing offline flow (estimate, photos, hashes, bill, PDF) runs exactly as it does today. Ratings come out of that flow, so they are backed by evidence.

> The offline core stays the main part of the pitch. This layer is added on top of it and is never required by it.

---

## 1. Decisions already made

| Decision | Detail |
|---|---|
| Two user types | **Owner** and **Worker**, chosen at sign-up, with login. |
| Online is allowed for this layer | `INTERNET` is added. The "no `INTERNET` permission" claim is **retired for the app as a whole** (see Section 3 for the replacement claim). |
| Discovery by location | Location permission so owners can see workers open to work *near them*. |
| Contact | The owner can call the worker and book them. The address is given **on the call**, never stored on a server. |
| Hand-off | When the worker reaches the home, **either party** can start the existing offline flow. |
| Ratings | Every worker has a rating; owners choose using it. |

## 2. Honest scope and risk

This is a second product (a small marketplace) bolted onto the first. In the time left it cannot be built fully. Be critical about what we claim.

- A real marketplace needs accounts, trust and safety, fraud handling and moderation. We will not have those at a hackathon. We will have a **working vertical slice** (Section 12) and a **clear roadmap**.
- Marketplaces have a **cold-start problem**: with no workers online the owner screen is empty. For the demo we need real test accounts on real phones, plus clearly labelled demo workers (never presented as real people).
- The demo needs **at least two phones** (one worker, one owner) and working Wi-Fi or mobile data on stage. This is a new way for the demo to fail. The offline flow remains the fallback demo.
- Do not build this at the expense of the offline demo. Feature-freeze rule: the offline flow must keep passing its tests and its Airplane Mode run after every merge.

## 3. Principle: keep the offline core provably offline

The offline core is the strength of the project, so the new layer must not weaken it.

1. **Isolate the network code.** All online code lives in a separate package (`com.koolikanaku.connect`) or Gradle module. The core packages (`ai`, `domain`, `capture`, `export`) must not import it. Add a unit test or a lint rule that fails if a core class imports `connect`, Firebase or an HTTP library.
2. **Photos and job data are never uploaded.** Only the data in Section 6 leaves the phone. The estimate, grime score, photos, hashes and PDF stay on the device unless the user shares the PDF.
3. **Replacement claim for the pitch:** "The estimate and evidence engine has no network code and works in Airplane Mode. Only a worker's profile, a coarse location and booking status go online, and photos never do."
4. **Optional, stronger option:** two build flavors. `offline` has no `INTERNET` permission and no connect layer (the verifiable, jury-friendly build). `connected` includes it. Demo the connected one, and keep the offline one for the proof slide ("this APK's manifest has no `INTERNET`").
5. **Graceful offline:** with no network, the Find-a-worker entry shows "needs internet" and everything else works as today.

## 4. User flows

### 4.1 Common
1. App opens. If not signed in: **Sign in** (phone number + OTP), then **choose role: Owner or Worker** (stored in the profile; changing role later needs sign-out).
2. Skip option for the offline core: **"Use without an account"** goes straight to the existing Setup screen. The estimator does not require login.

### 4.2 Worker
1. **Profile setup (once):** name, phone (already verified by OTP), languages, services offered (kitchen, bathroom, bedroom, general), hourly rate range, optional short bio.
2. **Open for work toggle** (home screen of the worker). When turned on:
   - ask for location permission, take a **coarse** location, store a **geohash** (not exact coordinates) and a "last seen" time;
   - auto-switch off after a set time (for example 4 hours) or when the app is closed for a long time, so stale workers do not appear;
   - a visible "You are visible to owners nearby" banner, and a one-tap off.
3. **Incoming requests:** a card with the owner's first name, area, rooms/size hint, requested time. Actions: **Accept** or **Decline**.
4. **After accepting:** the owner's phone number is shown, with a **Call** button. The address is shared by voice.
5. **On arrival:** tap **"I've arrived"**, then **Start the estimate** (opens the existing offline flow).
6. After the job: see the rating the owner gave. Optionally rate the owner (private reliability signal, not public).

### 4.3 Owner
1. **Find a worker:** location permission (coarse), then a list of **workers open to work near you**, sorted by a mix of distance and trust score.
2. **Worker card:** name, rating (stars + count), "verified jobs" count, distance band ("about 2 km"), languages, rate range, services.
3. **Request booking:** pick when (now or a time) and send. The owner's number is **not** shown to the worker yet.
4. **When accepted:** the worker's number is revealed, with a **Call** button. Tell the address on the call.
5. **On arrival:** the owner can also tap **"Worker arrived"**, then **Start the estimate** (existing offline flow, in owner mode if they prefer to produce the quotation).
6. **After the job:** **Rate the worker** (1 to 5 stars, quick tags, optional comment). The rating is marked **verified** if the completed job has an evidence summary (Section 8).

### 4.4 Booking states
```
REQUESTED -> ACCEPTED -> ARRIVED -> IN_PROGRESS -> COMPLETED -> RATED
     |           |
     +-> DECLINED / EXPIRED / CANCELLED (by either side, with a reason)
```
- `REQUESTED` expires if not answered (for example 10 minutes).
- Either side can cancel before `ARRIVED`. Repeated cancellations affect a worker's reliability score.
- `ARRIVED` can be set by either party (the spec requires either can start the flow). Both see the same state.
- `IN_PROGRESS` begins when the existing job is started from this booking. `COMPLETED` when the Bill is finished (with an optional after photo).

## 5. Architecture

### 5.1 Recommended backend for a hackathon: Firebase
Fastest path with no server to run. Verify current plan limits before relying on them.

| Need | Firebase piece | Notes |
|---|---|---|
| Login | Authentication (phone OTP) | SMS delivery may cost or be limited on the free plan. For demos use **Firebase "test phone numbers"** with fixed codes. |
| Data | Cloud Firestore | Real-time listeners give instant request/accept updates without push. |
| Nearby search | Geohash field + range queries on Firestore | Standard pattern; precision 5 is about a 5 km cell, 6 is about 1.2 km. Filter by true distance on the device afterwards. |
| Push when app is closed | Cloud Messaging via Cloud Functions | Needs a paid plan for Functions. **Fallback:** real-time listeners while the app is open (enough for a demo). |
| Abuse limits | Security Rules | Writes limited to the owner of the document, with field validation. |

Alternatives: Supabase (Postgres + PostGIS gives proper radius queries, simple row-level security) or a small custom server. Either is fine; pick what someone on the team already knows. The Android code should hide the backend behind an interface (Section 5.3) so it can be swapped.

### 5.2 New Android pieces
- **Dependencies:** Firebase BoM, `firebase-auth-ktx`, `firebase-firestore-ktx`, `play-services-location`.
- **Manifest:** `INTERNET`, `ACCESS_COARSE_LOCATION` (not fine location: privacy and enough for "nearby"). Request at the moment the user opens the feature, with a plain-language reason.
- **Packages:** `connect/auth`, `connect/model`, `connect/data` (repositories), `connect/ui` (screens), `connect/location`.
- **Navigation additions:** `Welcome/SignIn`, `RoleSelect`, `WorkerProfile`, `WorkerHome`, `RequestInbox`, `NearbyWorkers`, `WorkerDetail`, `BookingStatus`, `RateWorker`.
- **Config the team must provide:** a Firebase project and `google-services.json` (not committed if it holds anything private), and test phone numbers.

### 5.3 Interface so the backend is swappable and testable
```kotlin
interface AuthRepository      { suspend fun signIn(phone, code); fun currentUser(): User?; fun signOut() }
interface WorkerRepository    { suspend fun setAvailability(open: Boolean, geohash: String?, expiresAt: Long?)
                                fun nearbyOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerCard>> }
interface BookingRepository   { suspend fun request(workerId, whenIso): Booking
                                fun observe(bookingId): Flow<Booking>; suspend fun respond(id, accept)
                                suspend fun setStatus(id, status); suspend fun complete(id, summary: EvidenceSummary) }
interface RatingRepository    { suspend fun rate(bookingId, stars, tags, comment); fun ratingsFor(workerId): Flow<List<Rating>> }
```
Provide a **`FakeConnectRepository`** (in memory) for unit tests and for the labelled demo mode.

## 6. Data model and privacy

### 6.1 What goes online (and nothing else)
| Data | Stored on server | Visible to |
|---|---|---|
| Account (phone number, role) | yes | the user; phone shown to a counterpart only after acceptance |
| Worker profile (name, languages, services, rate range, bio) | yes | owners browsing |
| Availability (`open`, **geohash** of coarse location, expiry) | yes | owners nearby (as distance band only) |
| Owner location | **no** (used only to compute the search, never saved) | n/a |
| Booking (ids, status, time, rooms/size hint) | yes | the two parties |
| Rating (stars, tags, comment, verified flag) | yes | public on the worker |
| Evidence summary (Section 8) | yes (hashes and totals only) | the two parties |
| **Address** | **never** | shared by voice on the call |
| **Photos, job details, PDF** | **never** | stay on the device |

### 6.2 Documents (Firestore sketch)
```
users/{uid}            { role, name, phone, createdAt }
workers/{uid}          { name, languages[], services[], rateMin, rateMax, bio,
                         open, geohash, expiresAt, updatedAt,
                         ratingSum, ratingCount, verifiedCount, cancelCount }
bookings/{id}          { ownerId, workerId, status, requestedFor, areaLabel, sizeHint,
                         createdAt, acceptedAt, arrivedAt, completedAt, evidence? }
ratings/{bookingId}    { workerId, ownerId, stars, tags[], comment, verified, createdAt }
```
Keep the aggregate fields on `workers/{uid}` updated by the client transaction on rating (or by a Cloud Function if available).

### 6.3 Location privacy rules
- Coarse location only; store a geohash prefix, not coordinates.
- Show **distance bands** ("under 1 km", "about 3 km"), never a map pin of a worker's house.
- Remove availability automatically on expiry and on sign-out.
- Owners can only see workers with `open == true` and an unexpired `expiresAt`.

### 6.4 Compliance note
This stores personal data (phone numbers, approximate location). Add a short consent screen, a way to delete the account and data, and a privacy statement. India's data-protection law may apply. **Check it properly; this document is not legal advice.**

## 7. Ratings and trust

Goal: owners can choose by rating, and the rating is hard to fake.

1. **Only after a completed booking**, and only **one rating per booking**, only from the counterpart. No rating without a real booking.
2. **Verified rating:** a rating is flagged **verified** when the completed job carries an evidence summary (Section 8): a bill fingerprint plus at least one after photo hash, and the photo hashes still verify on the device that finished the job.
3. **Trust score (for sorting):** use a smoothed average so one lucky five-star does not beat a long record:
   `score = (sum(weight * stars) + m * prior) / (sum(weight) + m)`, with verified weight 1.0, unverified 0.5, prior 3.5, m = 3.
   Add a **reliability** term from cancellations and no-shows.
4. **Show the evidence, not just stars:** "4.7 · 23 ratings · 18 with verified photos".
5. **Known weaknesses (say them if asked):** friends can rate each other (collusion); verified only proves a job was documented, not that the work was good; a new worker starts with no rating. Mitigations for later: ID verification, rate limits per owner, anomaly checks.
6. Owners may also receive a **private** reliability signal from workers (no-show, payment dispute). Not public in v1.

## 8. Connecting the online booking to the offline flow

This is the feature's real strength: the booking hands over to the existing job, and the job's evidence comes back as a rating signal.

1. **Start from a booking:** "Start the estimate" opens the current Setup screen, prefilled with the worker's rate and the right mode, and creates the job with an optional `bookingId`. **This adds a field to `Job`** (a contract change, so announce it and get a yes from every owner of a touched file).
2. **The offline flow is unchanged:** rooms, photos, detection, measuring, grime, pricing, hashes, Bill, PDF. It never needs the network.
3. **Completion:** when the Bill is finished, the app builds an **evidence summary** and, if online, attaches it to the booking. If offline at that moment, queue it and send later.
   ```
   EvidenceSummary { billFingerprint, totalPriceRange, roomCount,
                     afterPhotoHashes[], photoCount, generatedAt }
   ```
   - `billFingerprint` = SHA-256 over a canonical string of the bill totals plus every photo hash. (It is also worth building on its own as a small improvement to the offline story, with a QR code on the PDF.)
   - No photos, no addresses and no task detail are sent.
4. **Either party can start it.** Whoever starts the job, both phones show `IN_PROGRESS`. The estimate itself is produced on one phone. The other party gets the PDF through the normal share sheet.

## 9. Screens (new)

| Screen | Contents |
|---|---|
| Welcome / Sign in | Phone number, OTP, "Use without an account". |
| Role select | Owner or Worker, with a one-line description each. |
| Worker profile | Name, languages, services, rate range, bio. |
| Worker home | Big **Open for work** toggle, visibility banner, incoming requests, active booking. |
| Request inbox | Request cards with Accept and Decline. |
| Nearby workers (owner) | List sorted by trust and distance, with filters (service, rating, rate). Empty-state message when none. |
| Worker detail | Rating breakdown, verified-job count, recent comments, **Request booking**. |
| Booking status | Timeline of the states, Call button (after acceptance), "Arrived", "Start the estimate". |
| Rate worker | Stars, tags (on time, careful, fair price), comment, and a note that it will be marked verified. |

All new strings need English and Malayalam, written by a native speaker.

## 10. Edge cases to handle

- No network or flaky network (queue the writes, show the state, never lose a booking silently).
- Location permission denied, location services off, or no fix (offer to search by area name instead).
- Worker toggles open and the app is killed (expiry handles it).
- Two owners request the same worker at once (worker sees both; accepting one declines the other or marks busy).
- Worker accepts but never arrives (owner can cancel; counts toward reliability).
- OTP failures and rate limits.
- Account deletion while a booking is active.
- Clock changes (use server timestamps for booking times, not the phone clock).
- Role abuse (one account acting as both): one role per account in v1.

## 11. Testing plan

- **Unit tests** with `FakeConnectRepository`: booking state machine (valid and invalid transitions), trust-score ordering (20 verified 4.8s outrank one unverified 5.0), distance and geohash helpers, expiry logic.
- **Security rules tests** with the Firebase emulator: owners cannot read other users' bookings, workers cannot rate themselves, only the counterpart can rate a completed booking once.
- **Offline-core guard:** a test that fails if `ai`, `domain`, `capture` or `export` import the connect layer or Firebase.
- **Instrumented:** the full existing end-to-end test must still pass unchanged.
- **Manual two-phone run** of the whole path, in a real network, before the demo, plus a run with the network switched off.

## 12. Build plan

### 12.1 MVP vertical slice (what is realistic before a demo)
1. Backend project, test phone numbers, `google-services.json`, repository interfaces, fake repository.
2. Sign in, role select, worker profile.
3. Worker **Open for work** toggle with coarse location and geohash.
4. Owner **nearby workers** list (geohash query, distance bands, rating shown).
5. **Request → accept** over real-time listeners; reveal the number; Call via the dialer (`ACTION_DIAL`, no permission needed).
6. **Arrived → Start the estimate**, which opens the existing flow with `bookingId` attached.
7. **Complete → rate**, with the verified flag from the evidence summary.

### 12.2 Cut list (drop these first)
Push notifications when the app is closed, scheduled bookings (do "now" only), filters, worker rating of owners, comments, map view, in-app chat, payments, ID verification, admin and moderation tools.

### 12.3 Order of work (so something works at every step)
`interfaces + fake` → `auth + roles` → `availability + nearby list` → `request/accept` → `hand-off to estimate` → `rating` → `Malayalam strings` → `polish and tests`.

## 13. Roadmap (say this in the pitch)

1. **Now:** offline estimate and evidence, plus this connected layer as a working prototype.
2. **Next:** push notifications, scheduled bookings, in-app masked calling, ID and background verification.
3. **Later:** payments, dispute handling using the evidence bundle, worker teams, regional language voice support, replacing AGPL-licensed model weights with permissively licensed ones.

## 14. Questions for the team

1. Backend: Firebase (fast, recommended) or something we already know?
2. Do we keep a separate **offline-only build flavor** for the "no `INTERNET`" proof slide?
3. How many real phones can we have for the demo, and how do we get reliable internet on stage?
4. Who writes the Malayalam strings and the consent text?
5. Do workers see owner ratings in v1 (we suggest no)?
6. Where does the bill fingerprint feature go: before this feature (recommended, it is small and also strengthens the offline story)?
7. Who owns this feature, and who protects the offline core from regressions while it is built?

## 15. Honest summary for the pitch

- The offline estimate and evidence engine is the product. It works in Airplane Mode and never uploads photos.
- The connected layer adds discovery, booking and ratings that are tied to real, hashed evidence. This is what makes it different from a generic star-rating marketplace.
- What is built is a vertical slice with test accounts. Trust and safety, moderation and scale are the roadmap, and we say so plainly.
