# Groupman TCG

Groupman TCG is a RuneLite challenge plugin powered by the
[OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) collection. Tracked
NPCs, items, gathering nodes, recipes, and skill interactions remain locked
until the matching card is collected. Official Group Ironman and Hardcore
Group Ironman teams can combine their cards into one permanent shared unlock
collection.

This is an honour-mode plugin. It blocks supported RuneLite menu clicks and
clearly marks locked content, but it does not alter the game server or claim to
enforce every possible interaction.

## What you need

- The official desktop RuneLite client.
- **OSRS TCG** installed and enabled from the Plugin Hub.
- **Groupman TCG** installed and enabled.
- An official GIM/HCGIM account for shared-group mode. Normal accounts can use
  solo mode.
- A shared RuneLite Party for live collection updates, pack popups, and Top
  Trumps.
- Optionally, one private self-hosted Cloudflare Worker for offline history and
  convergence. There is no public default server.

## Start a solo challenge

1. Enable **OSRS TCG** and **Groupman TCG**.
2. Set **Collection mode** to **Solo collection**.
3. Open packs in OSRS TCG. Newly collected cards immediately unlock their
   matching content.
4. Review the restriction difficulty options before committing to the account.

Solo mode does not use RuneLite Party or the hosted backend.

## Start a new multiplayer group

### Live-only setup

This is the shortest setup and requires no Cloudflare account:

1. Every teammate installs and enables OSRS TCG and Groupman TCG.
2. Every teammate sets **Collection mode** to **Shared GIM collection**.
3. Log into the official GIM/HCGIM characters so RuneLite can read the local
   in-game roster.
4. Join the same RuneLite Party.
5. Leave **Hosted offline sync** disabled.

Collection snapshots and pack reveals then travel through RuneLite Party.
Players who are never online together will not exchange missed history until a
hosted backend is added.

### Private hosted setup

Use this setup when the group wants offline pack history and durable per-member
collections:

