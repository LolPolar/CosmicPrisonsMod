# Cosmic Prisons Companion Paper Plugin Guide

This repo includes `paper-plugin/`, a Paper plugin module that speaks the same `servercompanion:main` protocol as the Fabric client mod.

## What is covered now

- Handshake/session flow (`ClientHelloC2S` -> `ServerHelloS2C` -> active session).
- Join lifecycle handling (reminder for players without companion handshake).
- Event timer widget broadcasting.
- Inventory overlays for **money notes** and **energy** using a **PDC long amount**.
- Overlay refresh on pickup/drop/click/drag and periodic scan.

## The exact thing you asked: money note + energy overlay from PDC

The plugin reads a `LONG` from item meta PDC and displays it as compact text (e.g. `12.3k`, `5.50m`) on supported materials.

Config (`paper-plugin/src/main/resources/config.yml`):

```yaml
inventory-overlays:
  amount-key: cosmic:amount
  scan-interval-ticks: 4
  money-note-materials:
    - PAPER
  energy-materials:
    - GLOWSTONE_DUST
```

How it works:

1. For each companion player inventory slot `0..35`, plugin checks item meta PDC key `amount-key`.
2. If material is in `money-note-materials`, sends overlay type `OVERLAY_TYPE_MONEY_NOTE`.
3. If material is in `energy-materials`, sends overlay type `OVERLAY_TYPE_COSMIC_ENERGY`.
4. Overlay payload auto-updates when items are picked up/dropped/moved, plus periodic scan.

So yes: pickup/drop/new stack/split/merge changes are automatically pushed.

## Commands

- `/companiondemo` -> sends sample widget + marker + current inventory overlays.
- `/companiontimer list`
- `/companiontimer add <key> <seconds> <display name...>`
- `/companiontimer remove <key>`

## API usage from your other plugins

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("CosmicCompanionPaper");
if (!(plugin instanceof CosmicCompanionService service)) {
    return;
}

if (!service.isCompanionPlayer(player)) {
    return; // wait until handshake exists
}

service.sendHudWidgets(player, List.of(
    new CosmicCompanionService.HudWidgetPayload("events", List.of("Meteorite: 00:58"), 3)
));

service.refreshInventoryOverlays(player); // force immediate PDC overlay refresh

service.sendEntityMarkerDelta(
    player,
    ProtocolConstants.MARKER_TYPE_GANG_PING_BEACON,
    List.of(player.getEntityId()),
    List.of()
);
```

## Join setup recommendation

- On join: do normal server setup.
- Before sending companion payloads: check `service.isCompanionPlayer(player)`.
- If false: either queue retry or skip companion-only visuals.
- On quit: session is cleaned automatically.

## Tests included

- Protocol codec tests.
- Event timer tests.
- Amount formatter tests used by inventory overlay display text.

## Build

```bash
./gradlew :paper-plugin:build
```

Copy jar from `paper-plugin/build/libs/` into your Paper `plugins/` directory.
