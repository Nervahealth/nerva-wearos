# HUGR BLE Data Contract v1.0
## Definitive Specification — Watch ↔ Phone Communication

**Date:** 2026-08-18
**Status:** **SUPERSEDED DESIGN CANDIDATE — NOT THE LIVE BLE CONTRACT**
**Patent relevance:** Clusters 36 (Tiered Intelligence), 39 (Batch-Aware), 49 (Cross-Domain Platform)

> **Reality correction — 2026-08-22:** The deployed source does not implement this document exactly. In the verified Build 39w/phone source, UUID `4444...` carries both raw PPG and HR/IBI via a format byte, UUID `5555...` carries skin temperature, and UUID `6666...` is the status characteristic. This document is retained as design history only. The governing cross-device scientific-memory specification is `docs/HUGR_Scientific_Memory_Event_Contract_2026_08_22.md` in the mobile repository; any revised BLE packet contract must be versioned, backward-compatible and verified against both watch and phone source before promotion.

---

## 1. Service & Characteristic UUIDs

| Name | UUID | Direction | MTU |
|------|------|-----------|-----|
| **HUGR Service** | `12345678-1234-5678-1234-567812345678` | — | — |
| **EDA** | `11111111-1111-1111-1111-111111111111` | Watch → Phone (Notify) | 8 bytes |
| **Cardiac** | `22222222-2222-2222-2222-222222222222` | Watch → Phone (Notify) | 12 bytes |
| **Accelerometer** | `33333333-3333-3333-3333-333333333333` | Watch → Phone (Notify) | 14 bytes |
| **PPG Raw** | `44444444-4444-4444-4444-444444444444` | Watch → Phone (Notify) | 14 bytes |
| **Haptic Command** | `0000fff5-0000-1000-8000-00805f9b34fb` | Phone → Watch (Write) | Variable |
| **Sensor Status** | `55555555-5555-5555-5555-555555555555` | Watch → Phone (Read/Notify) | 8 bytes |

---

## 2. Payload Formats (Little-Endian)

### 2.1 EDA Characteristic (11111111)
```
Byte 0:    Format version (0x01)
Byte 1:    Flags [bit0=screenOn, bit1=flush, bit2-7=reserved]
Byte 2-5:  EDA conductance (float32, µS)
Byte 6-7:  Sequence number (uint16, wraps at 65535)
Total: 8 bytes
```

### 2.2 Cardiac Characteristic (22222222) — HR + IBI
```
Byte 0:    Format version (0x01)
Byte 1:    Flags [bit0=ibiValid, bit1=screenOn, bit2=flush, bit3-7=reserved]
Byte 2-3:  Heart rate (uint16, bpm × 10 for 0.1 bpm resolution)
Byte 4-5:  IBI (uint16, milliseconds, 0 = not available)
Byte 6-7:  IBI status (uint16, 0=valid, 1=calibrating, 2=unreliable)
Byte 8-9:  RMSSD (uint16, milliseconds × 10 for 0.1ms resolution)
Byte 10-11: Sequence number (uint16)
Total: 12 bytes
```

### 2.3 Accelerometer Characteristic (33333333)
```
Byte 0:    Format version (0x01)
Byte 1:    Flags [bit0=screenOn, bit1=flush, bit2-7=reserved]
Byte 2-5:  X axis (float32, m/s²)
Byte 6-9:  Y axis (float32, m/s²)
Byte 10-13: Z axis (float32, m/s²)
Total: 14 bytes
```

### 2.4 PPG Raw Characteristic (44444444)
```
Byte 0:    Format version (0x01)
Byte 1:    Flags [bit0=screenOn, bit1=flush, bit2=ppgAvailable, bit3-7=reserved]
Byte 2-5:  PPG Green (int32, raw ADC value)
Byte 6-9:  PPG IR (int32, raw ADC value)
Byte 10-13: PPG Red (int32, raw ADC value)
Total: 14 bytes

If PPG_CONTINUOUS is NOT available (bit2=0):
  All PPG values = 0 (phone ignores this characteristic)
```

### 2.5 Sensor Status Characteristic (55555555)
```
Byte 0:    Format version (0x01)
Byte 1:    Active sensors bitmask:
           bit0 = EDA active
           bit1 = HR active
           bit2 = PPG active
           bit3 = Accel active
           bit4 = SkinTemp active (future)
           bit5 = SpO2 active (future)
           bit6 = ECG active (future)
Byte 2:    Battery level (0-100%)
Byte 3:    Power mode (0=FULL, 1=ACTIVE, 2=REST, 3=SLEEP)
Byte 4-5:  Uptime seconds (uint16)
Byte 6-7:  Buffer fill level (uint16, number of buffered samples)
Total: 8 bytes
```

### 2.6 Haptic Command Characteristic (0000fff5)
```
Byte 0:    Command type (0x01=play, 0x02=stop, 0x03=configure)
Byte 1:    Pattern ID (0-255)
Byte 2:    Amplitude (0-255, maps to 0-100%)
Byte 3:    Duration (0-255, × 100ms)
Byte 4-7:  Timestamp (uint32, epoch seconds — for sync verification)
Total: 8 bytes (for play command)
```

