---
name: justiciar-effects-design
description: "Justiciar effects rework — design artifact URL, the pathway's visual grammar, the new engine pieces it proposes, and the 7 mechanic decisions still awaiting the user"
metadata:
  node_type: memory
  type: reference
  originSessionId: fc1debbe-0ac4-4f32-954f-8a1a4dee0700
  modified: 2026-11-16T10:46:48.563Z
---

Design for moving all 13 Justiciar abilities (Seq. 9–5) onto the phenomena engine, written 16.11.2026 on request, as a
sibling of the "Supernatural Rendering Engine" artifact (BS6LZBxMymFbKNCbRMpUWH):
https://claude.ai/artifact/3MmcvvuFaZxwwZzWxdRpUv

**Status on 16.11.2026: proposed, not yet reviewed by the user.** Verify before treating any of it as decided.
Step 0 of its §8 (the probe) is done, on the user's request "only step 0": vanilla iron bars — yes, both as `block_display`
and through `submitBlockModel`, under Iris + Complementary too; `textBackground()` cold iron works under the pack.
Findings in `.claude/rules/world-effects.md` («Залізо Justiciar: проба»), scene in `JusticiarProbeClientGameTest`.
Step 1 done 16.11.2026 (uncommitted): `Edict` (SEAL, BONDS, CROWN only; STROKE/FRONT/DENIAL/PLAQUE wait for steps 2–3), `Lash`
(WHIP, NEEDLE, BOLT), `effect/Eyes` (EyeGlow SUN/GOLD/WHITE replaces `SUN_EYES`), sounds GAVEL/RATCHET/CLANG; Authority, Gaze,
Whip, Piercing, Brand, Lashing rewritten with no particles/text. Applied from §6 without asking: #2 (Authority targets at the
blow), #3 (gaze lowers eyes 40°), #6 (Lashing hops every 3 ticks). Not done in step 1: Brand's transient attribute modifiers
(still Slowness 255 + `rootInPlace`). Scene: `JusticiarClientGameTest` (`-Ponly=justiciarclient`); the 4-tick needle and bolt
are gone before a screenshot, so the test injects them straight into `Lashes`; needle still not caught on a frame (edge-on or
hidden behind the caster). Steps 2–5 not started.

**16.11.2026 evening: user rejected step 1 visuals as "terrible"** (hairlines, no texture, no animation; bracelet = open
square, needle = two static stripes, whip not whip-like). Root cause: my own grammar below (no easing/fade, axis-aligned,
1-px `pixelLine`, whole-tick phases) and judging screenshots by "readable" not "good". Rework plan (chat, not yet built):
textured ribbons along curves + easing + fade + additive halo, partialTick ages, `BbModel` props, frame-strip screenshots
shown to user per effect before the next. User decisions: Whip of Pain = Terraria-style segmented whip (first to build);
Psychic Lashing per lore = illusory lightning coating the held weapon; Authority = NO item, a Nen-like (Hunter x Hunter)
aura visible to others but laconic/pixel enough for Minecraft; Brand = chains spiralling onto the victim and tightening;
Piercing = jagged bolts from both eyes + spirit-body ghost knocked out. Textures: try SpriteCook (free tier, 28 credits left;
`pixel-engine-image-v1` = 6 credits, exact size, transparent). Finding: it ignores sheet layout (parts come out diagonal,
cells not respected) — generate one part per image, or use it only for style.

Rework progress (uncommitted, all verified in the client test with frame series): Whip (`Whips`, Terraria-style,
grip anchored to the on-screen fist), Brand (`Bonds`, chains from the ground, red-hot texture cells), Piercing (bolts
from both eyes + spirit ghost), Lashing (mechanic changed per lore: weapon charge 10 s, arcs on the held item via two
mixins), Authority (17.11: `Auras`, a separate flame envelope round the body rising above the head, per the user's anime reference; the model-shell layer was rejected). Details and traps
in `.claude/rules/world-effects.md`. Seq. 6–5 done 17–18.11 (uncommitted, verified by
client tests): Verdict (5 lore sentences on wheel modes; Confinement = rune dome after user said "just a box"; Flog =
curved cooling welts after "strips look bad"), Prohibition (one ability, 4 laws, rune belt + edge veil + crossed-out
sign, `Prohibitions` registry, executor refuses teleporting powers), Punishment (active mark: dawn burst, Paladin looms
in the target's eyes via `PaladinLoomMixin`, shackles, x1.5 blows; Prohibition breakers auto-marked). User's standing
rule since 17.11: nothing appears instantly. Left: Jurisdiction (Seq. 8), Recognition, Intuition, Gaze polish.

User decisions 17.11.2026 for Seq. 6–5 (all as I recommended): Verdict picks its sentence by **wheel modes**, not the
item in hand; the set is the lore's: Exile, Imprison, Confinement, Death (dash with afterimages + blow), Flog (invisible
whip tears flesh); Evaporation (trident) dropped. **One** Prohibition with modes Flight, Teleportation, Invisibility,
Undead (expel + keep out a creature kind); kill/weapon bans and the separate Spawn Prohibition dropped. **Punishment
becomes active**: mark a target (dawn glow on the Paladin, the target alone sees him grow taller, invisible shackles on
its feet, stronger blows against it); a violator of one's Prohibition is marked automatically; the offhand item gone.

The grammar below is SUPERSEDED by the above:
- Grammar: everything axis-aligned to the world grid; "stamp and ratchet" motion (no easing, no alpha fade); colour is
  state (gold = law pronounced, iron = law stands, red = law broken). No shader of its own.
- New pieces: `WorldEffectPayload.Edict` (SEAL, BONDS, STROKE, FRONT, DENIAL), `Lash` (WHIP, NEEDLE, BOLT), `Precinct`
  (POSTS, BARS, CAGE: boundary bars that rise in steps as the viewer approaches), `Edict.PLAQUE` (iron plaque with a
  vanilla item icon behind three bars: the law of Prohibition/Spawn Prohibition is shown, not written), first use of `block_display`
  (`iron_bars` cell for Imprisonment), `sun_eyes` attachment generalised to an eye variant.
- World text only in two places (user agreed 16.11 after review against [[immersive-abilities-direction]]): the
  Verdict word (lore exception) and Whip of Pain interrogation answers (knowledge, caster only). "КАРА" and law
  sentences were cut. v1 of the artifact claimed "only the verdict word" while its body had four more text sources.
- Seven mechanic changes listed in its §6 as awaiting the user: square zones, Authority targets at landing, Gaze lowers
  the eyes instead of a 100° turn, Jurisdiction boundary private, Recognition outline private, Psychic Lashing hops in
  sequence, Verdict waits 8 ticks after the word.

Related: [[immersive-abilities-direction]], [[ability-visuals-pixel-and-on-theme]], [[ability-visuals-must-not-clutter]],
[[door-slice]], [[player-text-no-dots-arrows-dashes]].
