# KooliKanaku: Final Plan (v2, Open Images V7 model)

**Team JangoMar** (Abhinav, Ebin, Aadi, Nived) | **Track:** Physical & Edge AI (REALM HACK) | 24-hour build
**Pitch line:** "Maths, not an accusation."

> **What changed from the previous plan:** the detector is now `yolov8n-oiv7` (601 classes) instead of COCO (80 classes). That means a generic output parser, labels loaded from a file, class-agnostic NMS, and an **allow-list** instead of an exclusion list. COCO stays as the fallback if the Hour 0-1 bake-off says oiv7 isn't working.

---

## 1. What We're Building

An offline Android app that gives domestic cleaning workers and their clients a transparent, fair wage estimate for a job, computed on-device from two photos. No cloud calls. The AI only *observes* the room; the worker decides what's being cleaned; the price comes from deterministic math anyone can audit.

**Stack:** Kotlin, Jetpack Compose (Material 3), CameraX, LiteRT (YOLOv8n-oiv7), SHA-256 via `MessageDigest`, DataStore or Room, MVVM with `StateFlow`.

---

## 2. User Flow (5 screens)

1. **Setup:** base wage (INR/hr), pace factor (0.9 / 1.0 / 1.15), room size (paces, feet, or metres). Room type is optional here; it gets suggested from the photo later. Bilingual (English / Malayalam).
2. **Capture:** Photo 1 (wide room), Photo 2 (toughest spot). Each is SHA-256 hashed and timestamped the moment it's captured.
3. **Analyzing:** runs both pipelines on-device. Offline badge in the top bar.
4. **Confirm (opt-in):** detected items shown **unchecked**. Worker picks what they're cleaning, adjusts quantities with +/-, can add missed tasks, can override room type and dirt severity.
5. **Receipt:** both photos with hash badges, time range, INR wage range.

---

## 3. The Two Pipelines

### Pipeline A: object detection (Nived)

- **Model:** `yolov8n-oiv7`, exported to TFLite (float32, `imgsz=640`), run through LiteRT with 4 CPU threads. Bundled in `assets/` as `detector.tflite` with its class names in `assets/labels.txt`. Using fixed asset names means swapping models later never touches code.
- **Output:** `[1, 4 + numClasses, 8400]`, expected `[1, 605, 8400]` for 601 classes. The parser reads `numClasses = outputShape[1] - 4` and loads names from `labels.txt`, so it works for any YOLOv8 export (including COCO if we fall back).
- **Chain:** Bitmap -> resize 640x640 -> float32 ByteBuffer (0-1) -> inference -> parse -> confidence filter -> **class-agnostic NMS** -> **allow-list filter** -> `List<DetectedObject>`.
- **Confidence threshold:** start at **0.30**, not 0.40. Scores are spread across 601 classes and run lower than COCO's. Tune during calibration.
- **Class-agnostic NMS (IoU ~0.5):** Open Images labels are hierarchical ("Table" vs "Kitchen & dining room table", "Kitchen appliance" vs "Oven"), so one physical object can fire under two labels. Suppressing overlaps *across* classes, keeping the higher-confidence one, stops duplicate checklist rows.
- **Allow-list, not block-list:** only labels that exist in `TaskCatalog` ever reach the UI. People, faces, hands, pets, clothing and everything else are dropped automatically. This replaces the old `person/cat/dog/bird` exclusion list, which wouldn't work here (Man, Woman, Boy, Girl, Human face, Human hand, and so on).
- **Label spelling:** names must match `labels.txt` exactly (case-insensitive lookup). A unit test asserts that every key in `TaskCatalog`, `ClutterRules` and `RoomTypeInferrer` exists in `labels.txt`, so a typo fails at test time, not on stage.
- **Verify at the start:** print `getOutputTensor(0).shape()` and a few raw values. Confirm the layout is `[1, 605, 8400]` (not transposed) and whether box coords are normalized (0-1) or pixels.

### Pipeline B: grime scoring (Nived)

