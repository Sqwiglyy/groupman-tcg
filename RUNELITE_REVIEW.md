# RuneLite review brief

Use this note for pre-clearance in the RuneLite development Discord and as the
basis of the eventual Plugin Hub pull request. Do not submit until the manual
release checks in `PLUGIN_HUB_CHECKLIST.md` pass.

## Pre-clearance message

> I maintain Groupman TCG, a group-first multiplayer companion to OSRS TCG.
> I know Bronzeman TCG is already listed and want to avoid Plugin Hub
> fragmentation. The overlap is card-based interaction locking. The distinct
> purpose is official GIM/HCGIM multiplayer: a grow-only shared collection,
> individual member collection views, live RuneLite Party synchronization,
> group pack popups, consent-based Top Trumps, and optional identity-free
> offline synchronization through a self-hosted one-group Worker. Hosted sync
> and Wiki artwork are disabled by default, the server URL is blank, and no
> RuneScape/GIM name or original-puller name is sent to the Worker. Would this
> group-specific scope be acceptable as a separate Plugin Hub entry, or should
> I approach the Bronzeman TCG maintainer about contributing the multiplayer
> features there instead?

## Reviewer-facing boundaries

- Java 11 and Plugin Hub `standard` build with no custom runtime dependency.
- Reads the local OSRS TCG collection; it does not alter OSRS TCG.
- Restrictions are self-imposed menu-click consumption. Examine, Drop, Destroy,
  bank deposits, and LMS safety behavior are documented and configurable.
- RuneLite Party provides live group transport only between the user's current
  party members.
- Hosted sync is an explicit opt-in and accepts HTTPS Worker URLs; localhost is
  accepted only for development.
- The hosted API receives opaque identifiers, generated private labels, hashed
  credentials, card names, foil/debug state, timestamps, and pack contents. It
  does not receive RuneScape names, GIM names, raw OSRS TCG instance IDs, bank
  contents, inventory contents, chat, location, gear, stats, or credentials.
- Each self-hosted Worker is claimable by one group and limited to five active
  members.
- Optional Wiki artwork loading is off by default and limited to the fixed OSRS
  Wiki image origin.

## Draft Plugin Hub description

Groupman TCG is an opt-in multiplayer Bronzeman challenge driven by cards in
the locally installed OSRS TCG plugin. Official GIM/HCGIM members permanently
combine unlocks, can inspect individual contributions, see queued group pack
reveals, and play consent-based Top Trumps. Card-locked NPCs and items receive
clear visual treatment and prohibited interactions are consumed with an
explanatory chat message.

Live multiplayer uses RuneLite Party. Optional offline synchronization uses a
group-owned Cloudflare Worker whose URL is blank by default. The protocol uses
opaque identifiers and generated member labels; it does not upload RuneScape
or GIM names. Optional Wiki card artwork is also disabled by default. Full data
flows are documented in `PRIVACY.md`.
