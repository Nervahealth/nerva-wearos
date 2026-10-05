# HUGR NSTAIR — Watch70 Passive Identity and Marker Observation

**Date:** 2026-10-05
**Scope:** The separately authorised passive identity-and-marker discriminator only. No normal Main HUGR relaunch, Phone57 HUGR/Connect action, sensing, recording, GATT delivery, acknowledgement, egress/readback/export, verifier, ADB/Termux, root/storage access, data clear, reset, retry, source change, or build occurred.

> **IDENTITY CONFIRMED AT VERSION-STRING LEVEL · FINALIZED-DELIVERY MARKER NOT PRESENT AT THE EARLIER WATCH70 MAIN LAUNCH · NO STARTUP DEFECT OR BLUETOOTH CAUSE ESTABLISHED.**

## 1. Operator-supplied passive evidence

The supplied Watch app-details photograph shows the ordinary HUGR app version string:

```text
0.70.0-finalized-delivery-recovery-candidate
```

This confirms the installed runtime identity at the **version-string** level. It does not independently establish the APK bytes or SHA-256, which remain supported by the signed admission artifact and installation-only observation rather than package-manager byte extraction.

The supplied `HUGR Startup Readiness` photograph shows the passive, marker-metadata-only viewer and reports:

```text
runId=73a7ec8c-97bb-413b-afbf-9eb53656f1ca
lastStage=FIRST_DRAW_OBSERVED
stageEpochMs=1791195389738
```

`1791195389738` is `2026-10-05T10:16:29.738Z`, or `2026-10-05T12:16:29.738+02:00` in Europe/Oslo. This aligns with the earlier stopped Watch70 normal-Main launch rather than the later passive viewer observation.

The accompanying Phone file-manager image shows a retained candidate-named APK entry of approximately 12.76 MB. It is supporting provenance only; it does not prove the on-Watch installed byte hash.

## 2. Source-established interpretation

Watch70 reads the one-record `NormalStartupMarkerStore` during `MainActivity.onCreate()` before layout, GATT, Phone resume, Bluetooth waiting, or ordinary marker writes. It takes the finalized-delivery-only route only when that pre-read value is exactly `BOUNDED_RUN_FINALIZED`.

The recorded result establishes this sequence for the earlier Watch70 Main launch:

1. Its pre-read marker was **not** exact `BOUNDED_RUN_FINALIZED` (or was absent); otherwise the app would have synchronously rendered `Finalized delivery recovery · standard GATT only` and returned from the normal-launch path.
2. It entered the ordinary branch, wrote a new marker run ID, rendered the generic first frame, and then wrote `FIRST_DRAW_OBSERVED` at the captured timestamp.
3. The later Startup Readiness view read that latest marker only. It did not itself write a marker, launch Main HUGR, start Health/GATT/BLE/sensing, traverse a journal, or perform cleanup.

This **rules out an installed-version mismatch** as the explanation for the generic frame at the version-string level and rules out a post-first-draw GATT, Phone, or Bluetooth delay as the reason that the recovery headline never appeared. It does **not** reconstruct why `BOUNDED_RUN_FINALIZED` was absent/non-finalized at the instant of the earlier Main launch: the marker store keeps only the latest record.

## 3. Marker-writer conclusion

The only source writers remain normal Main HUGR, an explicitly started Health service, and GATT only after the exact terminal session/endpoint/SHA acknowledgement. The documented Watch69→Watch70 sequence contains no established pre-launch marker overwrite. Bluetooth restoration happened after the stopped Watch70 launch and has no marker-writer/start path in source.

Therefore, no evidence supports blaming Bluetooth; likewise, there is no evidence for a Watch70 startup implementation defect. The source-established present fact is narrower: **the recovery selector did not receive an exact finalized marker when Watch70's Main activity started.**

## 4. Consequence and next engineering boundary

No new physical attempt is authorized or implied. Watch69 run `b561b698-8206-4cb5-8717-95a5c9f533e2` remains **NOT QUALIFIED**; no Phone manifest equality, exact acknowledgement, or Watch delivery-completion marker has been observed.

The present marker cannot be restored by observation, and the retained session must not be declared lost merely because a lifecycle marker is no longer final. If further recovery work is desired, the next issue is a source/test-only design decision: whether finalized-session recovery eligibility can safely be derived from the durable fresh journal's retained, unacknowledged terminal manifest rather than requiring the mutable one-record startup marker. That would require separate explicit authorization, preservation-boundary tests, and no device action unless a later candidate is admitted.

Watch58 remains exactly `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`. Watch68 v1 remains **UNCLASSIFIED / NOT VERIFIED**. The egress binary remains compatibility-only; no egress action or validation occurred.

## 5. Evidence and limits

- Operator-supplied app-details image: `IMG_20261005_152422.webp`.
- Operator-supplied passive marker-viewer image: `IMG_20261005_152536.webp`.
- Operator-supplied Phone file-manager image: `Screenshot_20261005_152450_com_hihonor_filemanager_FileManager.jpg`.
- Source reference: `app/src/main/java/com/hugr/wearos/MainActivity.kt` lines 65–93 and 145–176; `NormalStartupMarkerActivity.kt` lines 12–65.

No source implementation changed and no device log, private storage, payload, verifier, or cryptographic on-device package inspection was obtained. This receipt distinguishes direct visual/operator evidence from source interpretation.


## 6. Preservation closeout

The initial Watch source observation commit `7d245edf908179bd49dd4cab5761d0b0ac88ba61` was pushed without force to `Nervahealth/nerva-wearos` branch `preservation/watch69-bounded-ordinary-run-2026-10-03` and independently read back at the same SHA. The governed-control commit `1693bc5b0d96d28f3c5a2b64185775bb2581922b` and Phone/control-copy commit `5b96412ff68fd81bb51bbed1e542c87961e1f7e4` were likewise read back from their Nervahealth preservation branches. No force push, merge to `main`, or accepted-S4 mutation occurred. The existing sanitized fallback remains retained.
