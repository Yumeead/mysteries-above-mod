---
name: in-game-verification-with-two-clients
description: "The user verifies abilities by actually playing them on two dev clients, reports precise symptoms, and prefers deferring what the dev environment cannot show over building workarounds"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: e9502c28-4c5b-41d9-90ce-ba899412a4a0
  modified: 2026-09-06T11:38:59.355Z
---

Abilities get verified by the user **playing them**, not by a green build — and for anything that
touches another player, on two clients at once (`./gradlew :mod:runServer`, `:mod:runClient`,
`:mod:runClient2`). The user reads symptoms precisely and reports them separately ("tab name
changed, chat name did not, skin did not"), which is what makes narrowing possible; give them a
checklist per ability with the exact commands and what to look for, and name the two or three
things most likely to break so they know where to look.

When the dev environment itself cannot show a result, the user's call is to **defer the check**,
not to build a workaround for the test rig. That happened with `Shapeshifting`'s skin: the dev
server is `online-mode=false`, offline profiles carry no textures, and rather than fabricate a
texture blob the user said they would retest later with real skins — see
[[shapeshifting-skin-open-question]].

Two practical notes worth remembering: the first dev client's username is **random per launch**
(`Player###`), so never write a checklist assuming a fixed name — read it from the server log or
tell the user to substitute; and a leftover dev server from a previous session holds the world
lock and the port, which has blocked a startup before, so stop it when finishing.

**Why:** the project's own rule is that a client-side change is not done until `runClient` has
raised the game, and two phases were once closed "green" without ever launching.

**How to apply:** after writing an ability that others can see, raise the game and hand over a
concrete checklist before calling it done; when the environment is the blocker, say so plainly and
offer to defer rather than contorting the setup.
