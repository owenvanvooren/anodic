# Anodic

Native Metal antialiasing for Minecraft on Apple Silicon.

- **Quality:** temporal AA with subpixel camera jitter, depth reprojection, and history clipping. Keeps the hand and HUD sharp. Default for new installs.
- **Performance:** lightweight spatial AA inside Metallum's presentation pass. No history buffers; also filters the HUD.
- **Off:** original rendering.

Requires Minecraft **26.2**, Fabric Loader **0.19.3+**, Java **25+**, and [Metallum **0.0.23**](https://modrinth.com/mod/metallum-mc). Other Metallum versions are unsupported.

Download [`anodic-0.jar`](https://github.com/owenvanvooren/anodic/releases/latest) into your instance's `mods` folder. Remove older Anodic or Clarity JARs first.

Open settings with **F8** (sometimes **fn+F8**) or **Mod Menu → Anodic → Configure**. Rebind under **Controls → Key Binds → Anodic**. **Shift+F8** toggles AA while playing. Settings save locally.

Quality is experimental: it uses camera motion and depth, without per-object motion vectors. Moving entities and translucent effects can trail or shimmer. Use Performance if Quality costs too much GPU time. See [validation](VALIDATION.md).

Build with Java 25: `./gradlew build`. Output: `build/libs/anodic-0.jar`.

MIT licensed.
