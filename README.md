# CombatLog

A modern, high-performance, enterprise-grade combat logging and PvP punishment system built specifically for **Paper** and **Spigot** servers running Minecraft 1.21.x.

---

## ⚡ Overview

**CombatLog** prevents combat evasion (combat logging) during PvP encounters. When a player disconnects while tagged in active combat, CombatLog spawns a fully authentic, native player-skin NPC in their place. Opponents can continue fighting the NPC, deal damage, and claim the victim's loot upon defeat.

CombatLog is engineered for zero-compromise performance:
- **No Heavy External NPC Dependencies**: Operates completely autonomously without requiring Citizens, ProtocolLib, or external packet hooks.
- **Zero File I/O Overhead**: Eliminates sluggish disk serialization during deaths; all state tracking is handled through thread-safe in-memory registries.
- **Native Mojang & Paper 1.21.1 Lifecycle**: Leverages connectionless server-side entity tracking, modern TextDisplay entities, and vanilla respawn mechanics.

---

## ⚔️ Key Features

### 1. Native Player-Skin Combat NPCs
- **Exact Visual Replicas**: Spawns an authentic player avatar matching the logged-out player's exact skin textures (`PlayerProfile` property maps), worn armor, main hand, and offhand equipment.
- **Full 3D Skin Layer Rendering**: Transmits complete skin metadata bitmasks (`0x7F`) so player outer layers (jackets, sleeves, pants, hats) render in full 3D detail.
- **Seamless Rejoining & Health Synchronization**: If the player logs back in before their combat NPC expires or dies, their health is dynamically synchronized to the NPC's remaining HP and the NPC is safely despawned.

### 2. Unified Overhead Health & Nametag Display
- **Zero-Lag TextDisplay Architecture**: Replaces legacy, heavy ArmorStands with native Minecraft 1.21 `TextDisplay` entities for billboarding and smooth rendering.
- **Instant Spawn Visibility**: The combined nametag and color-interpolated health indicator (`[PlayerName] ❤ 20.0/20.0 [■■■■■■■■■■]`) renders immediately upon NPC creation without waiting for the first damage event.
- **Live Dynamic Updates**: Re-calculates health bar segments, colors, and absorption shields in real-time as damage is taken.

### 3. Native Vanilla Death & Inventory Drops
- **Authentic Loot Drops**: Upon NPC defeat, all inventory items, armor, offhand items, and accumulated experience points drop naturally at the death coordinates.
- **Clean Reconnect Lifecycle**: Killed combat loggers have their stored inventory cleared upon rejoining and trigger standard vanilla death handling (`setHealth(0.0)`), allowing Minecraft to naturally manage bed, respawn anchor, and world spawn locations.

