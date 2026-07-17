# Group TCG

[![Build](https://github.com/Sqwiglyy/groupman-tcg/actions/workflows/build.yml/badge.svg)](https://github.com/Sqwiglyy/groupman-tcg/actions/workflows/build.yml)
[![License: BSD 2-Clause](https://img.shields.io/badge/license-BSD%202--Clause-blue.svg)](LICENSE)
[![Fork on GitHub](https://img.shields.io/badge/fork-on%20GitHub-2ea44f.svg)](https://github.com/Sqwiglyy/groupman-tcg/fork)

Turn your [OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) pulls into a
Bronzeman-style RuneLite challenge. Cards unlock the NPCs, items, gathering
nodes, recipes, and skill interactions they represent.

You can play alone or share permanent unlocks with friends. Group TCG works on
any RuneScape account; it does not require a Group Ironman team, clan, or
RuneLite Party.

> Group TCG is a release candidate ready for RuneLite Plugin Hub submission.
> Automated checks and the full two-account launch checklist have passed.

## Install or run your own fork

When Group TCG is listed on the RuneLite Plugin Hub, install it from RuneLite's
**Plugin Hub** panel and enable **OSRS TCG** alongside it.

To try the current release from your own fork:

1. Click **Fork on GitHub** above, then clone your copy:

   ```text
   git clone https://github.com/YOUR-GITHUB-NAME/groupman-tcg.git
   cd groupman-tcg
   ```

2. Install Eclipse Temurin Java 11 and set `JAVA_HOME` to that JDK.
3. Open the cloned folder and launch the RuneLite development client:

   ```powershell
   .\gradlew.bat run
   ```

   On macOS or Linux, use `./gradlew run` instead.

4. Install or enable **OSRS TCG** in that development client, then enable
   **Group TCG**.

No Cloudflare account is needed for solo play. Multiplayer setup is optional
and lives in the separate server repository linked below.

## What it does

- Choose a personal collection or a shared group collection for your unlocks.
- See the 20 latest cards pulled by the selected player, including duplicates
  and foils.
- Open any group member's collection in the full OSRS TCG-style album.
- Share delayed pack-opening popups with friends, including missed openings
  after reconnecting.
- Challenge approved members to consent-based Top Trumps from the sidebar or
  their in-game right-click menu.
- Mark locked NPCs and items clearly, then explain which card is missing when
  a supported action is blocked.

The collection album uses the snapshot the plugin has already synced. Browsing
another member does not trigger a fresh library download. Optional card artwork
is loaded separately from fixed OSRS Wiki URLs when enabled.

## Start playing solo

1. Install and enable **OSRS TCG**.
2. Enable **Group TCG** and choose **Solo collection**.
3. Open packs in OSRS TCG as normal.
4. Review the restriction settings before committing to the challenge.

That is all a solo player needs. Server features stay off until you explicitly
enable **Connect to server**.

## Play with friends

Group TCG does not include a public multiplayer service. One person in the
group hosts a small private Cloudflare Worker and D1 database; everyone else
connects to that same URL.

1. The host follows the
   [Group TCG Server setup guide](https://github.com/Sqwiglyy/groupman-tcg-server)
   and configures its private `SETUP_KEY`.
2. The host enables **Connect to server**, enters the Worker URL, then creates
   a group from the Group TCG sidebar.
3. Friends enter the same URL and join with the private group ID and invite.
4. The host checks each displayed RuneScape name before approving it.
5. Each member independently chooses **Shared server collection** or
   **Solo collection**.

A private Worker supports up to 50 active memberships. Invites last 30 days and
can be rotated. Revoking someone removes their access, while cards already
added to the shared unlock pool remain unlocked.

## Privacy, in plain English

Both network features are off by default:

- **Connect to server** sends the active character's display name, Group TCG
  credentials, collection mode, card/copy data, pack events, and Top Trumps
  events to the private Worker chosen by the player.
- **Download card artwork** requests images from the OSRS Wiki. The Wiki sees
  the connecting IP address and image URL, but no RuneScape display name or
  Group TCG credential.

The plugin never sends Jagex login details, session tokens, account IDs, bank
PINs, inventory, bank, equipment, stats, XP, world, location, clan data, or chat
messages to the Worker. Raw OSRS TCG instance IDs are replaced with opaque
hashes before upload.

The person who owns the private Cloudflare account can inspect its database and
backups, so only join a server run by someone you trust. The complete field-by-
field boundary is in [PRIVACY.md](PRIVACY.md), and private security reports are
covered by [SECURITY.md](SECURITY.md).

## Restriction coverage

Group TCG can enforce card requirements for:

- combat and configured NPC interactions;
- taking, buying, withdrawing, equipping, consuming, and using tracked items;
- Woodcutting, Mining, Fishing, and Runecrafting;
- Cooking, Firemaking, Smelting, Smithing, Crafting, Enchanting, Fletching,
  and Herblore;
- Farming, Hunter, Slayer, Thieving, and Sailing.

Items and NPCs without an OSRS TCG card are left alone. Coins are exempt by
default, and restrictions pause during live Last Man Standing matches unless
that safety setting is disabled.

This is still an honour-mode plugin. It can consume supported RuneLite menu
clicks and draw lock indicators, but it cannot change the game server or
guarantee that every unusual interaction path is covered.

## Troubleshooting

- **No cards appear:** enable OSRS TCG, open its collection once, and make sure
  the character has an active RuneLite profile.
- **Shared mode only sees local cards:** check **Connect to server**, the Worker
  URL, owner approval, and the sync status in the sidebar.
- **A member stays pending:** the host must verify and approve the displayed
  RuneScape name.
- **Top Trumps is missing:** both players must be approved on the same server
  and have **Enable Top Trumps** switched on. The sidebar challenge works even
  when the other player is not nearby.
- **Artwork is blank:** artwork downloads are optional and disabled by default.
- **The plugin switches off in a development client:** restart the client after
  rebuilding and inspect `%USERPROFILE%\.runelite\logs\client.log` for the first
  Group TCG exception.

## Building from source

Group TCG uses Java 11 and the RuneLite Plugin Hub standard build:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
.\gradlew.bat clean build
```

The Cloudflare backend lives in the separate
[groupman-tcg-server](https://github.com/Sqwiglyy/groupman-tcg-server)
repository and is not part of the RuneLite plugin artifact.

Project references:

- [Changelog](CHANGELOG.md)
- [Privacy details](PRIVACY.md)
- [Security reporting](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

Maintained by [Sqwiglyy](https://github.com/Sqwiglyy).
