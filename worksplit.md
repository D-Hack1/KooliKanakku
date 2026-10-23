# KooliKanaku: Work Split (v2, Open Images V7 model)

**Team JangoMar** | Read `plan.md` first for the overall picture. This file is who builds what, the contracts between us, and the order to do it in.

> **What changed from the previous version:** the detector is `yolov8n-oiv7` (601 classes). Labels now come from `assets/labels.txt`, the parser is generic, NMS is class-agnostic, and an allow-list built from `TaskCatalog` replaces the old exclusion filter. COCO is the fallback.

---

## 1. Ground Rules

- Everyone builds against the **shared contracts** in Section 8. Nobody changes a signature or data class without telling the group.
- Package root: `com.koolikanaku`. Domain models live in `com.koolikanaku.domain`.
- One branch per person (`abhinav/*`, `nived/*`, `ebin/*`, `aadi/*`), merge to `main` at the **Hour 6 merge** and then at every integration checkpoint.
- Every module must be testable with **dummy data**, so nobody is blocked waiting on someone else.
- Anything over 12MP never reaches an analyzer. Downscale right after capture.
- Model files in `assets/` are always named `detector.tflite` and `labels.txt`, whichever model is active.

---

## 2. Ownership at a Glance

| Person | Owns | Files |
|---|---|---|
| **Abhinav** | Architecture, navigation, state, pricing | `MainViewModel.kt`, `PricingEngine.kt`, `TaskMapper.kt`, `TaskCatalog.kt`, `ClutterRules.kt`, `RoomTypeInferrer.kt`, nav graph |
| **Nived** | On-device AI | `YoloAnalyzer.kt`, `StainAnalyzer.kt`, `LabelLoader.kt`, `detector.tflite`, `labels.txt` |
| **Ebin** | Capture, evidence, storage | `CameraModule`, `CryptoUtils.kt`, photo cache, persistence |
| **Aadi** | UI and localization | 5 Compose screens, `strings.xml` (en + ml), bounding-box Canvas |

---

## 3. Abhinav: Architect, ViewModel, Pricing

**Deliverable:** a `MainViewModel` exposing `StateFlow` for the whole journey. Photos and inputs go in, a final INR range comes out.

Tasks, in order:
1. Create the Android Studio project, Gradle config (including `noCompress 'tflite'`), Compose Navigation routes for the 5 screens.
2. Commit the shared data classes (Section 8) in the first hour so everyone can build against them.
3. `PricingEngine.kt` with the formula in `plan.md` Section 4. Must be pure and deterministic. Unit-test it with a few hand-computed cases.
4. `TaskCatalog.kt`: the single lookup table, keyed by label name from `labels.txt`. Each entry has EN/ML titles, `minutesPerUnit`, `materialCostInr`. **It is also the allow-list**: a label not in the catalog never reaches the UI. It also feeds the "+ Add task" sheet.
5. `TaskMapper.kt`: filters detections to catalog labels, **dedups by label** into `quantity`, builds `CleaningTask`s. Lookup is case-insensitive.
6. `ClutterRules.kt`: set of clutter labels and the buckets (0-2 LOW, 3-6 MEDIUM, 7+ HIGH) -> `M_clutter`.
7. `RoomTypeInferrer.kt`: rules in `plan.md` Section 3. Returns a suggested `RoomType`; the worker can override.
8. **Label test:** a unit test that reads `labels.txt` and asserts every key in `TaskCatalog`, `ClutterRules` and `RoomTypeInferrer` exists in it. Wait for Nived's `labels.txt` (Hour 1.5), then fill in the exact label spellings.
9. Selection state: `isSelected` defaults to **false**. Handle zero selected tasks without crashing.
10. Hour 6: wire hardcoded `DetectedObject`s through the whole flow for the merge.

---

## 4. Nived: Edge AI and LiteRT

**Deliverables:** two suspend functions (signatures in Section 8):
- `detectObjects(bitmap): List<DetectedObject>`
- `analyzeStain(bitmap): StainResult`

### Hour 0-1.5: model bake-off (do this first)
1. Export both models at `imgsz=640`, float32:
   - `yolo export model=yolov8n-oiv7.pt format=tflite imgsz=640`
   - `yolo export model=yolov8n.pt format=tflite imgsz=640`
