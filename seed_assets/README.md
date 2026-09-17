# Seed assets

Files preserved from the legacy 2015 app (see `docs/PLAN.md` §P0) before the
"table rase" commit removed the old Java source tree. They have no home yet
in the new module layout and will be moved into place during later phases:

- `challenge.bin` → `app/src/main/res/raw/challenge.bin` (P4/P9). 245 challenge
  levels, 14 bytes each. **Irreplaceable** — do not regenerate, do not edit.
- `strings/values*/strings.xml` → source material for the localized strings
  rewrite (P9). Keys will be renamed to snake_case and Bluetooth-only keys
  dropped; translations (de, es, fr, it, ru) are reused as a base.
- `ic_launcher_legacy_reference.png` → visual reference only, for redrawing
  the adaptive vector launcher icon (P9). Not used as-is in the new app.

This directory is temporary and should be empty (and removable) once P9 is
complete.