- No model. "Dirty" isn't a class any detector has, so this is pixel statistics on Photo 2 (256x256).
- **Edge density:** mean gradient magnitude from neighbouring luminance differences (texture, crust, debris).
- **Chromatic variance:** mean of `max(R,G,B) - min(R,G,B)` per pixel (grease, staining).
- `grimeScore = 0.6 * edge + 0.4 * chroma` (normalized 0-100) -> LIGHT / MODERATE / HEAVY. Weights and cutoffs are tuned on real photos (see Section 6).

### Auto room-type suggestion (cheap AI win, no new model)

A rule layer over the detections, matched case-insensitively against `labels.txt` names (confirm the exact spellings when the label file exists):
- Toilet or Bathtub -> BATHROOM
- Bed -> BEDROOM
- Oven, Microwave oven, Refrigerator, or Sink without a toilet -> KITCHEN
- Couch or Television -> LIVING_ROOM
- otherwise keep whatever the worker chose, or OTHER

Shown as "Detected: Kitchen. Change?" and the worker can override it.

---

## 4. Pricing Engine (Abhinav)

```
BaseFloorMinutes = Area * MinutesPerSqFt[roomType]
TaskMinutes      = sum(quantity * minutesPerUnit)   // selected tasks only
M_cond           = 1.0 + (M_clutter - 1.0) + (M_grime - 1.0)
TotalMinutes     = (BaseFloorMinutes + TaskMinutes) * M_cond * PaceFactor
Price            = (TotalMinutes / 60) * HourlyWage + sum(materialCost of selected tasks)
Range            = +/-10% around the point estimate
```

- `M_cond` applies to labour minutes only, never to material cost.
- `M_clutter` comes from **detected** clutter items (small loose objects such as bottles, cups, mugs, bowls, plates, books; exact label names come from `labels.txt`): 0-2 LOW, 3-6 MEDIUM, 7+ HIGH.
- `M_grime` comes from Pipeline B's severity (worker can override it on Screen 4).
- Zero selected tasks is valid: price falls back to base floor cost only.

---

## 5. Key Design Decisions (and why)

| Decision | Reasoning |
|---|---|
| **OIV7 (601 classes) over COCO (80)** | Covers cleaning-relevant objects COCO lacks (towel, mirror, bathtub, and likely countertop, tap, window; confirm in `labels.txt`). Size and speed cost is small for a once-per-photo inference. |
| **Generic parser + `labels.txt`** | One code path for any YOLOv8 export, so falling back to COCO is an asset swap, not a rewrite. |
| **Allow-list from `TaskCatalog`** | Only things we can price reach the UI. People and pets can never appear, with no per-label maintenance. |
| **Class-agnostic NMS** | Hierarchical labels would otherwise create duplicate rows for one object. |
| **Opt-in task list** (nothing pre-checked) | AI observes, worker decides. Fits "not an accusation" better than "AI decided, you can object". |
| **Dedup by label** | Three mugs is one row "Mug (3)", not three rows. Matches `CleaningTask.quantity`. |
| **"+ Add task"** | Handles detector misses so fairness works in both directions. |
| **Severity multipliers decoupled from selection** | `M_clutter` and `M_grime` come from raw detection, so ticking or unticking tasks can't game them. Only `TaskMinutes` moves with selection. |
| **Manual room size** | Image EXIF has no scene depth, so "read room size from metadata" doesn't work. Paces/feet entry ships. ARCore two-tap measurement is stretch only. |
| **Stock model, no fine-tuning** | No dataset exists and we can't build one in 24h. Pretrained plus label mapping gives the same demo result. |

---

## 6. 24-Hour Timeline

Hours are relative to kickoff.

