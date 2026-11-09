# Quick Claim

Claim land with a wand and manage it from an Amethyst Claim Lectern. Includes personal claims, factions, a shared upkeep vault, and admin controls.

Requires Minecraft 1.20.1, Forge 47.4.0 or newer in the 47.x series, and Java 17. Download the JAR from the repository's Releases page and put the same version in the server's and each player's `mods` folder. Remove the old Quick Claim JAR when updating.

## Claiming land

Use the wand on one corner, then crouch and use it on the opposite corner. The corners set the height too. If both sit at floor level, you claim only that layer. Right-click a claim lectern to claim the selected area.

In the Claims tab, you can rename a claim, set permissions, or change its shape. Select another area with the wand to add land, cut some out, or replace the existing area.

Personal claims start with 2,560 blocks of horizontal area. You can trust four other players to use blocks and open the vault. Being trusted doesn't grant building, breaking, or claim-management permissions.

## Finding home

Choose **Set main claim** in the Claims tab. Press `P` to ping your personal home or `O` for your faction's home. Both keys can be changed in Controls.

The same ping is available through commands:

- `/ping` or `/ping personal` — your personal main claim.
- `/ping faction` — your faction's main claim.
- `/ping <main claim name>` — either home by name, including names with spaces.

Pings last 15 seconds. They only point to your personal or faction main claim.

## Factions

You can join one faction and keep your personal claims. Factions have no member limit. The owner invites players, removes members, changes the faction title and hex color, and appoints up to four managers.

Members can build on faction land. The owner and managers can also edit claims, change permissions, and use the shared vault. When anyone enters faction land, the claim popup uses that faction's color.

A new faction gets 2,560 blocks of horizontal claim area. Each member after the owner adds another 256 blocks. Invitations don't add space until they're accepted.

## Upkeep

By default, creating a faction costs 200 diamonds. Upkeep is due every 30 real days. At 10 members, the payment rises to 250 diamonds, then increases by another 50 for each five members after that.

Put payment items in the faction vault. Missed payments have a seven-day grace period: protection stays on and the faction can still expand. After seven days, protection and expansion stop until the debt is paid. The claims remain saved. Time spent offline counts too.

Open the vault through a lectern inside the faction's land. If the lectern is removed, another lectern on that land can access the same items. Personal vaults don't have upkeep.

## Admin controls

OP level 2 unlocks the Admin tab. It includes server claims, a protection bypass, PvP overrides, claim allowances, and payment settings. Server claims have no upkeep, and the bypass resets when you log out.

In the payment editor, click an inventory item to use it as currency. Drag the sliders or type the amount and interval: 1–10,000 items and 1–365 real days. Existing charges keep their original price.

Claim permissions control visitors' block use, placing, breaking, PvP, hostile spawning, and mob griefing. Passive mobs still spawn. Admin PvP overrides take priority over claim settings. Other command shortcuts are under `/claim` and `/faction`.

## Crafting

At a crafting table, stack an amethyst shard (A), a gold ingot (G), and a stick (S) from top to bottom to make the wand. Leave the other slots empty.

```text
Wand       Lectern
. A .      A B A
. G .      . L .
. S .      G C G
```

In the lectern pattern, B is a book, L is a lectern, and C is a chest.

## Models and animations

The wand and claim lectern have custom models and textures. With GeckoLib 4.8.4 installed, the lectern's book opens when a player comes within three blocks, and its drawer opens while the vault is in use. The drawer stays open until the last viewer closes it.

GeckoLib is optional. Without it, the same models render in their closed pose, and all claim and vault controls still work.

## Atlas and Carry On

With Map Atlases 1.20-6.0.20, claim borders and home markers appear on the world map and minimap. Faction borders use the faction's hex color; unprotected claims appear gray. Your personal home appears as a small house, tinted to match its claim border. You also see your faction's home marker. Borders follow map movement, zoom, rotation, dimension, and height slice. Claim data refreshes about every five seconds.

Carry On 2.1.2.7 respects claim pickup and placement restrictions. Claim lecterns can't be carried. Items being carried at death near protected land are kept instead of placed into the claim.

Both mods are optional. Other automation mods can bypass Forge's protection events. Fluid and fire spread, existing lingering potion clouds, and interactions with other mods still need checking in your modpack.

## Building

Run `./gradlew build`, or `gradlew.bat build` on Windows. The JAR goes in `build/libs/`. Use `runClient` or `runServer` to start a development instance. Add `-Pgecko` to include GeckoLib.

Source is in `src/main/java/dev/quickclaim/`. `ClaimData` holds saved state; `ClaimService`, `ClaimCommands`, and `Management` handle player actions; `ProtectionEvents` checks permissions. `ClaimNavigation` handles home pings. Atlas data comes from `AtlasSync`, with drawing in `client/AtlasOverlay` and its hook in `mixin/AtlasWidgetMixin`.

Menus are in `client/`, and area and payment calculations are in `core/`. Recipes, models, language entries, and block tags are in `src/main/resources/`.
