# Build 32w Diagnostic Results — 2026-08-18

## VIDEO ANALYSIS FINDINGS:

### Supported tracker types on Watch 8 (from diagnostic log):
```
PPG_GREEN, ACCELEROMETER, PPG_IR,
ECG, RED, SPO2, SPO2_ON_DEMAND,
HEART_RATE, HEART_RATE_CONTINUOUS,
BIA, BIA_ON_DEMAND,
SWEAT_LOSS, SKIN_TEMPERATURE,
SKIN_TEMPERATURE_ON_DEMAND,
SKIN_TEMPERATURE_CONTINUOUS,
PPG_ON_DEMAND, PPG_CONTINUOUS,
EDA_CONTINUOUS, MF_BIA_ON_DEMAND
```

### What started: ONLY EDA
### Error: "TRACKER ERROR: Invalid HealthTrackerType"
### HR/Accel: DID NOT START (killed by the shared try/catch)

## ROOT CAUSE ANALYSIS:

### Problem 1: Single try/catch wraps all tracker starts
Code at line 210-248: ONE try block wraps EDA + PPG + Accel.
When PPG_CONTINUOUS throws (line 223), execution jumps to catch (line 245).
HR fallback and Accelerometer NEVER execute.

### Problem 2: PPG_CONTINUOUS in supported list but throws on getHealthTracker()
Samsung docs confirm: `supportHealthTrackerTypes` shows HARDWARE capability.
`getHealthTracker()` checks SDK POLICY (keystore approval).
PPG_CONTINUOUS is in the hardware list but our keystore may not have the policy downloaded.

Samsung FAQ says: "SDK_POLICY_ERROR occurs when your app's package name or 
SHA-256 signature does not match the information registered with Samsung."

BUT: Our EDA_CONTINUOUS works fine with the same keystore. So the keystore IS registered.
The issue is specifically that PPG_CONTINUOUS policy hasn't propagated to this watch.

### Problem 3: Samsung says "each tracker needs its own HealthTracker instance"
From getting-started guide: "If you would like to get another kind of sensor data, 
create a new HealthTracker instance and add a separate listener to it."
This confirms each tracker should be independent — failure of one should NOT affect others.

## FIX REQUIRED:
1. Wrap EACH tracker start in its OWN try/catch
2. If PPG_CONTINUOUS throws → catch it → try HR fallback → continue to Accel
3. This is NOT adhoc — it's the documented Samsung pattern (independent trackers)

## ADDITIONAL DISCOVERY:
The Watch 8 hardware supports ALL of these:
- SKIN_TEMPERATURE_CONTINUOUS ← we requested this from Samsung
- ECG ← we requested this
- SPO2, SPO2_ON_DEMAND ← we requested this
- BIA, BIA_ON_DEMAND ← body impedance analysis (not requested yet!)
- SWEAT_LOSS ← not requested yet!
- MF_BIA_ON_DEMAND ← multi-frequency BIA (not requested yet!)

## PATENT IMPLICATIONS:
- BIA (Body Impedance Analysis) could enable body composition tracking
- Multi-frequency BIA could improve EDA disambiguation (separate skin impedance from body impedance)
- SWEAT_LOSS directly relates to EDA/sympathetic activation measurement
- These expand Patent Clusters 33 (substance disambiguation) and 40 (multi-modal decomposition)
