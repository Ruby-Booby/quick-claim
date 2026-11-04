# Quick Claim

Mark out land with a wand, then use a claim lectern to manage it. Quick Claim runs on Minecraft 1.20.1 with Forge 47.4.0 or later in the 47.x series and Java 17.

Get the JAR from this repository's Releases page. Put the same version in the server's and each player's `mods` folder. Remove older Quick Claim JARs first.

## Claiming land

Use the Claim Wand on one corner, then sneak-use it on the other. Both corners set the height as well as the width and length. Right-click an Amethyst Claim Lectern to open the menu and claim the selection.

The Claims tab lets you rename a claim, change its permissions, or add, cut out, and replace areas with another wand selection. Set a main claim there to use the home ping. The default keys are `P` for your personal home and `O` for your faction's home; both can be changed in Controls.

Personal claims start with an allowance of 2,560 horizontal blocks. You can trust four other players to use blocks and open your vault. Trust alone doesn't let them build, break blocks, or manage the claim.

## Factions and payment

A player can belong to one faction and still keep personal claims. Factions have no member limit. The owner sends invitations, changes the title and its hex color, removes members, and appoints up to four managers.

Members can build on faction land. The owner and managers can also edit claims, change permissions, and open the faction vault. That vault holds the items used for upkeep.

The defaults are:

- 200 diamonds to create a faction, then a payment every 30 real days.
- An extra 50 diamonds per payment at 10 members, then another 50 for each five members after that.
- 2,560 horizontal blocks of claim space, plus 256 for every member after the owner. Pending invitations add no space.

Leave payment items in the faction vault. If a payment is missed, protection and expansion stay available for seven real days. After that, protection stops and the faction can't expand until its debt is paid. Its saved claims are kept. Offline time counts toward these dates.

The lectern must be inside the faction's claim before it can open the vault. If that lectern is removed, the stored items remain available through a replacement lectern on the faction's land. Personal vaults have no upkeep.

## Admin controls

OP level 2 unlocks the Admin tab. It has server-owned claims, a bypass toggle, PvP overrides, area allowances, and payment settings. Server claims don't need upkeep.

To change the currency, click an item in your inventory in the payment editor. Drag the sliders or type the amount and interval. The limits are 1-10,000 items and 1-365 real days. Already accrued charges keep their price.

Claim permissions cover visitors' block use, placement, breaking, PvP, hostile spawning, and mob griefing. Passive mobs still spawn. Admin PvP overrides take priority. The bypass resets on logout. Command shortcuts are available under `/claim` and `/faction`.

## Recipes

For the wand, put an amethyst shard in the top-right slot and a stick in the bottom-left slot of a 2x2 crafting grid.

The lectern recipe uses these three rows:

```text
A B A
. L .
G C G
```

`A` is an amethyst shard, `B` a book, `L` a lectern, `G` a gold ingot, and `C` a chest. Leave the dots empty.

## Other mods

Map Atlases 1.20-6.0.20 can show claim borders and home markers on its world map. It doesn't add those overlays to the minimap. Personal home markers belong to their owner; faction home markers are shared with members. Alignment across all Atlas settings still needs in-world testing.

Carry On 2.1.2.7 respects the claim's pickup and placement restrictions. The claim lectern itself can't be carried. Items carried at death near protected land are retained instead of being placed into the claim.

Neither mod is required. Other automation mods may bypass Forge's protection events. General fluid and fire spread, existing lingering potion clouds, and those bypasses still need testing in the intended modpack.

## Building and reading the code

Run `./gradlew build`, or `gradlew.bat build` on Windows. The mod JAR is written to `build/libs/`. `runClient` and `runServer` start development instances.

The Java code is in `src/main/java/dev/quickclaim/`:

- `ClaimData` stores claims, factions, vaults, and payment state.
- `ClaimService`, `ClaimCommands`, and `Management` handle player actions and access checks.
- `ProtectionEvents` enforces claim rules through Forge events.
- `Network` sends menu actions and claim data between the server and client.
- `client/` contains the menus, pings, and Atlas overlay. `core/` contains area and billing calculations.

Recipes, models, language entries, and block tags are in `src/main/resources/`.
