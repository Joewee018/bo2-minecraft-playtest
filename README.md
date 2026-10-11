# BO2 Minecraft playtest

Windows x64, Minecraft **Java Edition 1.20.1**, Java 17 and Fabric API 0.92.2+1.20.1.

This repository distributes integration code and an asset-free companion. It does not include Minecraft, BO2 models/textures/animations/audio, a Nuketown world, or third-party character models. Sign in using your own Minecraft account. Independently obtain assets you are entitled to use.

## Current availability

This is an **advanced playtest**, not a one-click retail BO2 importer. Owning BO2 is necessary but not sufficient: the current importer expects the project's converted resource format. Copying a PC BO2 installation into this package does not work. The retained conversion pipeline was developed against Xbox zone data; automatic conversion directly from a retail PC installation is still pending. No game dumps or asset download links are provided.

## Install

1. Download `docs/downloads/bo2-minecraft-playtest-windows.zip` and extract it.
2. Create a separate Minecraft Java 1.20.1 Fabric installation using the official Minecraft Launcher. Log in normally. Install Fabric API 0.92.2+1.20.1 in its `mods` folder. Use Java 17.
3. Prepare your own converted resources. The directory supplied as `--resources` must contain `assets/bo2beta/` and `data/bo2beta/`. The directory supplied as `--runtime` must contain `local-bo2/weapons/`. `asset-manifest.json` lists the required paths and the exact tested file hashes. Retail archive files are not accepted. Custom meshes/hands/textures under `custom/` are optional.
4. Install Python 3.10 or later and run:

   ```powershell
   python install.py --instance "D:/Minecraft/BO2Playtest" --resources "D:/MyConvertedBO2/resources" --runtime "D:/MyConvertedBO2/runtime"
   ```

   Installation verifies every required file before writing. It creates a private local asset mod and copies the asset-free integration mod. It does not modify other instances or distribute your assets.
5. Independently install the compatible Minecraft Nuketown world in that instance's `saves` folder. The current map contract uses overworld spawn near **1633, 20, -784**, and the layout in `data/bo2beta/nuketown-spawns.json`. A differently scaled/positioned Nuketown map needs a regenerated spawn file; arbitrary worlds will not start matches.
6. Run `Start-Companion.cmd`. Keep its window open. Copy the two JVM arguments from the generated `private/jvm-arguments.txt` into the Fabric installation's Java arguments. These let a normally authenticated Minecraft Launcher connect to this session's local companion. Do not replace the existing memory arguments. Launch the Fabric installation through your normal launcher. Press Enter in the companion window after Minecraft closes to stop the companion.

The companion listens only on 127.0.0.1, uses a fresh token each session, and does not request your Minecraft or GitHub credentials. Do not publish the `private` directory or your locally generated asset mod.

## Controls

P: lobby / start / stop / difficulty / character. Left mouse: fire. Right mouse: aim, or cook a selected grenade. R: reload. V: melee. Z/C: peek. O: local aim-assist menu. TDM, FFA and an adapted 20-stage Gun Game are available. Player and bot health is 200 HP. Respawns and TDM/FFA loadouts are randomized inside Nuketown. Custom characters appear only when their locally supplied body and first-person hand files are installed; otherwise bots use CDC and the player can switch between CDC and default Steve.

## Scope

This is a fan-made experimental integration. It is not affiliated with Mojang, Microsoft, Activision, Treyarch or the character creators. It is not a complete 1:1 BO2 multiplayer implementation. Minecraft collision/pathfinding, adapted Gun Game stages, partial weapon aliases, and missing native recoil/penetration/perks remain limitations. Back up your own worlds and use a separate instance.

For a bug report, include mode, bot count/difficulty, selected gun, exact actions, and relevant log lines. Remove session tokens and account information. Do not attach BO2 content, private asset mods, world archives, or character models to public issues.

## Build the companion

Install a current Rust toolchain, then `cargo build --manifest-path companion/Cargo.toml --release --locked`. Weapon-profile unit tests require `BASEPLATE_ASSETS` pointing to locally converted test catalogs. The source contains no game content.

## Publish

GitHub Pages can serve `/docs` on the `main` branch. The page's relative download link also works in a project Pages site. The public package is audited to contain only compiled integration classes, root mod metadata, companion code/binary, installer, docs and asset hashes.

## Additional map profiles

Revolution and Strike support the same match core through a world-local `bo2-match-map.json`. Obtain the Minecraft worlds independently. The profile contains map name, entry point, arena polygon, valid standing spawn points and movement/spawn height bounds. No map archives or converted worlds are distributed. Arbitrary maps need their profiles prepared and collision-tested before use.
