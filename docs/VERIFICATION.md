# Verification · 1.0.12

This is a development preview, not a claim that every original performance or visual requirement has completed acceptance.

| Area | Evidence |
| --- | --- |
| Local core tests | 37 passed, 0 failures, 0 errors; excludes the template arithmetic test |
| Build | `testDebugUnitTest assembleDebug` passed |
| Packaged assets | 28 GLB and 198 WAV; localized name and custom icon checked |
| Launcher icon | Full-square PNG, all pixels opaque; RGBA matches the packaged resource |
| Real device | 1.0.12 code13 installed and started |
| Prior real-device combat | Laser: 3 kills; web: 2 kills; HIT and GAME_OVER events observed |
| Audio lifetime | Repeated playback and emitter destruction check passed |
| Native projectile motion | About 0.56–0.58m in 0.3s at a requested 2m/s |
| Native collision | Regular and reparented portal collider: 1.85m measured hit; 10m/s swept query passed |
| Surface orientation | Floor, wall X and wall Z orientation passed before/after portal emergence |

Tests cover gesture capture/release and reset, repeated custom-pose registration, tool pickup and visibility, attachment noise/recovery, upright panel heading, wall-to-floor transition and surface projection. Debug-only native checks do not simulate player input or award game points.

Still pending: measured end-to-end response latency and perceived spatial-audio angular precision, sustained performance, controller activation behavior across firmware, energy/pulse acceptance, and visual review of the latest head-following UI, re-registration navigation, launcher mask and wall emergence. An earlier laser/web hit does not prove every weapon or revised visual passes.

Raw device logs, serial numbers, local filesystem paths and headset passthrough captures are intentionally not included in the public repository. The media shown in the README are offline asset previews.