---

## 3. Watch Implementation Rules

### 3.1 Sensor Priority & Fallback
```
1. ALWAYS start: EDA_CONTINUOUS + ACCELEROMETER_CONTINUOUS
2. TRY: PPG_CONTINUOUS
   - If succeeds: send on UUID 44444444, derive HR from peaks, send on 22222222
   - If fails (Invalid HealthTrackerType): 
     → Start HEART_RATE_CONTINUOUS instead
     → Send HR+IBI on UUID 22222222
     → Send zeros on UUID 44444444 with ppgAvailable=0 flag
3. LOG all supported tracker types (for diagnostics)
4. NEVER crash on tracker failure — graceful fallback
```

### 3.2 IBI Extraction (CRITICAL — from Samsung docs)
```
Samsung docs: "IBI values for the complete tracking times are stored 
in the FIRST data point. The other data points contain NULL."

CORRECT:
  val firstDp = dataPoints[0]
  val ibiList = firstDp.getValue(ValueKey.HeartRateSet.IBI_LIST)
  
WRONG (what we had before):
  for (dp in dataPoints) {
    val ibiList = dp.getValue(ValueKey.HeartRateSet.IBI_LIST)  // NULL for all except first!
  }
```

### 3.3 PPG ValueKeys (use NEW, not deprecated)
```
CORRECT (current SDK):
  ValueKey.PpgSet.PPG_GREEN
  ValueKey.PpgSet.PPG_IR  
  ValueKey.PpgSet.PPG_RED
  ValueKey.PpgSet.STATUS

WRONG (deprecated):
  ValueKey.PpgGreenSet.PPG_GREEN  ← DO NOT USE
```

### 3.4 Screen State & Flush
```
- Register screen on/off receiver
- When screen OFF: start 30s flush timer
- When screen ON: cancel flush timer (data arrives real-time)
- Tag every notification with flags byte (screenOn, flush bits)
```

### 3.5 Advertising
```
- NEVER stop advertising when phone connects
- Keep advertising so phone can reconnect after disconnection
- Advertising interval: 100ms (fast) for 30s after disconnect, then 1000ms (slow)
```

---

## 4. Phone Implementation Rules

### 4.1 Subscription Order
```
1. Discover HUGR service (12345678...)
2. Subscribe to Sensor Status (55555555) FIRST — know what's available
3. Subscribe to EDA (11111111)
4. Subscribe to Cardiac (22222222)
5. Subscribe to PPG (44444444) — may send zeros if PPG unavailable
6. Subscribe to Accel (33333333)
7. Start foreground service
```

### 4.2 Parser Safety
```
EVERY characteristic parser MUST:
1. Check data length >= expected minimum
2. Check format version byte (ignore if unknown version)
3. Wrap in try/catch — NEVER crash on malformed data
4. Log unexpected formats for debugging
```

### 4.3 Reconnection
```
- On disconnect: wait 3s, then scan with scanMode=BALANCED
- Exponential backoff: 3s → 6s → 12s → 24s → 30s (max 5 attempts)
- Before reconnecting: destroy() old device reference
- After reconnect: re-subscribe to all characteristics
```

### 4.4 Data Quality Tagging
```
Every received data point gets metadata:
- timestamp (phone clock, ms precision)
- delivery_mode: REALTIME (screenOn=1) | BATCHED (screenOn=0, flush=0) | FLUSH (flush=1)
- sequence_gap: true if sequence number skipped (indicates lost notification)
- quality: HIGH (realtime) | MEDIUM (flush) | LOW (batched with gaps)
```

---

## 5. Future Extension Points

When Samsung approves additional sensors:
- SKIN_TEMPERATURE: Add UUID `66666666-6666-6666-6666-666666666666`
- SPO2_ON_DEMAND: Add UUID `77777777-7777-7777-7777-777777777777`  
- ECG_ON_DEMAND: Add UUID `88888888-8888-8888-8888-888888888888`

Each new characteristic follows the same pattern:
- Byte 0 = format version
- Byte 1 = flags
- Remaining bytes = sensor-specific data

---

## 6. Decision Gate Verification

| Question | Answer |
|----------|--------|
| 1. Preserves closed loop? | ✅ Cardiac + EDA + Accel = state estimation possible |
| 2. Allows Bayesian optimisation? | ✅ Haptic command characteristic enables intervention |
| 3. Research-grade data? | ✅ Sequence numbers detect gaps, flags tag quality |
| 4. All stakeholders? | ✅ Same data serves all tiers |
| 5. Patent claims? | ✅ Self-describing format = Cluster 39, Status char = Cluster 36 |
| 6. Shortcut? | ❌ NO — this is the proper engineering solution |

---

*Historical note: this proposal was once labelled final, but the runtime audit found it was never implemented as written. Future changes require a new versioned contract, matched watch/phone tests and evidence-state reconciliation.*
