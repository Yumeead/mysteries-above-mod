---
name: shapeshifting-skin-open-question
description: "Resolved 14.11.2026: why a disguise's skin never changed (player-info profile id is not on the wire) and how it is drawn now"
metadata:
  node_type: memory
  type: project
  originSessionId: e9502c28-4c5b-41d9-90ce-ba899412a4a0
  modified: 2026-11-14T22:40:00.000Z
---

Resolved 14.11.2026. The disguise skin never changed because a `ClientboundPlayerInfoUpdatePacket`
`ADD_PLAYER` entry carries the profile as **name + properties only**; the client rebuilds it under the
entry's own id (the wearer's). With no textures (offline dev server) the default skin was picked by
the wearer's id, so the borrowed face's id never reached the client.

Fix: `WORN_FACE` (`WornFacePayload(wearer, face)`) goes to every mod client; `PuppetSkins.masks` +
`AbstractClientPlayerSkinMixin` draw the face on others and on oneself. Vanilla clients still see
only the name (and a textured skin, where the server has one).

**Why:** the old note sent the next attempt to an online-mode server; that is no longer needed.
**How to apply:** to test anything another player sees, make a second `ServerPlayer` on the server
(`placeNewPlayer` + `EmbeddedChannel`, see `FacesClientGameTest`) — one client is enough.
Related: [[in-game-verification-with-two-clients]].
