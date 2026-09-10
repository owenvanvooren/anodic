# Validation — Anodic 0

Test host: Apple M2, arm64, macOS 27.0, Java 25. Minecraft 26.2, Fabric Loader 0.19.3, Metallum 0.0.23.

## Completed

- Compilation, packaging, and all seven JUnit tests passed.
- Native tests compiled and rendered the shipping spatial and temporal Metal shaders. Temporal history reset and depth disocclusion checks passed.
- Isolated live-world smoke runs passed with vanilla rendering and Sodium 0.9.1. After the stationary-view correction, the Sodium run enabled Metal API Validation and passed after 419 filtered frames, exercising a stationary camera, camera rotation, temporal resolve, Off/Performance/Quality, F8 settings, and Shift+F8 toggle.
- The redesigned settings screen was captured and visually reviewed.

## Synthetic results

### Stationary-view correction

The original resolve discarded all sky history and repeatedly rejected silhouette history as jitter changed the nearest depth sample. This exposed the sampling pattern even with a fixed camera. The correction accumulates sky with rotation-only reprojection, selects foreground depth across the cubic reconstruction footprint, and increases history retention below 0.1 pixel of camera motion.

A 128-frame fixed-camera regression covers a flat-depth edge, a sky edge, and a foreground/background silhouette. Across the final 16 frames, worst-pixel brightness range fell from **0.0596 / 0.7085 / 0.7085** to **0.0200 / 0.0200 / 0.0200** respectively (0–1 color scale). Each scene must remain below 0.025. This measures synthetic flicker, not a guarantee that every modded scene is stable. The native regression also passes Metal API Validation. The smoke client now includes a stationary segment and verifies that history survives a full jitter cycle before rotating the camera.

Temporal AA reduced mean squared error against an 8×8 coverage reference by **24.99%** on a moving diagonal plane after history warm-up. Performance reduced error by **81.51%** on the separate static silhouette test. These are different workloads and do not compare the two modes or measure perceived resolution in Minecraft.

Original-release median offscreen GPU times, milliseconds (before the stationary-view correction's wider depth sampling):

| Resolution | Performance added cost over presentation | Quality resolve + copy | Quality history memory |
| --- | ---: | ---: | ---: |
| 1920×1080 | 0.389 | 1.370 | 31.6 MiB |
| 2560×1440 | 0.811 | 2.807 | 56.3 MiB |
| 3840×2160 | 1.180 | 5.051 | 126.6 MiB |

These measurements exclude Minecraft world rendering and do not measure FPS or input latency. Quality timing excludes presentation. The host was not thermally controlled; background work and GPU frequency affect results. Native synthetic textures differ from the game's resource workload.

## Implementation and limits

Quality uses eight-phase Halton projection jitter, two RGBA16Float histories, camera/depth reprojection, depth rejection, neighborhood clipping, and cubic reconstruction. It resolves before the hand and HUD. History resets after camera cuts, resizing, world changes, menus, and mode changes. It uses Metallum's existing command buffer and synchronization; it adds GPU work but no separate submission or explicit CPU wait.

There are no per-object motion vectors. Entities, particles, translucency, and newly revealed geometry can trail or shimmer. Long-duration resource stress, shader packs, other Apple Silicon chips, and other Metallum releases remain unverified. Mod Menu integration compiles; the smoke run verifies the keyboard route rather than clicking through Mod Menu.

## Reproduce

```sh
./gradlew build
MTL_DEBUG_LAYER=1 ./gradlew runSmokeClient -PsmokeSodium
mkdir -p build/native-test
xcrun swiftc -O tests/MetalValidation.swift -o build/native-test/spatial
build/native-test/spatial . build/native-test/spatial-results
xcrun swiftc -O tests/TemporalValidation.swift -o build/native-test/temporal
build/native-test/temporal . build/native-test/temporal-results
```

Smoke runs create their own world under `build/smoke-run`. The smoke mod, Minecraft assets, and test worlds are excluded from the release JAR and repository.
