# RuneLite review brief

Use this note for pre-clearance in the RuneLite development Discord and for the
eventual Plugin Hub pull request. Do not submit until `PLUGIN_HUB_CHECKLIST.md`
passes.

## Pre-clearance message

> I maintain Group TCG, a solo/private-group multiplayer companion to OSRS
> TCG. The overlap with Bronzeman TCG is card-based honour-mode interaction
> locking. Group TCG adds configurable solo or server-shared collections,
> individual contribution views, pack popups, and consent-based Top Trumps.
> Individual views can be opened as read-only OSRS TCG-style collection albums;
> they use the already-synced snapshot and do not make a separate player-library
> request.
> It does not require GIM or RuneLite Party. Multiplayer is an explicit opt-in
> to a user-supplied, privately hosted Cloudflare Worker; no public server URL
> is bundled. The Worker stores the RuneScape display name needed for owner
> approval and right-click matching, plus TCG data, but receives no Jagex
> credentials/session, account ID, inventory, bank, location, stats, clan, or
> chat data. Would this scope be acceptable as a separate Plugin Hub entry?

## Reviewer-facing boundaries

- Java 11 and Plugin Hub `standard` build with no custom runtime dependency.
- Reads profile-scoped OSRS TCG state and does not alter the OSRS TCG plugin.
- Restrictions are self-imposed menu-click consumption with documented gaps.
- Multiplayer requires an explicit HTTPS server URL and opt-in. HTTP is
  accepted only for loopback development.
- The private API receives RuneScape display names, random identifiers,
  selected collection mode, hashed Group TCG credentials, card names,
  foil/debug state, timestamps, pack events, and Top Trumps events.
- It never receives Jagex credentials/session data, account IDs, bank PINs,
  inventory, bank, equipment, location, world, stats, XP, clan data, or chat.
- Raw OSRS TCG instance IDs are replaced with opaque SHA-256-derived IDs.
- Each self-hosted Worker is claimable once. First creation requires a private
  encrypted setup key which the plugin sends once and never saves.
- Optional Wiki artwork is off by default and limited to fixed OSRS Wiki URLs.
- Card faces use attributed BSD-licensed OSRS TCG code/assets.
- The current rejected-feature guidance mentions plugins that expose player
  information over HTTP. Group TCG uses HTTPS, sends only the opting-in member's
  own display name to their user-supplied private Worker, and should be
  pre-cleared with reviewers against that rule before the submission PR.

## Draft Plugin Hub description

Group TCG is an opt-in Bronzeman-style challenge driven by cards in OSRS TCG.
Choose a personal collection or permanently share unlocks with approved
members of a private server. Browse individual contributions in full read-only
collection albums, see recent pulls and queued pack reveals, and challenge
server friends to consent-based Top Trumps from the sidebar or in-game
right-click menu. Locked NPCs and items receive clear visual treatment and
blocked actions show the required card.

The optional multiplayer Worker is self-hosted and has no bundled public URL.
Its documented privacy boundary is in `PRIVACY.md`.
