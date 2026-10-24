# Local Android position conversion patch

## Provenance and scope

- Upstream package: `geolocator_android` 5.0.3, already resolved by this workspace.
- Source: the existing local Pub cache, `$env:LOCALAPPDATA/Pub/Cache/hosted/pub.dev/geolocator_android-5.0.3`.
- Upstream repository reference is retained in `pubspec.yaml`; no remote checkout or download was performed.
- Local version: `5.0.3+rutafija.1`, `publish_to: 'none'`.
- Copied package files: `lib/`, Android source/configuration, `pubspec.yaml`, `LICENSE`, `AUTHORS`, `README.md` and `CHANGELOG.md`.
- Excluded: examples, package-level test fixtures, `android/build`, `android/.gradle`, generated caches and private configuration.
- `UPSTREAM-SOURCE.sha256` records the SHA-256 of every copied upstream file before local modifications. It is an immutable baseline, not a manifest of the patched files.

All copied files were compared with their cached source before local changes. The upstream licence and authors file are preserved byte for byte.

## Reproduction boundary

The initial vendor copy intentionally leaves `lib/src/types/android_position.dart` unchanged. The contract regression at `../../test/features/location/l08_android_position_contract_test.dart` exercises the real `AndroidPosition.fromMap` conversion with synthetic platform-channel data before a patch is applied. Preparing that test is not evidence that it was executed.

The resolved `geolocator_platform_interface` 4.3.0 provides presence indicators such as `hasAccuracy`; its `Position.fromMap` distinguishes an absent measurement from a measured zero. The Android wrapper currently copies numeric values into another `Position` subtype without copying those indicators. The application's existing accuracy validation must remain in force.

## Applied local differences

1. `pubspec.yaml`: local version suffix and publication disabled.
2. `lib/src/types/android_position.dart`: expose all seven existing presence indicators as optional superclass constructor arguments and propagate the parsed indicators in `fromMap`. Absent indicators retain `Position`'s original defaults; values, timestamps and native provider behaviour are untouched.
3. This provenance document and the original source hash manifest.

`LOCAL-CHANGES.patch` records the exact textual differences for the two modified upstream files. `PATCHED-SOURCE.sha256` records their new hashes alongside every unmodified copied upstream file. These manifests exclude generated caches and provenance files and do not contain sensor data.

No other package implementation or Android provider behaviour is intended to change. In particular, preserve the foreground requirement, permission checks, sensor timestamp, accuracy validation, timeout, request cancellation, provider selection and mock-location flag. No coordinates or captured runtime positions belong in these records.

## Status

The parent agent executed the unpatched contract tests and archived the failing reproduction in `backend/target/l08-gps-android-contract-red.log` with exit code 1: eight tests, two passed and six failed because presence indicators were false instead of true. The original source manifest and failing reproduction remain unchanged.

`PATCH_READY`: the conversion patch is now applied. The patch author has not executed tests or a build; a passing regression run and physical validation still require their own evidence. The existing application accuracy checks and controller state guards have not been changed.
