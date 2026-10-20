// tyrant_siren_5 — "Морська Сирена" (Tyrant pathway, Sequence 5 "Ocean Songster").
// Humanoid drowned-sailor silhouette (matches MythicMobs Type: DROWNED), not a fish-tailed
// mermaid: gaunt reaching arms, kelp for hair, a Tyrant-blue (70,115,199) gill membrane and
// spine ridge. Second pass: dropped the jaw box (read as an unexplained bump near the mouth
// from the default camera angle — its "open when singing" face was visible even at rest) and
// the flat foot plane (added silhouette without adding anything worth the extra element), and
// reworked skin shading from fixed dark bottom-rows to proportional bands so joints (neck,
// wrist) no longer show a harsh dark ring where one part's shadow met the next part's highlight.
//
// BetterModel conventions (these names are load-bearing, not cosmetic):
//   * animations "idle" / "walk" / "death" are BUILT-IN — BetterModel plays them automatically
//     off the base entity's state. "attack" is NOT built-in and is fired from MythicMobs via
//     the state{} mechanic. Renaming any of these silently disables it.
//   * bone tag "hi_" makes that bone AND its children follow the base entity's head rotation,
//     so hi_head lets the siren track whoever it is looking at; the kelp hair rides along.
//     Because look-tracking ADDS to animation rotation, idle deliberately keys no head turn.
//
// All animation numbers below are VISUAL: +X right, +Y up, -Z forward (north).
// BB.Rot / BB.Pos apply Blockbench's axis negation for you.

using System;
using System.Collections.Generic;

public static class Model
{
    // ---- palette ----
    // Decayed drowned skin: cool cyan-gray, hue-shifted (warmer/brighter highlight, cooler/darker
    // shadow). More saturated than a first pass — the washed-out low-S version read as dull gray.
    static readonly int[] SK0 = BB.HSV(198, 40, 10);
    static readonly int[] SK1 = BB.HSV(194, 36, 18);
    static readonly int[] SK2 = BB.HSV(189, 30, 30);
    static readonly int[] SK3 = BB.HSV(183, 24, 44);
    static readonly int[] SK4 = BB.HSV(175, 18, 58);

    // Accent ramp anchored on the Tyrant pathway brand colour (PathwayBranding: RGB 70,115,199).
    static readonly int[] AC1 = BB.HSV(224, 64, 28);
    static readonly int[] AC2 = BB.HSV(220, 62, 46);
    static readonly int[] AC3 = BB.HSV(217, 50, 66);
    static readonly int[] AC4 = BB.HSV(219, 65, 78);

    // Translucent variants of the same blue, for the webbed membranes (gills, foot webs).
    static readonly int[] GA_EDGE = new int[] { 45, 75, 130, 225 };
    static readonly int[] GA_MID = new int[] { 70, 115, 199, 170 };
    static readonly int[] GA_TIP = new int[] { 120, 160, 220, 110 };

    // Kelp (hair + waist strands): dark algae green-black.
    static readonly int[] KP1 = BB.HSV(144, 42, 17);
    static readonly int[] KP2 = BB.HSV(139, 36, 27);
    static readonly int[] KP3 = BB.HSV(132, 26, 40);
    static readonly int[] KP3T = new int[] { KP3[0], KP3[1], KP3[2], 140 };

    // Claws.
    static readonly int[] CL0 = BB.HSV(216, 42, 10);
    static readonly int[] CL1 = BB.HSV(210, 32, 20);

    // Eyes: small, low-contrast pale glints (no dark pupil — a 1px dot reads as one eye, not four).
    static readonly int[] EA = BB.HSV(188, 38, 64);

    static readonly int[] TR = new int[] { 0, 0, 0, 0 };

