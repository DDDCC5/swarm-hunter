# Verification - 1.1.0

This debug candidate is not a claim of completed physical-room gameplay or performance acceptance.

| Area | Evidence |
| --- | --- |
| Unit tests | 55 passed, 0 failures, 0 errors (includes template arithmetic test) |
| Build | testDebugUnitTest assembleDebug passed |
| Experimental API consistency | Source/manifest checker passed |
| Packaged assets | 32 GLB, 180 SFX WAV, 36 bilingual voice WAV; 12 upgraded GLB hashes match approved media |
| Launcher icon | Existing full-square custom icon retained and packaged |
| Runtime startup | Candidate installed/launched on hardware and compatible 6.2.1 emulator |
| Imported animations | Native check of 9 model clip sets, duration and playback speed passed |
| Native probes | Audio reuse, portal/surface orientation, projectile movement and swept collision passed |

New regression coverage includes complete attack recovery/contact timing, stride mapping, nearest surface/enemy collision, wall-only bounded ricochets, short-beam endpoints, release-gated/limited/non-overlapping traps, full-segment wall occlusion, stale anchor rejection and temporary tracking grace. Debug probes do not simulate player input or award points.

Pending human acceptance: PBR/alpha appearance, foot contact and attack transitions, physical ricochets, tabletop/wall placement and trap triggering, tracking comfort, voice quality, sustained frame rate and end-to-end gameplay. Measured firing latency and perceived spatial-audio angular accuracy remain unverified. Earlier 1.0.12 laser/web combat results do not establish acceptance of these revised features.

No raw device logs, serial numbers, local filesystem paths, headset passthrough captures, internal execution ledgers or submission materials are included in the public repository.
