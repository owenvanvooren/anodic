<img width="211" height="44" alt="Frame" src="https://github.com/user-attachments/assets/442bccd0-6116-46ee-89bf-8403133471ed" />


Native Metal antialiasing for Minecraft on Apple Silicon.

- **Quality:** temporal AA with subpixel camera jitter, depth reprojection, and history clipping. Keeps the hand and HUD sharp. Default for new installs.
- **Performance:** lightweight spatial AA inside Metallum's presentation pass. No history buffers; also filters the HUD.
- **Off:** original rendering.

<img width="1506" height="680" alt="quality comparison" src="https://github.com/user-attachments/assets/18078021-9e10-4a76-9ff9-d616d18fa4e9" />

Requires Minecraft **26.2**, Fabric Loader **0.19.3+**, Java **25+**, and [Metallum **0.0.23+**](https://modrinth.com/mod/metallum-mc). Other Metallum versions are unsupported.

Download [`anodic-0.jar`](https://github.com/owenvanvooren/anodic/releases/latest) into your instance's `mods` folder. Remove older Anodic JARs first.

Open settings with **F8** (sometimes **fn+F8**) or **Mod Menu → Anodic → Configure**. Rebind under **Controls → Key Binds → Anodic**. **Shift+F8** toggles AA while playing. Settings save locally.

Quality is experimental: it uses camera motion and depth, without per-object motion vectors. Moving entities and translucent effects can trail or shimmer. Objects may also jitter even if they are not moving. Use Performance if Quality costs too much GPU time. See [validation](VALIDATION.md).

Build with Java 25: `./gradlew build`. Output: `build/libs/anodic-0.jar`.

vibe coded as heck (because there's no way i'm better at programming than Astra)

MIT licensed.
