# DRNAS v0.5.16: Proton 11 anonymous PE-image A/B experiment

## Reproduced on Poco F8 Ultra, Android 16 (2026-10-14)

v0.5.15 RAW trace (23:45:31) and full session trace:

- `wine-10.0` CLI returned exit 0; wineserver version succeeded and persistent server remained alive.
- Android app UID read/write probe OK; Wine native `ntdll.so` readable.
- `ANON_RW_TO_RX_OK` on both Android packaged host .so and staged PE ntdll.
- `PRIVATE_R_TO_RX_OK` on both.
- `PRIVATE_RW_TO_RX_DENIED=13 (Permission denied)` on both.
- Wine `ntdll.dll` `.text` and `.hexpthk` failed executable protection after mapping/relocation; then stack-overflow error and `WINEBOOT_EXIT_1`.
- Direct `execve` of app-private wineserver was denied EACCES, but launch via system `linker64` and foreground server succeeded.

**Interpretation:** SELinux executable-modification policy is the primary suspect.
This evidence supports a dirty *file-backed private* executable mapping restriction, not a blanket PROT_EXEC ban and not missing general storage permission.
The anonymous mapping probe is an indicator, not a proof that any arbitrary Windows PE mapping will execute correctly.
Do not ask for 'all files access', root or changing SELinux policy to fix this issue.

## Experimental fix path

Switch only the v0.5.16 LAB APK's bundled Proton archive to GameNative Proton 11.0-2 ARM64EC (2026-09-28).
The release documents "Anonymous PE-image mapping for bionic", specifically relevant to the diagnostic signature.
Pin exactly the published release archive by GitHub asset SHA-256:
`fffa467241bdae3eacd6ceb7e8096bb7793d617ce53a198dae8bc63a3453f595`.
The Wine 10 v0.5.15 LAB installation remains untouched.
All other default versions (FEX 2609, DXVK 1.10.3) remain the same.

Source: https://github.com/GameNative/proton-wine/releases/tag/proton-11.0-2-20260928

## A/B measurement and pass/reject

1. v0.5.15 control: `wine-10.0` exit 0, Wineboot exit 1, `ntdll.dll .text/.hexpthk` RX denial.
2. v0.5.16 treatment: `wine-11.*` exit 0, native wineserver preflight exit 0, Wineboot execution checked.
3. Observe `execmap` findings; these may remain DENIED for file-backed dirty mappings, even if Wine's new anonymous mapping succeeds.
4. PASS only with Wineboot exit 0, completion evidence, changed registries and independent Windows CMD marker (all currently checked by the app).
5. PROGRESS if RX denial disappears and process reaches a different meaningful failure, but still classify boot as NOT VERIFIED.
6. REJECT if the new Wine package fails to stage, native Wine CLI fails, or prior permission symptoms persist without forward progress.
7. Export RAW Wine trace and complete console log; no fabricated readiness or GPU/X11 claims.

Do not merge this experimental path into `main` until a reproducible, on-device verified result.