    // ---- painters ----
    //
    // Front/side use PROPORTIONAL bands (fraction of face height), not fixed pixel rows.
    // A fixed "last row = darkest" band is fine on a 13-tall torso but is half the face on a
    // 4-tall hand — stacked at every joint (neck, wrist) that produced a harsh dark ring where
    // one part's dark bottom row met the next part's bright top row. Front/side now stay in the
    // SK4..SK2 range (no near-black); SK1/SK0 are reserved for the back face and the top/bottom
    // caps, which read as shadow, not as a seam.

    static void SkinFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.25 ? SK4 : (t < 0.6 ? SK3 : SK2);
            for (int u = 0; u < w; u++) put(u, v, c);
        }
    }

    static void SkinSide(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.2 ? SK3 : SK2;
            for (int u = 0; u < w; u++) put(u, v, c);
        }
    }

    static void SkinBack(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.15 ? SK3 : (t < 0.55 ? SK1 : SK0);
            for (int u = 0; u < w; u++) put(u, v, c);
        }
    }

    static void BodyTop(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
                put(u, v, (u == 0 || u == w - 1) ? SK3 : SK4);
    }

    // Spine ridge: a single-pixel Tyrant-blue column down the back, brighter near the shoulders.
    static void BodySpine(BB.Put put, int w, int h)
    {
        int cx = w / 2;
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] baseC = t < 0.15 ? SK3 : (t < 0.55 ? SK1 : SK0);
            int[] ridgeC = t < 0.25 ? AC4 : (t < 0.7 ? AC3 : AC2);
            for (int u = 0; u < w; u++) put(u, v, u == cx ? ridgeC : baseC);
        }
    }

    // Face: two small pale eye-glints (no dark pupil, so they never read as four dots) and a
    // closed-mouth crease near the chin — a flat line, not a separate protruding jaw box.
    static void HeadFront(BB.Put put, int w, int h)
    {
        SkinFront(put, w, h);
        put(1, 2, EA);
        put(w - 2, 2, EA);
        int mouthRow = (int)(h * 0.78);
        for (int u = 1; u < w - 1; u++) put(u, mouthRow, SK1);
    }

    static void HandClaws(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
                put(u, v, (u + v) % 2 == 0 ? CL1 : CL0);
    }

    // Gill membrane: vertical ribs, edges darker/more opaque, faint banding between them.
    static void GillFin(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int[] c;
                if (u == 0 || u == w - 1) c = GA_EDGE;
                else c = (v % 2 == 0) ? GA_MID : GA_TIP;
                put(u, v, c);
            }
    }

    // Hanging kelp ribbon: rooted (dark) near the anchor, lightens toward the tip, frays at the end.
    static void KelpStrand(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int[] c = KP2;
                if (v < 2) c = KP1;
                else if (v >= h - 3) c = KP3;
                put(u, v, c);
            }
        for (int u = 0; u < w; u++)
        {
            if (h >= 2) put(u, h - 2, KP3T);
            put(u, h - 1, TR);
        }
    }

    public static string Build(string outDir, string scratchDir)
    {
        BB.Init(64, 64);

        // ---- geometry ----
        string bodyElem = BB.Box("body", "body", new double[] { -3.5, 12, -2 }, new double[] { 3.5, 25, 2 });
        string headElem = BB.Box("head", "head", new double[] { -3, 25, -3 }, new double[] { 3, 31, 3 });

        // Gills sit 0.1 proud of the torso's actual surface (x=3.5) so the membrane plane
        // doesn't coincide with the torso's own face and z-fight.
        string gillL = BB.Plane("gill_left", "gill", new double[] { 3.6, 21, -2 }, new double[] { 3.6, 25, 1 }, "x");
        string gillR = BB.Cube("gill_right", new double[] { -3.6, 21, -2 }, new double[] { -3.6, 25, 1 },
                               BB.FacesPlane("gill", "x"));

        // Kelp "hair": three strands, offset in z so none sits exactly on the head's back face.
        string kelpC = BB.Plane("kelp_center", "kelpc", new double[] { -0.5, 17, 3.1 }, new double[] { 0.5, 31, 3.1 }, "z");
        string kelpL = BB.Plane("kelp_left", "kelpl", new double[] { -2.3, 20, 2.7 }, new double[] { -1.3, 29, 2.7 }, "z");
        string kelpR = BB.Plane("kelp_right", "kelpr", new double[] { 1.3, 20, 3.3 }, new double[] { 2.3, 29, 3.3 }, "z");

        string armL = BB.Box("arm_left_upper", "arm", new double[] { 3.5, 15, -1 }, new double[] { 5.5, 24, 1 });
        string armR = BB.Cube("arm_right_upper", new double[] { -5.5, 15, -1 }, new double[] { -3.5, 24, 1 }, BB.Faces6("arm"));

        string handL = BB.Box("hand_left_box", "hand", new double[] { 3.5, 11, -1 }, new double[] { 5.5, 15, 1 });
        string handR = BB.Cube("hand_right_box", new double[] { -5.5, 11, -1 }, new double[] { -3.5, 15, 1 }, BB.Faces6("hand"));

        string legL = BB.Box("leg_left_box", "leg", new double[] { 0.5, 0, -1.5 }, new double[] { 3.5, 12, 1.5 });
        string legR = BB.Cube("leg_right_box", new double[] { -3.5, 0, -1.5 }, new double[] { -0.5, 12, 1.5 }, BB.Faces6("leg"));

        string waistL = BB.Plane("waist_left", "waist", new double[] { -1.8, 4, -1.8 }, new double[] { -0.8, 12, -1.8 }, "z");
        string waistR = BB.Cube("waist_right", new double[] { 0.8, 4, -1.8 }, new double[] { 1.8, 12, -1.8 },
                                BB.FacesPlane("waist", "z"));

        // ---- texture ----
        BB.Paint("body_u", BodyTop); BB.Paint("body_d", BB.Flat(SK0));
        BB.Paint("body_n", SkinFront); BB.Paint("body_s", BodySpine);
        BB.Paint("body_e", SkinSide); BB.Paint("body_w", SkinSide);

        BB.Paint("head_u", BodyTop); BB.Paint("head_d", BB.Flat(SK1));
        BB.Paint("head_n", HeadFront); BB.Paint("head_s", SkinBack);
        BB.Paint("head_e", SkinSide); BB.Paint("head_w", SkinSide);

        BB.Paint("arm_u", BB.Flat(SK3)); BB.Paint("arm_d", BB.Flat(SK1));
        BB.Paint("arm_n", SkinFront); BB.Paint("arm_s", SkinBack);
        BB.Paint("arm_e", SkinSide); BB.Paint("arm_w", SkinSide);

        BB.Paint("hand_u", BB.Flat(SK2)); BB.Paint("hand_d", HandClaws);
        BB.Paint("hand_n", SkinFront); BB.Paint("hand_s", SkinBack);
        BB.Paint("hand_e", SkinSide); BB.Paint("hand_w", SkinSide);

        BB.Paint("leg_u", BB.Flat(SK2)); BB.Paint("leg_d", BB.Flat(SK1));
        BB.Paint("leg_n", SkinFront); BB.Paint("leg_s", SkinBack);
        BB.Paint("leg_e", SkinSide); BB.Paint("leg_w", SkinSide);

        BB.Paint("gill_e", GillFin); BB.Paint("gill_w", GillFin);

        BB.Paint("kelpc_n", KelpStrand); BB.Paint("kelpc_s", KelpStrand);
        BB.Paint("kelpl_n", KelpStrand); BB.Paint("kelpl_s", KelpStrand);
        BB.Paint("kelpr_n", KelpStrand); BB.Paint("kelpr_s", KelpStrand);
        BB.Paint("waist_n", KelpStrand); BB.Paint("waist_s", KelpStrand);

        // ---- bones ----
        var outliner = new List<object> {
            BB.Group("body", new double[] { 0, 12, 0 }, BB.Kids(
                bodyElem,
                BB.Group("hi_head", new double[] { 0, 25, 0 }, BB.Kids(
                    headElem, gillL, gillR,
                    BB.Group("kelp_hair", new double[] { 0, 31, 3.1 }, BB.Kids(kelpC, kelpL, kelpR))
                )),
                BB.Group("arm_left", new double[] { 4.5, 24, 0 }, BB.Kids(
                    armL,
                    BB.Group("hand_left", new double[] { 4.5, 15, 0 }, BB.Kids(handL))
                )),
                BB.Group("arm_right", new double[] { -4.5, 24, 0 }, BB.Kids(
                    armR,
                    BB.Group("hand_right", new double[] { -4.5, 15, 0 }, BB.Kids(handR))
                )),
                BB.Group("leg_left", new double[] { 2, 12, 0 }, BB.Kids(legL)),
                BB.Group("leg_right", new double[] { -2, 12, 0 }, BB.Kids(legR)),
                BB.Group("waist_kelp", new double[] { 0, 12, -1.8 }, BB.Kids(waistL, waistR))
            ))
        };

        var anims = new List<object>();

        // ---- idle: treading water. Head snaps-then-holds; limbs and kelp drift on
        // independent periods so nothing reads as mechanically symmetrical. ----
        var idle = BB.Pose();
        idle["body"] = BB.Pos(new double[] { 0, 0, 0, 0 }, new double[] { 3, 0, 0.4, 0 }, new double[] { 6, 0, 0, 0 });
        // No head yaw here on purpose: hi_head already tracks the entity's look direction, and
        // an animated turn would add to it and read as the head drifting off its target.
        idle["kelp_hair"] = BB.Rot(new double[] { 0, 0, 0, -4 }, new double[] { 3, 0, 0, 5 }, new double[] { 6, 0, 0, -4 });
        idle["waist_kelp"] = BB.Rot(new double[] { 0, 0, 0, 3 }, new double[] { 2.6, 0, 0, -5 },
                                    new double[] { 5.2, 0, 0, 3 }, new double[] { 6, 0, 0, 3 });
        idle["arm_left"] = BB.Rot(new double[] { 0, 3, 0, 0 }, new double[] { 2, -4, 0, 0 },
                                  new double[] { 4, 3, 0, 0 }, new double[] { 6, 3, 0, 0 });
        idle["arm_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 2.6, 4, 0, 0 },
                                   new double[] { 5, -3, 0, 0 }, new double[] { 6, -2, 0, 0 });
        idle["hand_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 1.95, -5, 0, 0 },
                                   new double[] { 3.75, 2, 0, 0 }, new double[] { 6, 2, 0, 0 });
        idle["hand_right"] = BB.Rot(new double[] { 0, -1, 0, 0 }, new double[] { 2.75, 5, 0, 0 },
                                    new double[] { 5.15, -2, 0, 0 }, new double[] { 6, -1, 0, 0 });
        idle["leg_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 2.3, -3, 0, 0 },
                                  new double[] { 4.6, 2, 0, 0 }, new double[] { 6, 2, 0, 0 });
        idle["leg_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 2.9, 3, 0, 0 },
                                   new double[] { 5.3, -2, 0, 0 }, new double[] { 6, -2, 0, 0 });
        anims.Add(BB.Anim("idle", "loop", 6, idle));

        // ---- walk: BetterModel's built-in locomotion animation, so it must carry this exact
        // name. The siren is a DROWNED, so locomotion is a swim rather than a stride:
        // alternating flutter kick + breaststroke pull, body undulating once per cycle, kelp
        // trailing backward from the motion. ----
        var walk = BB.Pose();
        walk["body"] = BB.Merge(
            BB.Rot(new double[] { 0, 3, 0, 0 }, new double[] { 0.8, -3, 0, 0 }, new double[] { 1.6, 3, 0, 0 }),
            BB.Pos(new double[] { 0, 0, 0.3, 0 }, new double[] { 0.8, 0, -0.2, 0 }, new double[] { 1.6, 0, 0.3, 0 }));
        walk["leg_left"] = BB.Rot(new double[] { 0, -25, 0, 0 }, new double[] { 0.8, 20, 0, 0 }, new double[] { 1.6, -25, 0, 0 });
        walk["leg_right"] = BB.Rot(new double[] { 0, 20, 0, 0 }, new double[] { 0.8, -25, 0, 0 }, new double[] { 1.6, 20, 0, 0 });
        walk["arm_left"] = BB.Rot(new double[] { 0, 25, 0, 6 }, new double[] { 0.5, -35, 0, -8 }, new double[] { 1.6, 25, 0, 6 });
        walk["arm_right"] = BB.Rot(new double[] { 0, -25, 0, -6 }, new double[] { 0.8, 25, 0, 8 }, new double[] { 1.6, -25, 0, -6 });
        walk["hand_left"] = BB.Rot(new double[] { 0, 29, 0, 7 }, new double[] { 0.65, -42, 0, -10 }, new double[] { 1.6, 29, 0, 7 });
        walk["hand_right"] = BB.Rot(new double[] { 0, -29, 0, -7 }, new double[] { 0.95, 29, 0, 9 }, new double[] { 1.6, -29, 0, -7 });
        walk["kelp_hair"] = BB.Rot(new double[] { 0, -30, 0, -5 }, new double[] { 0.8, -30, 0, 6 }, new double[] { 1.6, -30, 0, -5 });
        walk["waist_kelp"] = BB.Rot(new double[] { 0, -25, 0, 4 }, new double[] { 0.8, -25, 0, -6 }, new double[] { 1.6, -25, 0, 4 });
        anims.Add(BB.Anim("walk", "loop", 1.6, walk));

        // ---- attack: default DROWNED melee. Anticipation (claws drawn back) -> lunge ->
        // settle exactly back onto idle's t=0 pose for a seamless hand-off into the idle loop. ----
        var attack = BB.Pose();
        attack["body"] = BB.Pos(new double[] { 0, 0, 0, 0 }, new double[] { 0.25, 0, 0.1, 0.6 },
                                new double[] { 0.45, 0, -0.15, -1.3 }, new double[] { 0.7, 0, -0.05, -0.9 },
                                new double[] { 1.0, 0, 0, 0 });
        // Head pitch is kept (a lunge is a head thrust); only yaw was dropped for hi_ tracking.
        attack["hi_head"] = BB.Rot(new double[] { 0, 0, 0, 0 }, new double[] { 0.25, 6, 0, 0 },
                                new double[] { 0.45, -20, 0, 0 }, new double[] { 0.7, -10, 0, 0 }, new double[] { 1.0, 0, 0, 0 });
        attack["arm_left"] = BB.Rot(new double[] { 0, 3, 0, 0 }, new double[] { 0.25, -40, 0, -12 },
                                    new double[] { 0.45, 72, 0, 14 }, new double[] { 0.7, 55, 0, 8 }, new double[] { 1.0, 3, 0, 0 });
        attack["arm_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 0.25, -40, 0, 12 },
                                     new double[] { 0.45, 72, 0, -14 }, new double[] { 0.7, 55, 0, -8 }, new double[] { 1.0, -2, 0, 0 });
        attack["hand_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 0.35, -55, 0, -14 },
                                     new double[] { 0.55, 85, 0, 18 }, new double[] { 0.8, 60, 0, 10 }, new double[] { 1.0, 2, 0, 0 });
        attack["hand_right"] = BB.Rot(new double[] { 0, -1, 0, 0 }, new double[] { 0.35, -55, 0, 14 },
                                      new double[] { 0.55, 85, 0, -18 }, new double[] { 0.8, 60, 0, -10 }, new double[] { 1.0, -1, 0, 0 });
        attack["leg_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 0.25, -15, 0, 0 },
                                    new double[] { 0.45, 25, 0, 0 }, new double[] { 0.7, 10, 0, 0 }, new double[] { 1.0, 2, 0, 0 });
        attack["leg_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 0.25, 15, 0, 0 },
                                     new double[] { 0.45, -20, 0, 0 }, new double[] { 0.7, -8, 0, 0 }, new double[] { 1.0, -2, 0, 0 });
        attack["kelp_hair"] = BB.Rot(new double[] { 0, 0, 0, -4 }, new double[] { 0.3, 0, 0, 10 },
                                     new double[] { 0.55, 0, 0, -25 }, new double[] { 0.85, 0, 0, -8 }, new double[] { 1.0, 0, 0, -4 });
        attack["waist_kelp"] = BB.Rot(new double[] { 0, 0, 0, 3 }, new double[] { 0.3, 0, 0, -8 },
                                      new double[] { 0.55, 0, 0, 20 }, new double[] { 0.85, 0, 0, 6 }, new double[] { 1.0, 0, 0, 3 });
        anims.Add(BB.Anim("attack", "once", 1.0, attack));

        // ---- death: BetterModel built-in, plays once when the mob dies. A drowned thing does
        // not topple like a land mob — it goes limp and sinks, so the body sags and rotates
        // face-down while the limbs and kelp trail upward, lagging the torso. "hold" keeps the
        // final pose for the corpse's remaining frames instead of snapping back to idle. ----
        var death = BB.Pose();
        death["body"] = BB.Merge(
            BB.Rot(new double[] { 0, 0, 0, 0 }, new double[] { 0.3, -18, 0, 6 },
                   new double[] { 0.9, -62, 0, 10 }, new double[] { 1.4, -78, 0, 8 }),
            BB.Pos(new double[] { 0, 0, 0, 0 }, new double[] { 0.3, 0, -0.6, 0 },
                   new double[] { 1.4, 0, -3.4, 0 }));
        death["hi_head"] = BB.Rot(new double[] { 0, 0, 0, 0 }, new double[] { 0.45, 14, 0, -5 },
                                  new double[] { 1.4, 26, 0, -8 });
        death["arm_left"] = BB.Rot(new double[] { 0, 3, 0, 0 }, new double[] { 0.5, 34, 0, 14 },
                                   new double[] { 1.4, 58, 0, 22 });
        death["arm_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 0.6, 30, 0, -12 },
                                    new double[] { 1.4, 54, 0, -20 });
        death["hand_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 0.65, 26, 0, 10 },
                                    new double[] { 1.4, 40, 0, 15 });
        death["hand_right"] = BB.Rot(new double[] { 0, -1, 0, 0 }, new double[] { 0.75, 22, 0, -9 },
                                     new double[] { 1.4, 36, 0, -13 });
        death["leg_left"] = BB.Rot(new double[] { 0, 2, 0, 0 }, new double[] { 0.5, 12, 0, 4 },
                                   new double[] { 1.4, 22, 0, 6 });
        death["leg_right"] = BB.Rot(new double[] { 0, -2, 0, 0 }, new double[] { 0.6, 10, 0, -3 },
                                    new double[] { 1.4, 19, 0, -5 });
        death["kelp_hair"] = BB.Rot(new double[] { 0, 0, 0, -4 }, new double[] { 0.55, 28, 0, 12 },
                                    new double[] { 1.4, 46, 0, 6 });
        death["waist_kelp"] = BB.Rot(new double[] { 0, 0, 0, 3 }, new double[] { 0.65, 24, 0, -10 },
                                     new double[] { 1.4, 40, 0, -4 });
        anims.Add(BB.Anim("death", "hold", 1.4, death));

        return BB.Write("tyrant_siren_5", outDir, scratchDir, outliner, anims, null);
    }
}