### 4. BetterTeams & Trust System
- **BetterTeams Integration**: Automatically detects [BetterTeams](https://github.com/booksaw/BetterTeams) as a soft dependency. Teammates and allies are prevented from tagging each other in combat.
- **Custom Player Trust System**: Standalone buddy management (`/combatlog trust <add|remove|list>`) allowing friendly players to duel or train without accidental combat punishment if BetterTeams is not present.

### 5. Asynchronous Discord Webhook Logging
- **100% Non-Blocking**: Dispatches infraction reports using native Java 11 `HttpClient` on an independent thread pool, ensuring zero tick drops or main-thread hitching.
- **Rich Embed Reports**: Transmits player names, UUIDs, opponent details, remaining combat timers, exact coordinates, and enforcement outcomes directly to staff Discord channels.

### 6. Configurable Command Suppression
- **PvP Exploit Prevention**: Blocks teleportation, flight, god-mode, homes, and private messaging commands during active combat tags.
- Fully customizable list of blocked command prefixes in `config.yml`.

---

## 📁 Project Architecture

```
CombatLog/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── settings.gradle.kts
├── LICENSE
├── README.md
├── CHANGELOG.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── me/badambarfi/combatlog/
    │   │       ├── CombatLogPlugin.java          # JavaPlugin entrypoint & lifecycle
    │   │       ├── api/
    │   │       │   ├── CombatManager.java        # Public combat management API
    │   │       │   └── CombatTag.java            # Immutable combat session record
    │   │       ├── command/
    │   │       │   └── CombatLogCommand.java     # Command executors & tab completion
    │   │       ├── config/
    │   │       │   └── ConfigManager.java        # Configuration loading & accessors
    │   │       ├── listener/
    │   │       │   ├── CombatListener.java       # PvP attack & projectile tracking
    │   │       │   ├── CombatNpcListener.java    # NPC damage, death & player respawn
    │   │       │   ├── PlayerCommandListener.java# Command blocking during combat
    │   │       │   └── PlayerQuitListener.java   # Combat logout handling & NPC spawning
    │   │       ├── manager/
    │   │       │   └── CombatManagerImpl.java    # Scheduled ticking manager implementation
    │   │       ├── npc/
    │   │       │   ├── CombatNpc.java            # NPC session state & inventory holder
    │   │       │   └── CombatNpcManager.java     # Connectionless NMS player spawner & tracker
    │   │       ├── util/
    │   │       │   └── ChatColorUtil.java        # Adventure & MiniMessage / legacy color parser
    │   │       └── webhook/
    │   │           └── DiscordWebhookService.java# Async non-blocking Discord webhook dispatcher
    │   └── resources/
    │       ├── plugin.yml                        # Plugin descriptor
    │       └── config.yml                        # Default configuration file
    └── test/
        └── java/
            └── me/badambarfi/combatlog/
                └── api/
                    └── CombatTagTest.java        # JUnit 5 test suite
```

---

## 🔧 Requirements & Compatibility

- **Server Software**: Paper, Purpur, or Spigot (1.21.11+).
- **Java Runtime**: Java 21 or higher.
- **Dependencies**: None required (BetterTeams supported as optional soft-dependency).

---

## 📦 Building from Source

Build the plugin JAR using the included Gradle wrapper:

```bash
# Linux / macOS
./gradlew build

# Windows (Command Prompt / PowerShell)
.\gradlew.bat build
```

Compiled artifacts will be located in:
`build/libs/CombatLog-2.3.7.jar`

---

## ⚙️ Configuration Reference (`config.yml`)

```yaml
combat:
  duration-seconds: 15
  block-commands: true
  blocked-commands:
    - spawn
    - home
    - sethome
    - tp
    - tpa
    - tpaccept
    - tpadeny
    - warp
    - back
    - ec
    - enderchest
    - feed
    - heal
    - fly
    - repair
    - "plot home"
    - "clan home base"
    - "trade accept"

punishment:
  kill-on-quit: false
  broadcast-message: true

npc:
  duration-seconds: 15
  name-format: "%player%"
  killed-broadcast: "&c%player%'s combat NPC was eliminated by &e%killer%&c!"
  killed-broadcast-unknown: "&c%player%'s combat NPC was eliminated!"
  rejoin-notice: "&eYou rejoined before your combat NPC expired! Your health has been synchronized and you are still in combat."

webhook:
  enabled: false
  url: ""
  username: "CombatLog Watcher"
  avatar-url: ""

messages:
  combat-enter: "&cYou are now in combat with &e%opponent%&c! Do not disconnect for &e%seconds%s&c."
  combat-expired: "&aYou are no longer in combat."
  combat-quit-punish: "&c%player% logged out during combat and was eliminated!"
  command-blocked: "&cYou cannot execute that command while in combat!"
  trust-disabled: "&cThe trust system is disabled because BetterTeams is managing team combat."
  trust-added: "&aYou now trust &e%player%&a. Your attacks against them will not tag either of you in combat."
  trust-removed: "&cYou no longer trust &e%player%&c."
  trust-self: "&cYou cannot add yourself to your trust list."
  trust-not-found: "&cPlayer not found."
  trust-list-empty: "&7You do not trust any players."
  trust-list-header: "&6Trusted players: &e%players%"
```

---

## 📋 Commands & Permissions

| Command | Description | Default Permission |
| :--- | :--- | :--- |
| `/combatlog status` | Displays your active combat tag duration | `combatlog.use` (All players) |
| `/combatlog reload` | Reloads `config.yml` from disk | `combatlog.admin` (OP) |
| `/combatlog untag <player>` | Manually clears combat tag for a player | `combatlog.admin` (OP) |
| `/combatlog trust add <player>` | Grants combat immunity between players | `combatlog.use` (All players) |
| `/combatlog trust remove <player>` | Revokes combat immunity | `combatlog.use` (All players) |
| `/combatlog trust list` | Lists all players currently trusted | `combatlog.use` (All players) |

**Bypass Permission**:
- `combatlog.bypass` — Players with this permission are exempt from combat tagging and punishment.

---

## 📄 License

Copyright (c) 2026 badambarfi. All Rights Reserved.

Proprietary software. Unauthorized reproduction, modification, distribution, or decompilation of this source code and compiled binaries is strictly prohibited.
