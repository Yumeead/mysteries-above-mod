---
name: shapeshifting-skin-open-question
description: "Fool's Shapeshifting disguise works for name/tab/chat but the skin never visibly changes on the offline-mode dev server; what has already been ruled out"
metadata: 
  node_type: memory
  type: project
  originSessionId: e9502c28-4c5b-41d9-90ce-ba899412a4a0
  modified: 2026-09-06T11:38:46.487Z
---

`Shapeshifting` (Fool, Seq. 6) borrows another player's identity by faking the player-info packet.
Verified in game with two clients: **tab row, name above the head and chat all take the borrowed
name.** The **skin does not visibly change**, and the user chose to defer it until it can be
retested against **real skins on an online-mode server** rather than dig further.

Already ruled out, so do not re-derive:

- The server sends the right thing. One INFO line per disguise is deliberately left in
  `Disguise.show` and printed: `Disguise: Tester2 shown as Player618 (profile id 9558103d-…,
  textures absent) to 1 viewer(s)` — correct borrowed profile id, correct viewer count.
- Textures are absent **because the dev server runs `online-mode=false`**, and offline profiles
  carry no `textures` property at all. There is no skin in that world to copy.
- With no textures the client can only fall back to the UUID-keyed default skin, which is why the
  fabricated profile carries the **target's** UUID (the entry's own `profileId` stays the wearer's,
  which is what binds it to the body).
- The two test accounts' offline UUIDs map to **different** default skins, so a change should have
  been visible — the "both look the same anyway" explanation was checked and does not hold.
- The client-side entity is genuinely rebuilt: the name above the head changed, which it could not
  have done from a stale entity.

So the remaining suspect is the client's skin lookup path (`PlayerInfo.createSkinLookup` →
`SkinManager.createLookup`), not the server, not the packet, and not the entity respawn.

**Why:** this cost most of a session to narrow down, and the next attempt should start from the
client side with real textures rather than repeat the server-side elimination.

**How to apply:** when a server with real skins is available, retest first; the INFO line can be
removed once the skin half is confirmed either way.
