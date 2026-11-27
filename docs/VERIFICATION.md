# Verification — 1.3.2 (versionCode 20)

This debug build is a development preview, not completed physical-room gameplay or performance acceptance.

| Area | Evidence |
| --- | --- |
| Unit tests | 108 passed, 0 failures/errors |
| Build | assembleDebug and testDebugUnitTest passed |
| Experimental API consistency | Source/manifest checker passed; no new experimental opt-ins |
| Packaged resources | 67 model/metadata/thumbnail files match local approved bytes, including32 GLB |
| Creature assets | 8Tripo-source rigged creatures; 8moving one-second fall clips verified |
| Arrival audio | 16distinct dry-mono48kHz arrival PCM recordings; 8per-species rhythm rates |
| Runtime startup | Final candidate installed/launched on hardware and compatible6.2.1 emulator |
| Native animation imports | 17model clip sets, including creature attack/fall durations and tool fire clips |
| Native probes | Audio reuse/playback-rate command, floor/ceiling/wall orientation, projectile movement and swept collision passed |

Regression coverage includes registration completion/current-pose firing, rotation/scale-invariant hand features, stopping on mismatch/tracking loss, all four tool cadences, gaze-plane selection, release-gated traps, finite surface bounds, arrival visibility gating, proportional nest sizing, density progression, death staging, and continuous oblique ceiling-to-wall turn entry.

The ceiling feature requires a real ceiling plane in the room scan. Placement uses HMD view-centre gaze, not eye tracking. Debug probes verify native APIs and transforms without simulating reactive gameplay or awarding points.

Human acceptance remains pending for actual portal visibility, physical AV timing, species sound recognition, death appearance, room-surface foot contact/corners, ricochets, traps, hand tracking comfort, voice quality, sustained frame rate and complete gameplay. Firing latency and perceived spatial-audio angular accuracy have not been certified.

Raw device logs, serial numbers, local filesystem paths, headset passthrough captures, internal execution ledgers and submission materials are excluded from this public release.