| Hours | What happens |
|---|---|
| **0-1.5** | Create the project, add dependencies, commit the shared data classes (see `worksplit.md` Section 8). **Model bake-off (Nived):** export both oiv7 and COCO, dump each model's class names to a `labels.txt`, run 4-5 test photos (bathroom, kitchen, cluttered bedroom) through each, then lock the choice. Rule: oiv7 wins unless it gives clearly worse boxes or duplicates that class-agnostic NMS can't fix. Once locked, no more switching. |
| **1.5-6** | Parallel build: Abhinav (ViewModel, nav, pricing, catalog), Nived (YOLO analyzer, stain analyzer), Ebin (capture, hashing, storage), Aadi (screens, strings, Canvas boxes). Everyone works against dummy data. |
| **6** | **Mandatory merge.** Hardcoded `DetectedObject`s flow through all 5 screens end to end. Nothing moves on until this works. |
| **6-12** | Integration: real LiteRT output into the UI, bounding boxes on Canvas, selection flow, pricing wired to real inputs, receipt screen. |
| **12-14** | **Calibration.** Collect ~10 real photos (clean tile, moderate, heavy; tidy vs cluttered rooms). Tune grime weights/cutoffs, confidence threshold, NMS IoU, clutter buckets. Check which catalog labels actually fire and fix `TaskCatalog` accordingly. |
| **14-16** | Build the APK, install on the Moto, Airplane Mode, run the full flow 3 times. Fix whatever breaks. |
| **16-19** | Polish: thumbnails, confidence % on boxes, auto room-type suggestion, Malayalam strings check. Stretch (ARCore) only if everything above is green. |
| **19-20** | **Code freeze.** Only bug fixes after this. |
| **20-23** | Pitch prep, record a backup demo video, stage the 3 gallery test rooms as a camera fallback. Rehearse twice. |
| **23-24** | Buffer. Charge the phone. Submit. |

---

## 7. Fallbacks

| Risk | Fallback |
|---|---|
| OIV7 export fails or output shape is unexpected | Use COCO `yolov8n` (same parser, swap `detector.tflite` + `labels.txt`, point catalog keys at COCO names) |
| OIV7 gives duplicate or noisy detections even with class-agnostic NMS | Raise the confidence threshold, tighten the allow-list, or fall back to COCO |
| A catalog label doesn't match `labels.txt` | The label test catches it; fix the spelling in `TaskCatalog` |
| CameraX too fiddly | `ActivityResultContracts.TakePicturePreview()` (native camera, returns a Bitmap) |
| NMS or output parsing won't behave | Swap to EfficientDet-Lite2 (built-in NMS, ready-made boxes) |
| OOM | Downscale immediately after capture; never pass a 12MP bitmap into any analyzer |
| Boxes look offset or squashed | Check normalized vs pixel coords, and map back from 640x640 to the displayed image size |
| Grime scoring doesn't separate clean from dirty | Retune weights on real photos; as a last resort, let the worker's severity override carry the demo |
| Live camera fails on stage | Gallery images of 3 staged rooms |
| LiteRT dependency or API name mismatch | Confirm the exact artifact version and `Interpreter` import in the first hour, not at hour 6 |
| ARCore stretch eats time | Cut it. It's a "future work" slide. |

---

## 8. Pitch Narrative (understated, defensible)

1. **Problem:** domestic workers in Kerala are often paid by negotiation with no shared reference. Disputes feel like accusations.
2. **Solution:** two photos, a few taps, and a transparent calculation both sides can see.
3. **On-device AI:** a 601-class object detector runs fully offline. Toggle Airplane Mode live on stage.
4. **Deliberate restraint:** AI is used where it adds value (detecting what's in the room). Grime isn't an object class, so it's scored with signal processing instead of a model forced onto the problem. Pricing stays deterministic so it can be audited.
5. **Fairness in both directions:** worker opts in to tasks and can add missed ones; severity can't be gamed by ticking boxes.
6. **Trust:** SHA-256 hashes on photos show the evidence wasn't swapped after the fact.
7. **Privacy:** no photo leaves the device, and only priceable objects are surfaced (people and pets never appear in results).
8. **Honest limits and next steps:** room size is manual today; ARCore two-tap measurement is the next step. Fine-tuning a detector on Kerala household scenes is the other.

---

## 9. Definition of Done

- [ ] Bake-off done and model locked by Hour 1.5
- [ ] All 5 screens navigate end to end in Airplane Mode on the Moto phone
- [ ] Both photos hashed at capture, hashes visible on the receipt
- [ ] Detection runs on a real photo and boxes line up with objects
- [ ] No duplicate rows for one physical object
- [ ] People and pets never appear in the checklist
- [ ] Label test passes (every catalog/rule label exists in `labels.txt`)
- [ ] Zero selected tasks doesn't crash and gives a sensible price
- [ ] Same inputs always give the same price
- [ ] Malayalam strings render correctly on every screen
- [ ] Backup demo video recorded
- [ ] Gallery fallback photos staged