1. One teammate deploys the
   [Groupman TCG Server](https://github.com/Sqwiglyy/groupman-tcg-server) to
   their own Cloudflare account.
2. Open `https://YOUR-WORKER.workers.dev/health` and confirm it returns
   `{"status":"ok"}`. Copy the root URL without `/health`.
3. Every teammate enables **Hosted offline sync** and enters that identical
   root URL under **Hosted server URL**.
4. The owner logs into their GIM/HCGIM character, opens the Groupman TCG
   sidebar, and selects **Create hosted group**.
5. The owner sends the displayed group ID and invite code privately to each
   teammate. Do not share bearer tokens or post join details publicly.
6. Each teammate selects **Join hosted group** and enters the group ID and
   invite code. The server assigns a private label such as `Member A1B2C3`;
   their RuneScape name is never uploaded.
7. The teammate tells the owner that private label through an existing trusted
   channel. The owner approves only the expected label.
8. Repeat for the remaining teammates. A private Worker supports one group of
   up to five active memberships.
9. Join the same RuneLite Party whenever instant reveals or Top Trumps are
   wanted. Hosted offline sync continues without a Party.

Invites last 30 days and can be rotated by the owner. A revoked member loses
API access; permanent grow-only unlocks remain in the group collection.

## Privacy and network behaviour

Privacy-sensitive features are off by default: **Hosted offline sync** and
**Download card artwork** both require an explicit opt-in.

The plugin reads the active character name, official GIM name and roster, OSRS
TCG collection state, and relevant visible interactions locally. Those values
are needed to bind local profile data and apply the challenge, but are never
sent to the self-hosted Worker.

When RuneLite Party is used, Groupman TCG sends an opaque hash in place of the
GIM name, compact card collection data, pack card names/foil/new flags, and
Top Trumps challenge/card data. It does not add RuneScape names or the GIM name
to its Party message payloads. RuneLite Party itself already exposes each
party member's display name to the other members of that Party.

When hosted sync is explicitly enabled, the selected Worker receives only:

- random group and member IDs plus generic labels such as `Owner` and
  `Member A1B2C3`;
- Groupman TCG bearer tokens and invite codes over HTTPS for authentication;
- opaque hashes of OSRS TCG source-instance IDs;
- card names, foil/debug flags, pull timestamps, shared unlocks, and pack
  contents required for multiplayer history.

The Worker is never sent a RuneScape name, GIM name or roster, account ID,
Jagex credential, bank PIN, game-session token, stats or XP, inventory, bank or
equipment contents, world, location, clan data, or chat. The bearer token is a
Groupman TCG credential stored in the local RuneLite profile and is always
bound to the Worker that issued it. The Worker stores only SHA-256 hashes of
bearer tokens and invite codes in D1.

Cloudflare necessarily processes connection metadata such as IP addresses when
a client connects. The Worker code does not deliberately log or store IP
addresses, request bodies, credentials, or gameplay data. The self-hosting
Cloudflare account owner remains responsible for their account settings,
backups, retention, and privacy obligations.

The complete field-by-field boundary is recorded in the
[privacy contract](PRIVACY.md).

Optional card artwork is downloaded only after **Download card artwork** is
enabled. Requests go only to fixed OSRS Wiki image URLs; the Wiki can then see
the connecting IP address and requested image URL. No RuneScape identity is
included in those requests. Card frames and names still work when artwork is
disabled or unavailable.

## Restriction coverage

- Combat and other configured interactions with locked tracked NPCs.
- Taking, telegrabbing, equipping, buying, withdrawing, consuming, and using
  locked tracked items.
- Grey outlines for locked NPCs and ground items, plus shaded inventory, bank,
  and equipment items with optional padlock markers.
- Woodcutting, Mining, Fishing, and Runecrafting gathering requirements.
- Cooking, Firemaking, Smelting, Smithing, Crafting, Enchanting, Fletching, and
  Herblore input/output modes.
- Farming raking, planting, produce, and compost requirements.
- Hunter birds, butterflies, implings, salamanders, pitfalls, chinchompas, and
  optional rumour-master assignment pools.
- Slayer masters, assignment pools, and optional superior variants.
- Thieving pickpockets, Master Farmers, and market stalls.
- Sailing ship upgrades and shipwreck salvage.

Items and NPCs without an OSRS TCG card are not restricted. Coins are exempt
from the general item lock by default, and restrictions suspend in live Last
Man Standing matches unless that safety option is disabled.

## Multiplayer features

- Grow-only shared unlock collection with one personal collection view per
  member.
- Queued miniature pack windows showing another member's pulls, duplicate,
  `NEW`, and `FOIL` state.
- Offline pack replay after reconnecting when hosted sync is enabled.
- Consent-based Top Trumps: right-click an online verified group member,
  challenge them, and draw two different cards from the shared collection after
  they accept.
- Local provenance for the current character. The hosted service deliberately
  omits original-puller names; remote history shows private member labels,
  copies, foil/debug state, and dates only.

## Updating, backup, and recovery

- RuneLite updates Plugin Hub plugins automatically after an approved Plugin
  Hub update.
- The self-hosting teammate updates and backs up the Worker separately. Follow
  the server repository's
  [update and backup guide](https://github.com/Sqwiglyy/groupman-tcg-server#updates-and-backups).
- Disconnecting removes the local hosted credential but leaves the cached
  grow-only unlock union.
- Changing **Hosted server URL** does not move an existing group. Disconnect,
  select the new Worker, and create or join on that deployment.

## Troubleshooting

- **No cards appear:** enable OSRS TCG, open its collection once, and confirm
  the character has a RuneLite profile.
- **Shared mode says to log in:** shared mode requires an official GIM/HCGIM
  account and a readable in-game group roster.
- **Live updates are missing:** all members must be in the same RuneLite Party
  and use compatible plugin/card-catalog versions.
- **Hosted setup fails:** enable hosted sync, use the exact same HTTPS Worker
  root URL on every client, and confirm `/health` returns `ok`.
- **A member remains pending:** the owner must confirm the member's private
  label out of band and approve it in the sidebar.
- **The Worker says it is claimed:** each deployment intentionally supports one
  private group. Deploy a separate Worker for another group.
- **Artwork is blank:** artwork downloads are disabled by default. Enable them
  only if the OSRS Wiki network request is acceptable.

## Development and Plugin Hub readiness

Requires Java 11. Run all checks with:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
.\gradlew.bat clean build
```

The repository uses the Plugin Hub's `standard` build, Java-only source,
`latest.release` RuneLite dependencies, a BSD 2-Clause license, and no custom
runtime dependencies. Before submission, complete a real two-account GIM test
covering install, live Party sync, private Worker creation/join/approval,
offline replay, restrictions, shutdown, and restart. Use the
[Plugin Hub launch checklist](PLUGIN_HUB_CHECKLIST.md) for the full manual
release gate.

RuneLite reviews new submissions for security and game-rule compliance. The
maintainer should follow the official
[Plugin Hub submission guide](https://github.com/runelite/plugin-hub#submitting-a-plugin)
and check the current
[rejected/rolled-back feature list](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
immediately before opening the Plugin Hub pull request.

Use [RUNELITE_REVIEW.md](RUNELITE_REVIEW.md) for the prepared pre-clearance
message and reviewer-facing network/privacy summary. Security or privacy
problems should be reported privately as described in [SECURITY.md](SECURITY.md).

Maintained by **Sqwiglyy**.
