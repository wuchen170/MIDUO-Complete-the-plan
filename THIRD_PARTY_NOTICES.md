## Google Material Symbols

The `ic_home_*.xml` vectors in `app/src/main/res/drawable` are adapted from
[Google Material Symbols Rounded](https://github.com/google/material-design-icons/tree/master/symbols/android)
with Fill enabled. The vectors are licensed under the
[Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE).
Their theme tint was removed so the home screen can render them in white on colored backgrounds.


## HyperOS4SwipeGate (Runtime broadcast ABI reference)

The native desktop clock settings carrier references the HyperOS Runtime broadcast ABI
verified by [HyperOS4SwipeGate](https://github.com/PzHown/HyperOS4SwipeGate),
Copyright (c) 2026 PzHown, MIT License. The MIT license text is bundled in
`app/src/main/assets/licenses/HyperOS4SwipeGate-MIT.txt`. The clock Hook and
formatting profile are independently implemented for this project's target device.

## Deadliner soft-glass navigation

The maintained `com.aritxonly.deadliner` material/navigation fork is synchronized for
bottom navigation with local Deadliner revision `5499df09383e84a120e19bd0c629c6e6ffa7ef0d`.
Copyright (c) 2024 Aritx Zhou, MIT License, included in
`app/src/main/assets/licenses/Deadliner-MIT.txt`. Source paths, local integration changes
and the runtime conventions are described in `docs/ARCHITECTURE.md`.

The optional official material dependency closure is vendored under
`third_party/miuix-glass`, from MIUIX PR 423 commit
`ec4c96963abf206ab2f2b0253a5efca71ba28043`, Apache-2.0. Copyright headers,
upstream hashes, and license are retained. The license is also included in
`app/src/main/assets/licenses/miuix-glass-Apache-2.0.txt`.

## AndroidLiquidGlass shader design reference

The self-developed soft-glass pipeline references shader design from
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
licensed under Apache-2.0. This attribution describes a design reference;
our material recipes, sampling adapters and host integrations are maintained locally.
The Apache-2.0 license text is bundled in
`app/src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt`.

## Upstream projects this module integrates

The desktop / camera / wallpaper half is ported from
[HyperGlassFOLD](https://github.com/wuchen710/HyperGlassFOLD) and the floating navigation half from
[HyperModifier](https://github.com/AritxOnly/HyperModifier). Both upstream projects are GPL-3.0;
this integration is distributed under the same terms, see `LICENSE` and `README.md`.

`app/src/main/jniLibs/arm64-v8a/libhgshim.so` is built from `jni/shim.c` in this repository
(build instructions in `jni/README.md`).

`app/src/main/jniLibs/arm64-v8a/libhyperglass.so` is a **prebuilt payload from upstream
HyperGlassFOLD**; its source is not included here. If you redistribute this repository under
GPL-3.0, verify the upstream license / source availability first, or remove that library and the
`native_init.list` entry from your release. See `docs/PUBLISHING.md`.