2. For each, dump the class names (`model.names`) into a `labels.txt`, one name per line, in index order. **Send `labels.txt` to Abhinav as soon as the oiv7 one exists**, since he needs the exact spellings for the catalog.
3. Confirm the exact LiteRT artifact and `Interpreter` import that compile (the version in the old master plan wasn't verified).
4. Load each model, print the input shape and `getOutputTensor(0).shape()`. Expect `[1,640,640,3]` in, `[1,605,8400]` out for oiv7 and `[1,84,8400]` for COCO. Run one image, print the first few raw values, and decide: are box coords normalized (0-1) or pixels? Is the layout transposed?
5. Run 4-5 test photos (a bathroom, a kitchen, a cluttered bedroom) through both. Compare: are the boxes sensible, are there duplicates, are the cleaning-relevant objects found?
6. **Decide by Hour 1.5.** oiv7 wins unless it's clearly worse. Copy the winner to `app/src/main/assets/detector.tflite` and its labels to `assets/labels.txt`. Tell the group. No switching after this unless something is truly broken.

### Hour 1.5-6: `YoloAnalyzer.kt`
1. `LabelLoader.kt`: read `assets/labels.txt` once into a `List<String>`.
2. Preprocess: scale to 640x640, direct `ByteBuffer`, native byte order, RGB float32 / 255.
3. Inference with 4 threads. Load the interpreter **once** and reuse it. Close it when the ViewModel clears.
4. Parse generically: `numClasses = outputShape[1] - 4`. For each of the 8400 anchors, take 4 box values and argmax over the class scores. Keep if confidence > **0.30** (a named constant; tune later). Convert `cx,cy,w,h` to `left,top,right,bottom`.
5. **Class-agnostic NMS** (IoU about 0.5): sort all candidates by confidence and drop any box overlapping a kept box, regardless of label. This stops one object being reported under a parent and child label.
6. Map boxes back from model space (640x640) to the coordinate space agreed with Aadi (Section 8), then return them. Allow-list filtering is applied by `TaskMapper` (or a shared helper), not here, so the analyzer stays model-agnostic.
7. Run on a background dispatcher (`Dispatchers.Default`), never the main thread.

### Hour 1.5-6: `StainAnalyzer.kt`
1. Resize Photo 2 to 256x256.
2. Edge density from neighbouring luminance differences; chromatic variance from `max - min` of RGB per pixel; both normalized to 0-100.
3. `grimeScore = 0.6 * edge + 0.4 * chroma` -> LIGHT / MODERATE / HEAVY. Return the raw score as well as the level so we can tune and display it.
4. Keep weights and cutoffs as named constants at the top of the file.

### Hour 12-14: calibration (you own this)
Collect ~10 photos: clean tile, moderately dirty, heavily stained; tidy and cluttered rooms. Print grime scores for each and set the cutoffs so the three levels actually separate. For detection, tune the confidence threshold and NMS IoU, and list which catalog labels never fire and which noisy ones do, so Abhinav can adjust `TaskCatalog`.

---

## 5. Ebin: Camera, Crypto, Storage

**Deliverable:** a module that captures a photo, hashes it immediately, and saves it to local cache.

Tasks, in order:
1. **Fallback first:** `rememberLauncherForActivityResult(TakePicturePreview())` working end to end. Then upgrade to CameraX only if time allows.
2. `CryptoUtils.kt`: SHA-256 over the **exact bytes you save**, hex-encoded. Hash the saved bytes (not a re-encoded Bitmap), or the hash won't match the file later.
3. Build `EvidencePhoto` (id, role `WIDE_ROOM` / `TOUGHEST_AREA`, uri, ISO timestamp, hash) right at capture.
4. Downscale before handing a Bitmap to the analyzers (long edge around 1280 is plenty) and keep the original file for the receipt.
5. Gallery-pick path for the demo fallback (3 staged rooms), going through the same hash-and-store function.
6. Persist the last job (DataStore or Room) so a process restart doesn't lose the receipt.
7. Handle camera permission denial gracefully.

---

## 6. Aadi: Compose UI and Localization

**Deliverable:** 5 Material 3 screens that feel snappy and professional, in English and Malayalam.

Tasks, in order:
1. Build every screen against dummy ViewModel state first (Abhinav's data classes).
2. `strings.xml` for `values/` and `values-ml/`. Every user-facing string goes through resources, no hardcoded text. Test Malayalam rendering early (font, line wrapping, long words).
3. **Screen 1:** wage, pace selector, room size entry with unit toggle (paces / feet / metres), optional room type.
4. **Screen 3:** progress indicator plus a visible **Offline / Airplane Mode badge** in the top bar.
5. **Screen 4 (the important one):**
   - Photo with bounding boxes drawn in a Compose `Canvas`
   - **Unchecked** checklist, quantity +/- on each row
   - "+ Add task" bottom sheet from the shared catalog
   - Room-type suggestion chip ("Detected: Kitchen. Change?")
   - Dirt severity chip with override
   - Small "self-added" tag on manually added tasks
6. **Screen 5:** two photos with hash badges, time range, INR range, clear "estimate" wording.
7. Boxes must scale correctly from the analyzer's coordinate space to the displayed image (see Section 8).
8. Polish tier (after the core flow is merged): per-task thumbnail crops (lazy), confidence % on boxes.

---

## 7. Checkpoints

| When | What must be true |
|---|---|
| **Hour 1.5** | Project builds for everyone, shared data classes committed, **model chosen**, `detector.tflite` + `labels.txt` in `assets/`, output shape confirmed, `labels.txt` shared with Abhinav |
| **Hour 6** | Dummy data flows through all 5 screens on a device |
| **Hour 12** | Real detections and grime score are driving the UI and the price; label test passes |
| **Hour 14** | Calibrated on ~10 real photos |
| **Hour 16** | Full flow works in Airplane Mode on the Moto |
| **Hour 19** | Code freeze |

---

## 8. Shared Contracts (commit these in Hour 1)

```kotlin
package com.koolikanaku.domain

enum class RoomType { KITCHEN, BATHROOM, BEDROOM, LIVING_ROOM, OTHER }
enum class SeverityLevel { LIGHT, MODERATE, HEAVY }
enum class ClutterLevel { LOW, MEDIUM, HIGH }
enum class LengthUnit { PACES, FEET, METRES }

data class RoomDimensions(
    val length: Double,
    val width: Double,
    val unit: LengthUnit,
    val computedAreaSqFt: Double
)

data class DetectedObject(
    val label: String,                        // exact name from labels.txt
    val confidence: Float,
    val boundingBox: android.graphics.RectF   // coordinate space: see note below
)

data class StainResult(
    val severity: SeverityLevel,
    val score: Float                          // 0-100, raw composite
)

data class CleaningTask(
    val id: String,
    val titleEn: String,
    val titleMl: String,
    var quantity: Int,
    val minutesPerUnit: Int,
    val materialCostInr: Double,
    var isSelected: Boolean = false,          // opt-in: default is NOT selected
    val isManuallyAdded: Boolean = false      // true for "+ Add task" (no photo evidence)
)

data class EvidencePhoto(
    val id: String,
    val role: String,                         // "WIDE_ROOM" or "TOUGHEST_AREA"
    val uri: android.net.Uri,
    val timestampIso: String,
    val sha256Hash: String
)

// Nived's analyzers
suspend fun detectObjects(bitmap: android.graphics.Bitmap): List<DetectedObject>
suspend fun analyzeStain(bitmap: android.graphics.Bitmap): StainResult
```

**Open decision to settle in Hour 1 (Nived + Aadi):** which coordinate space `boundingBox` is returned in. Proposal: pixel coordinates of the bitmap that was passed into `detectObjects`, so Aadi only needs one scale factor (displayed size / bitmap size).

**Model-agnostic by design:** `detectObjects` returns raw post-NMS detections with whatever labels the active model uses. Filtering to priceable labels happens downstream in `TaskMapper` using `TaskCatalog`, so swapping COCO and oiv7 only changes the asset files and the catalog keys.

**Changes from the original master plan:** `isSelected` now defaults to `false`; added `ClutterLevel`, `StainResult`, and `isManuallyAdded`.

---

## 9. Communication

- Anything that touches a shared contract gets announced in the group chat before it's merged.
- If you're blocked for more than 20 minutes, say so. Don't sit on it.
- Nived announces the model decision and shares `labels.txt` the moment it exists.
- Nobody builds the ARCore stretch goal until the Hour 16 Airplane Mode test passes.
