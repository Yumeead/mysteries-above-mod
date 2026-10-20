// fool_ancient_wraith_5 — "Стародавня Примара" (Fool pathway, Sequence 5 "Marionettist").
//
// Lore (LotM ch. 955 + the Marionettist potion formula, whose main ingredient is literally
// "Dust of Ancient Wraiths"): the last defender of a castle older than the present Epoch,
// still walking its post fifteen centuries later. Silver-black plate and a sword eaten
// nearly through by rust survived; the body did not. No skull, no legs — a heavy armoured
// top that trails off into spirit fog, and a cursed core burning in a breach in the cuirass.
//
// Silhouette rule for this mob: HEAVY TOP, EMPTY BOTTOM. Pauldrons wider than the chest,
// then a hard taper, then nothing. It must read as a floating suit of armour at 20 blocks
// with the texture switched off.
//
// BetterModel conventions (these names are load-bearing, not cosmetic):
//   * "idle" / "walk" / "spawn" / "death" / "damage" are BUILT-IN — BetterModel plays them
//     off the base entity's state. The base entity is an EVOKER, which navigates normally,
//     so "walk" fires; it is never noAi, so idle_fly/walk_fly would be dead and are absent.
//   * every "cast_*" / "recoil" / "flame_jump" / "attack" animation is NOT built-in and is
//     fired from MythicMobs via state{}. All are "once" so they stop themselves.
//   * bone tag "hi_" makes that bone AND its children follow the entity's head rotation, so
//     hi_head aims the visor at whoever it is tracking and the plume rides along. Because
//     look-tracking ADDS to animation rotation, no animation keys head YAW — pitch only.
//
// All animation numbers below are VISUAL: +X right, +Y up, -Z forward (north).
// BB.Rot / BB.Pos apply Blockbench's axis negation for you.

using System;
using System.Collections.Generic;

public static class Model
{
    // ---- palette ----
    // Silver-black plate, hue-shifted: shadows colder and more saturated, highlights warmer
    // and washed out. Kept off pure gray on purpose — a straight value ramp read as concrete.
    static readonly int[] AR0 = BB.HSV(228, 48, 8);
    static readonly int[] AR1 = BB.HSV(224, 40, 16);
    static readonly int[] AR2 = BB.HSV(220, 31, 25);
    static readonly int[] AR3 = BB.HSV(215, 23, 36);
    static readonly int[] AR4 = BB.HSV(209, 16, 49);
    static readonly int[][] AR = new int[][] { AR0, AR1, AR2, AR3, AR4 };

    // Tarnish. Silver goes green-black, not brown — this is what separates the armour from
    // the sword, which goes orange-red instead.
    static readonly int[] PT0 = BB.HSV(158, 34, 18);
    static readonly int[] PT1 = BB.HSV(150, 28, 27);

    // Rust ramp for the blade and guard.
    static readonly int[] RU1 = BB.HSV(21, 68, 36);
    static readonly int[] RU2 = BB.HSV(26, 62, 50);
    static readonly int[] RU3 = BB.HSV(31, 51, 63);

    // Tabard, faded past legibility.
    static readonly int[] CL0 = BB.HSV(32, 32, 20);
    static readonly int[] CL1 = BB.HSV(34, 27, 33);
    static readonly int[] CL2 = BB.HSV(36, 22, 46);
    static readonly int[] CLX = BB.HSV(28, 30, 26);   // ghost heraldry, one step off the cloth

    // Spectral accent — the Fool pathway's LIGHT_PURPLE, restricted to three places only:
    // the visor lights, the cursed core, the fog. It must never spread onto the plate.
    static readonly int[] SP1 = BB.HSV(277, 56, 33);
    static readonly int[] SP2 = BB.HSV(281, 47, 57);
    static readonly int[] SP3 = BB.HSV(286, 30, 84);

    // "Nothing is in there" — darker than any shadow in the plate ramp.
    static readonly int[] VD = BB.HSV(268, 44, 6);

    static readonly int[] MI0 = new int[] { 96, 74, 124, 84 };
    static readonly int[] MI1 = new int[] { 112, 86, 140, 150 };
    static readonly int[] MI2 = new int[] { 136, 110, 168, 52 };
    static readonly int[] TR = new int[] { 0, 0, 0, 0 };

    // ---- painters ----

    static int[] Plate(int i)
    {
        if (i < 0) i = 0;
        if (i > 4) i = 4;
        return AR[i];
    }

    // Corrosion creeping up from a plate's bottom edge. A band with a varying depth, not
    // scattered speckles — scattered dither on a large flat face reads as noise.
    static void Corrode(BB.Put put, int w, int h)
    {
        if (h < 4) return;
        for (int u = 0; u < w; u++)
        {
            int depth = ((u * 7) % 5 < 2) ? 2 : 1;
            for (int k = 0; k < depth; k++) put(u, h - 1 - k, k == 0 ? PT0 : PT1);
        }
    }

    // Front-facing plate: value ramp down the face plus a one-pixel darker falloff at each
    // side, so the plate reads as curved instead of flat.
    static void PlateFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.18 ? 4 : (t < 0.46 ? 3 : (t < 0.80 ? 2 : 1));
            for (int u = 0; u < w; u++)
                put(u, v, Plate(w > 2 && (u == 0 || u == w - 1) ? i - 1 : i));
        }
        Corrode(put, w, h);
    }

    static void PlateSide(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.20 ? 3 : (t < 0.66 ? 2 : 1);
            for (int u = 0; u < w; u++) put(u, v, Plate(i));
        }
        Corrode(put, w, h);
    }

    static void PlateBack(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.16 ? 2 : (t < 0.60 ? 1 : 0);
            for (int u = 0; u < w; u++) put(u, v, Plate(i));
        }
    }

    static void PlateTop(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int i = (v < h / 2) ? 4 : 3;                 // front half catches the light
                if (u == 0 || u == w - 1) i -= 1;
                put(u, v, Plate(i));
            }
    }

    // Visor: a dark recess with two lights in it. The lights stay solid — a dark pixel
    // splitting each one would read as four eyes at game distance.
    static void HelmFront(BB.Put put, int w, int h)
    {
        PlateFront(put, w, h);
        int slit = h / 2 - 1;
        for (int u = 1; u < w - 1; u++)
        {
            put(u, slit - 1, Plate(1));                      // brow shadow above the opening
            put(u, slit, VD);
            put(u, slit + 1, VD);
        }
        put(w / 2 - 2, slit, SP3); put(w / 2 - 2, slit + 1, SP2);
        put(w / 2 + 1, slit, SP3); put(w / 2 + 1, slit + 1, SP2);
    }

    // The breach the cursed core sits in. The lip is torn on a checker so the hole never
    // reads as a printed rectangle.
    static void CuirassFront(BB.Put put, int w, int h)
    {
        PlateFront(put, w, h);
        int x0 = w / 2 - 2, x1 = w / 2 + 1;
        int y0 = 3, y1 = 6;
        for (int v = y0 - 1; v <= y1 + 1; v++)
            for (int u = x0 - 1; u <= x1 + 1; u++)
            {
                if (u < 0 || u >= w || v < 0 || v >= h) continue;
                bool inside = u >= x0 && u <= x1 && v >= y0 && v <= y1;
                if (inside) put(u, v, VD);
                else if ((u + v) % 2 == 0) put(u, v, Plate(0));
            }
    }

    // Ragged hem: hard transparent cuts of varying depth with a darker frayed last row.
    // Hard cuts, not an alpha fade — a soft gradient is not a Minecraft silhouette.
    static void Hem(BB.Put put, int w, int h)
    {
        if (h < 4) return;
        for (int u = 0; u < w; u++)
        {
            int m = (u * 5) % 7;
            int cut = m < 3 ? 3 : (m < 5 ? 2 : 1);
            for (int k = 0; k < cut; k++) put(u, h - 1 - k, TR);
            put(u, h - 1 - cut, CL0);
        }
    }

    static void ClothPanel(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.22 ? CL2 : (t < 0.62 ? CL1 : CL0);
            for (int u = 0; u < w; u++) put(u, v, c);
        }
        Hem(put, w, h);
    }

    static void SkirtFront(BB.Put put, int w, int h)
    {
        ClothPanel(put, w, h);
        int cx = w / 2;                                      // heraldry worn to a ghost of itself
        for (int k = 0; k < 3; k++)
        {
            if (cx - 1 - k >= 0) put(cx - 1 - k, 1 + k, CLX);
            if (cx + k < w) put(cx + k, 1 + k, CLX);
        }
    }

    // Blade: steel survives near the hilt, rust wins toward the tip, and the edges are
    // bitten clean through in four places — the "almost snapping" detail from the novel.
    static void BladeSide(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            for (int u = 0; u < w; u++)
            {
                bool edge = (u == 0 || u == w - 1);
                if (edge) put(u, v, t < 0.35 ? RU1 : (t < 0.72 ? RU2 : RU3));
                else put(u, v, t < 0.28 ? AR2 : (t < 0.58 ? AR1 : RU1));
            }
        }
        put(0, h * 45 / 100, TR);
        put(w - 1, h * 62 / 100, TR);
        put(w - 1, h * 68 / 100, TR);
        put(0, h * 82 / 100, TR);
    }

    static void CoreFace(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                bool corner = (u == 0 || u == w - 1) && (v == 0 || v == h - 1);
                bool edge = u == 0 || v == 0 || u == w - 1 || v == h - 1;
                put(u, v, corner ? SP1 : (edge ? SP2 : SP3));
            }
    }

    // A plume torn short by fifteen centuries: cut from the top, deeper along its length.
    static void Crest(BB.Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            int cut = (u * 2) / Math.Max(1, w - 1);
            if ((u * 3) % 5 == 1) cut += 1;
            if (cut > h - 1) cut = h - 1;
            for (int v = 0; v < h; v++)
            {
                if (v < cut) { put(u, v, TR); continue; }
                int span = h - 1 - cut;
                double t = span <= 0 ? 0 : (double)(v - cut) / span;
                put(u, v, t < 0.34 ? CL2 : (t < 0.70 ? CL1 : CL0));
            }
        }
    }

    // Spirit fog: opaque where it leaves the armour, dithering out to nothing at the ground.
    // Each column dissolves at its OWN height — a shared cutoff row produced a straight
    // dither line and a regular sawtooth hem, which read as ice spikes rather than fog.
    static void MistPanel(BB.Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            int lift = ((u * 5) % 7) % 4;                    // 0..3 rows of extra fade
            int span = Math.Max(1, h - 1 - lift);
            for (int v = 0; v < h; v++)
            {
                double t = (double)v / span;
                int[] c;
                if (t < 0.30) c = MI1;
                else if (t < 0.58) c = MI0;
                else if (t < 0.82) c = ((u + v) % 2 == 0) ? MI0 : MI2;
                else if (t < 1.0) c = ((u + v) % 2 == 0) ? MI2 : TR;
                else c = TR;
                put(u, v, c);
            }
        }
    }

    // Paint all six faces of a plate prefix in one call.
    static void PaintPlate(string p)
    {
        BB.Paint(p + "_n", PlateFront);
        BB.Paint(p + "_s", PlateBack);
        BB.Paint(p + "_e", PlateSide);
        BB.Paint(p + "_w", PlateSide);
        BB.Paint(p + "_u", PlateTop);
        BB.Paint(p + "_d", BB.Flat(AR0));
    }

    public static string Build(string outDir, string scratchDir)
    {
        BB.Init(64, 64);

        // ---- geometry ----
        // Left-side elements own the UV; right-side ones reuse it via BB.Faces6.
        string cuirass = BB.Box("cuirass", "cui", new double[] { -4, 15, -3 }, new double[] { 4, 26, 3 });
        string helm = BB.Box("helm", "helm", new double[] { -4, 26, -4 }, new double[] { 4, 34, 4 });
        string skirt = BB.Box("skirt", "skirt", new double[] { -4.5, 8, -3.5 }, new double[] { 4.5, 16, 3.5 });

        string pauldL = BB.Box("pauldron_left", "pauld", new double[] { 4, 23, -4 }, new double[] { 8, 28, 4 });
        string pauldR = BB.Cube("pauldron_right", new double[] { -8, 23, -4 }, new double[] { -4, 28, 4 },
                                BB.Faces6("pauld"));

        string armL = BB.Box("arm_left_box", "arm", new double[] { 4.5, 16, -1.5 }, new double[] { 7.5, 25, 1.5 });
        string armR = BB.Cube("arm_right_box", new double[] { -7.5, 16, -1.5 }, new double[] { -4.5, 25, 1.5 },
                              BB.Faces6("arm"));

        string gauntL = BB.Box("gauntlet_left", "gaunt", new double[] { 4, 12, -2 }, new double[] { 8, 16, 2 });
        string gauntR = BB.Cube("gauntlet_right", new double[] { -8, 12, -2 }, new double[] { -4, 16, 2 },
                                BB.Faces6("gaunt"));

        string guard = BB.Box("sword_guard", "guard", new double[] { -8.5, 11, -1.5 }, new double[] { -3.5, 12, 1.5 });
        // Flat faces point front/back so the blade stays readable in the frontal silhouette.
        string blade = BB.Box("sword_blade", "blade", new double[] { -7.5, 0, -0.5 }, new double[] { -4.5, 11, 0.5 });

        // The cursed core sits 2 units proud of the cuirass, in the breach painted around it.
        string core = BB.Box("core", "core", new double[] { -1.5, 20, -5 }, new double[] { 1.5, 23, -2 });

        string crest = BB.Plane("crest", "crest", new double[] { 0, 33, -1 }, new double[] { 0, 37, 6 }, "x");

        // Crossed planes for the fog, in two layers so the lower one can lag the upper.
        string mistA = BB.Plane("mist_front", "mista", new double[] { -4, 3, 0 }, new double[] { 4, 10, 0 }, "z");
        string mistB = BB.Plane("mist_side", "mistb", new double[] { 0, 3, -4 }, new double[] { 0, 10, 4 }, "x");
        string mistC = BB.Plane("mist_front_low", "mistc", new double[] { -3, 0, 0.5 }, new double[] { 3, 5, 0.5 }, "z");
        string mistD = BB.Plane("mist_side_low", "mistd", new double[] { -0.5, 0, -3 }, new double[] { -0.5, 5, 3 }, "x");

        // ---- texture ----
        PaintPlate("cui"); BB.Paint("cui_n", CuirassFront);
        PaintPlate("helm"); BB.Paint("helm_n", HelmFront);
        PaintPlate("pauld");
        PaintPlate("arm");
        PaintPlate("gaunt");

        BB.Paint("skirt_n", SkirtFront); BB.Paint("skirt_s", ClothPanel);
        BB.Paint("skirt_e", ClothPanel); BB.Paint("skirt_w", ClothPanel);
        BB.Paint("skirt_u", BB.Flat(CL0)); BB.Paint("skirt_d", BB.Flat(TR));

        BB.Paint("guard_n", BB.Flat(RU1)); BB.Paint("guard_s", BB.Flat(RU1));
        BB.Paint("guard_e", BB.Flat(RU2)); BB.Paint("guard_w", BB.Flat(RU2));
        BB.Paint("guard_u", BB.Flat(RU2)); BB.Paint("guard_d", BB.Flat(RU1));

        BB.Paint("blade_n", BladeSide); BB.Paint("blade_s", BladeSide, true);
        BB.Paint("blade_e", BB.Flat(RU2)); BB.Paint("blade_w", BB.Flat(RU2));
        BB.Paint("blade_u", BB.Flat(AR2)); BB.Paint("blade_d", BB.Flat(RU3));

        BB.Paint("core_n", CoreFace); BB.Paint("core_s", CoreFace);
        BB.Paint("core_e", CoreFace); BB.Paint("core_w", CoreFace);
        BB.Paint("core_u", CoreFace); BB.Paint("core_d", CoreFace);

        BB.Paint("crest_e", Crest); BB.Paint("crest_w", Crest, true);

        BB.Paint("mista_n", MistPanel); BB.Paint("mista_s", MistPanel, true);
        BB.Paint("mistb_e", MistPanel); BB.Paint("mistb_w", MistPanel, true);
        BB.Paint("mistc_n", MistPanel); BB.Paint("mistc_s", MistPanel, true);
        BB.Paint("mistd_e", MistPanel); BB.Paint("mistd_w", MistPanel, true);

        // ---- bones ----
        // Pauldrons hang off the BODY, not the arms: real plate lets the arm swing out from
        // under the shoulder cop, and parenting them to arm_* made them fly off on the big
        // casting raises.
        var outliner = new List<object> {
            BB.Group("body", new double[] { 0, 15, 0 }, BB.Kids(
                cuirass, pauldL, pauldR,
                BB.Group("hi_head", new double[] { 0, 26, 0 }, BB.Kids(helm, crest)),
                BB.Group("core", new double[] { 0, 21.5, -3.5 }, BB.Kids(core)),
                BB.Group("arm_left", new double[] { 6, 25, 0 }, BB.Kids(
                    armL,
                    BB.Group("hand_left", new double[] { 6, 16, 0 }, BB.Kids(gauntL))
                )),
                BB.Group("arm_right", new double[] { -6, 25, 0 }, BB.Kids(
                    armR,
                    BB.Group("hand_right", new double[] { -6, 16, 0 }, BB.Kids(
                        gauntR,
                        BB.Group("sword", new double[] { -6, 12, 0 }, BB.Kids(guard, blade))
                    ))
                )),
                BB.Group("skirt", new double[] { 0, 16, 0 }, BB.Kids(skirt)),
                BB.Group("mist_hi", new double[] { 0, 10, 0 }, BB.Kids(
                    mistA, mistB,
                    BB.Group("mist_lo", new double[] { 0, 5, 0 }, BB.Kids(mistC, mistD))
                ))
            ))
        };

        var anims = new List<object>();
        Animate(anims);
        return BB.Write("fool_ancient_wraith_5", outDir, scratchDir, outliner, anims, null);
    }

    static double[] K(double t, double a, double b, double c)
    {
        return new double[] { t, a, b, c };
    }

    // ---- animations ----
    // Every "once" animation starts and ends on all-zeros, and idle's first/last keyframe is
    // all-zeros too, so any state{} cast hands back to the idle loop without a pop.
    static void Animate(List<object> anims)
    {
        // ---- idle (BUILT-IN, and the eternal base layer: it runs under everything else,
        // so it holds only what is true in every state — floating, drifting fog, the core
        // turning). No head yaw: hi_head already tracks the target and a keyed turn would
        // add to it and read as the helm sliding off whoever it is looking at. ----
        var idle = BB.Pose();
        idle["body"] = BB.Merge(
            BB.Pos(K(0, 0, 0, 0), K(2.2, 0, 0.55, 0), K(4.4, 0, -0.25, 0), K(6, 0, 0, 0)),
            BB.Rot(K(0, 0, 0, 0), K(1.6, 0, 0, 1.3), K(4.2, 0, 0, -1.4), K(6, 0, 0, 0)));
        idle["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(2.2, 0, 0, 1.2), K(2.35, -7, 0, -2),
                                 K(3.7, -7, 0, -2), K(3.85, 1, 0, 1), K(6, 0, 0, 0));
        idle["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(2, 0, 120, 0), K(4, 0, 240, 0), K(6, 0, 360, 0)),
            BB.Pos(K(0, 0, 0, 0), K(1.5, 0, 0.2, -0.35), K(3.5, 0, -0.1, 0.15), K(6, 0, 0, 0)));
        idle["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(2.1, -4, 0, 2.5), K(4.3, 4, 0, -1.5), K(6, 0, 0, 0));
        idle["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(2.7, 4, 0, -3), K(4.9, -3, 0, 1.5), K(6, 0, 0, 0));
        idle["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(2.35, -5, 0, 0), K(4.6, 4, 0, 0), K(6, 0, 0, 0));
        idle["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(2.95, 5, 0, 0), K(5.2, -3, 0, 0), K(6, 0, 0, 0));
        idle["sword"] = BB.Rot(K(0, 0, 0, 0), K(2.6, -3, 0, -1.5), K(5, 4, 0, 1.5), K(6, 0, 0, 0));
        idle["skirt"] = BB.Rot(K(0, 0, 0, 0), K(2.4, -3, 0, 3), K(4.8, 3, 0, -1.5), K(6, 0, 0, 0));
        // The two fog layers turn in opposite directions on different periods, so the cloud
        // churns instead of spinning like a wheel.
        idle["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(2, 4, 120, -5), K(4, -4, 240, 4), K(6, 0, 360, 0));
        idle["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(1.6, -5, -90, 6), K(3.8, 5, -220, -4), K(6, 0, -360, 0));
        anims.Add(BB.Anim("idle", "loop", 6, idle));

        // ---- walk (BUILT-IN). It has no legs: locomotion is a GLIDE. Body tips forward,
        // the tabard and both fog layers stream backward, the arms only drift. Any stepping
        // motion here would undo the whole "floating armour" read. ----
        var walk = BB.Pose();
        walk["body"] = BB.Merge(
            BB.Rot(K(0, -7, 0, 1.5), K(0.8, -9, 0, -1.5), K(1.6, -7, 0, 1.5)),
            BB.Pos(K(0, 0, 0.2, 0), K(0.8, 0, -0.3, 0), K(1.6, 0, 0.2, 0)));
        walk["hi_head"] = BB.Rot(K(0, 5, 0, -1), K(0.8, 7, 0, 1), K(1.6, 5, 0, -1));
        walk["arm_left"] = BB.Rot(K(0, -8, 0, 3), K(0.8, 6, 0, 5), K(1.6, -8, 0, 3));
        walk["arm_right"] = BB.Rot(K(0, 5, 0, -3), K(0.8, -8, 0, -5), K(1.6, 5, 0, -3));
        walk["hand_left"] = BB.Rot(K(0, -10, 0, 0), K(0.95, 8, 0, 0), K(1.6, -10, 0, 0));
        walk["hand_right"] = BB.Rot(K(0, 7, 0, 0), K(0.95, -10, 0, 0), K(1.6, 7, 0, 0));
        walk["sword"] = BB.Rot(K(0, -14, 0, 2), K(0.9, -20, 0, -2), K(1.6, -14, 0, 2));
        walk["skirt"] = BB.Rot(K(0, -22, 0, -3), K(0.8, -26, 0, 3), K(1.6, -22, 0, -3));
        walk["mist_hi"] = BB.Rot(K(0, -30, 0, 5), K(0.8, -36, 0, -5), K(1.6, -30, 0, 5));
        walk["mist_lo"] = BB.Rot(K(0, -16, 0, -6), K(0.95, -22, 0, 7), K(1.6, -16, 0, -6));
        walk["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.8, 0, 180, 0), K(1.6, 0, 360, 0)),
            BB.Pos(K(0, 0, 0, -0.2), K(0.8, 0, 0, -0.5), K(1.6, 0, 0, -0.2)));
        anims.Add(BB.Anim("walk", "loop", 1.6, walk));

        // ---- spawn (BUILT-IN). It does not walk in: it comes UP, out of its own grave.
        // Sunk fully below the floor, rises with the helm bowed, overshoots, then the head
        // snaps up on the last beat — that snap is the moment it notices you. ----
        var spawn = BB.Pose();
        spawn["body"] = BB.Merge(
            BB.Pos(K(0, 0, -30, 0), K(0.55, 0, -12, 0), K(1.05, 0, 0.9, 0), K(1.3, 0, -0.2, 0), K(1.6, 0, 0, 0)),
            BB.Rot(K(0, -6, 0, 0), K(1.05, 3, 0, 0), K(1.6, 0, 0, 0)));
        spawn["hi_head"] = BB.Rot(K(0, -36, 0, 0), K(0.55, -34, 0, 0), K(1.05, -22, 0, 0),
                                  K(1.22, 9, 0, 0), K(1.6, 0, 0, 0));
        spawn["arm_left"] = BB.Rot(K(0, -14, 0, 6), K(0.9, -10, 0, 4), K(1.25, 7, 0, -2), K(1.6, 0, 0, 0));
        spawn["arm_right"] = BB.Rot(K(0, -14, 0, -6), K(0.9, -10, 0, -4), K(1.25, 7, 0, 2), K(1.6, 0, 0, 0));
        spawn["hand_left"] = BB.Rot(K(0, -18, 0, 0), K(1.0, -12, 0, 0), K(1.6, 0, 0, 0));
        spawn["hand_right"] = BB.Rot(K(0, -18, 0, 0), K(1.0, -12, 0, 0), K(1.6, 0, 0, 0));
        spawn["sword"] = BB.Rot(K(0, 34, 0, 0), K(0.8, 26, 0, 0), K(1.25, -8, 0, 0), K(1.6, 0, 0, 0));
        spawn["skirt"] = BB.Rot(K(0, 26, 0, 0), K(0.8, 20, 0, 0), K(1.2, -9, 0, 0), K(1.6, 0, 0, 0));
        spawn["mist_hi"] = BB.Rot(K(0, 34, 0, 0), K(0.75, 26, 0, 0), K(1.2, -12, 0, 0), K(1.6, 0, 0, 0));
        spawn["mist_lo"] = BB.Rot(K(0, 28, -60, 0), K(0.85, 20, -30, 0), K(1.3, -10, 10, 0), K(1.6, 0, 0, 0));
        // The core lights up last, spinning up from dead as the curse takes hold again.
        spawn["core"] = BB.Rot(K(0, 0, 0, 0), K(1.0, 0, 180, 0), K(1.6, 0, 540, 0));
        anims.Add(BB.Anim("spawn", "once", 1.6, spawn));

        // ---- death (BUILT-IN, "hold" so the corpse stays down instead of snapping back to
        // idle). One jolt, then the armour slumps and sinks back into the ground while the
        // sword slips out of the gauntlet. The core rides UP relative to the sinking body —
        // the curse leaving its vessel is what actually kills it. ----
        var death = BB.Pose();
        death["body"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.25, 15, 0, -5), K(0.75, -20, 0, 7), K(1.25, -34, 0, 5), K(1.8, -40, 0, 4)),
            BB.Pos(K(0, 0, 0, 0), K(0.25, 0, 0.5, 0.4), K(0.75, 0, -2, 0), K(1.25, 0, -9, 0), K(1.8, 0, -22, 0)));
        death["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.25, 22, 0, 0), K(0.85, -26, 0, 6), K(1.8, -34, 0, 9));
        death["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.3, -38, 0, 22), K(0.9, 20, 0, 10), K(1.8, 34, 0, 4));
        death["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.35, -34, 0, -20), K(0.95, 22, 0, -9), K(1.8, 36, 0, -3));
        death["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.45, -30, 0, 0), K(1.1, 26, 0, 0), K(1.8, 38, 0, 0));
        death["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.45, -30, 0, 0), K(1.1, 26, 0, 0), K(1.8, 38, 0, 0));
        death["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.5, 18, 0, 0), K(1.1, 58, 0, 14), K(1.8, 84, 0, 22));
        death["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.4, 20, 0, 0), K(1.0, -14, 0, 0), K(1.8, -24, 0, 0));
        death["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.45, 24, 0, 0), K(1.1, -18, 0, 0), K(1.8, -30, 0, 0));
        death["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.55, 20, 40, 0), K(1.2, -14, 90, 0), K(1.8, -26, 140, 0));
        death["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.9, 0, 260, 0), K(1.8, 0, 700, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.5, 0, 1.5, -1), K(1.8, 0, 16, -3)));
        anims.Add(BB.Anim("death", "hold", 1.8, death));

        // ---- damage (BUILT-IN, fires on every hit). Deliberately small: the big evasive
        // lurch is "recoil", which MythicMobs fires only when Danger Intuition actually
        // procs. If both play at once, the larger one has to win. ----
        var damage = BB.Pose();
        damage["body"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.1, 7, 0, -3), K(0.22, -2, 0, 1), K(0.35, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.1, 0, 0, 0.7), K(0.35, 0, 0, 0)));
        damage["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.1, 10, 0, 0), K(0.35, 0, 0, 0));
        damage["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.12, -12, 0, 7), K(0.35, 0, 0, 0));
        damage["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.12, -12, 0, -7), K(0.35, 0, 0, 0));
        damage["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.14, 12, 0, 0), K(0.35, 0, 0, 0));
        damage["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 14, 0, 0), K(0.35, 0, 0, 0));
        damage["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.2, 11, 0, 0), K(0.35, 0, 0, 0));
        anims.Add(BB.Anim("damage", "once", 0.35, damage));

        Casts(anims);
    }

    // ---- cast animations ----
    // None of these are built-in. Each one is fired by a state{} line inside the matching
    // metaskill in mythic-pack/Skills/fool.yml, so the gesture always matches the skill that
    // actually rolled out of kitcast. All are "once": a "loop" fired from state{} would never
    // stop on its own.
    static void Casts(List<object> anims)
    {
        // ---- attack: plain melee, whatever gets close enough. Wind the rusted blade up over
        // the shoulder, chop down. The sword bone lags the arm by ~0.06 s and overshoots it,
        // which is what sells the weight of a sword this size. ----
        var attack = BB.Pose();
        attack["body"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.22, 11, 0, -4), K(0.42, -16, 0, 5), K(0.62, -8, 0, 2), K(0.9, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.22, 0, 0.3, 0.5), K(0.42, 0, -0.5, -0.9), K(0.9, 0, 0, 0)));
        attack["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 8, 0, 0), K(0.42, -18, 0, 0), K(0.62, -9, 0, 0), K(0.9, 0, 0, 0));
        attack["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -112, 0, -14), K(0.34, -74, 0, -10),
                                     K(0.46, 58, 0, 6), K(0.64, 32, 0, 3), K(0.9, 0, 0, 0));
        attack["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.26, -34, 0, 0), K(0.5, 42, 0, 0), K(0.68, 18, 0, 0), K(0.9, 0, 0, 0));
        attack["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.28, -42, 0, 0), K(0.52, 54, 0, 0), K(0.7, 22, 0, 0), K(0.9, 0, 0, 0));
        attack["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 26, 0, 10), K(0.46, -30, 0, 16), K(0.9, 0, 0, 0));
        attack["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 20, 0, 0), K(0.54, -24, 0, 0), K(0.9, 0, 0, 0));
        attack["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.28, 14, 0, 0), K(0.52, -22, 0, 0), K(0.9, 0, 0, 0));
        attack["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 18, 0, 0), K(0.56, -26, 0, 0), K(0.9, 0, 0, 0));
        attack["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.36, 14, 0, 0), K(0.62, -20, 0, 0), K(0.9, 0, 0, 0));
        attack["core"] = BB.Rot(K(0, 0, 0, 0), K(0.45, 0, 180, 0), K(0.9, 0, 360, 0));
        anims.Add(BB.Anim("attack", "once", 0.9, attack));

        // ---- cast_marionette (MA_Fool_S5_Marionette): the signature Marionettist gesture and
        // the longest cast it has. Both arms go up in front with the gauntlets hanging
        // fingers-down off the wrists (hand rotation cancels the parent's raise), hold while
        // the strings go taut, then JERK back on the beat where the skill's pull{} lands. ----
        var mar = BB.Pose();
        mar["body"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.45, 6, 0, 0), K(0.75, 7, 0, 0), K(1.0, 15, 0, 0), K(1.3, 5, 0, 0), K(1.6, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.45, 0, 0.8, 0), K(1.0, 0, 0.3, 1.1), K(1.6, 0, 0, 0)));
        mar["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.45, 12, 0, 0), K(0.75, 12, 0, 0), K(1.0, -8, 0, 0), K(1.6, 0, 0, 0));
        mar["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.45, 122, 0, 16), K(0.75, 118, 0, 14),
                                 K(1.0, 72, 0, 26), K(1.3, 34, 0, 12), K(1.6, 0, 0, 0));
        mar["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.45, 122, 0, -16), K(0.75, 118, 0, -14),
                                  K(1.0, 72, 0, -26), K(1.3, 34, 0, -12), K(1.6, 0, 0, 0));
        mar["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -96, 0, 0), K(0.8, -92, 0, 0), K(1.05, -60, 0, 0), K(1.6, 0, 0, 0));
        mar["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -96, 0, 0), K(0.8, -92, 0, 0), K(1.05, -60, 0, 0), K(1.6, 0, 0, 0));
        // The blade swings out of the way rather than waving overhead with the puppeteering hand.
        mar["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -74, 0, 0), K(1.0, -62, 0, 0), K(1.6, 0, 0, 0));
        mar["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.5, 0, 260, 0), K(1.0, 0, 620, 0), K(1.6, 0, 720, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.5, 0, 0.4, -1.6), K(1.0, 0, 0.4, -1.8), K(1.6, 0, 0, 0)));
        mar["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -10, 0, 0), K(1.05, 16, 0, 0), K(1.6, 0, 0, 0));
        mar["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -14, 60, 0), K(1.05, 20, 140, 0), K(1.6, 0, 240, 0));
        mar["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.6, -11, -70, 0), K(1.15, 16, -160, 0), K(1.6, 0, -240, 0));
        anims.Add(BB.Anim("cast_marionette", "once", 1.6, mar));

        // ---- cast_bolt (MA_Fool_S7_AirBullet): the free (left) hand, not the sword hand.
        // Short, snappy palm thrust with a body twist behind it — the projectile is instant,
        // so the animation has to be too. ----
        var bolt = BB.Pose();
        bolt["body"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 0, -9, 0), K(0.34, -4, 12, 0), K(0.7, 0, 0, 0));
        bolt["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 5, 0, 0), K(0.34, -7, 0, 0), K(0.7, 0, 0, 0));
        bolt["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -46, 0, 14), K(0.34, 96, 0, 4), K(0.5, 74, 0, 6), K(0.7, 0, 0, 0));
        bolt["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -40, 0, 0), K(0.38, 26, 0, 0), K(0.7, 0, 0, 0));
        bolt["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 12, 0, -6), K(0.34, -10, 0, -4), K(0.7, 0, 0, 0));
        bolt["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 14, 0, 0), K(0.4, -12, 0, 0), K(0.7, 0, 0, 0));
        bolt["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.24, 8, 0, 0), K(0.44, -10, 0, 0), K(0.7, 0, 0, 0));
        bolt["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.26, 10, 0, 0), K(0.46, -12, 0, 0), K(0.7, 0, 0, 0));
        bolt["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 8, 0, 0), K(0.5, -9, 0, 0), K(0.7, 0, 0, 0));
        bolt["core"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 0, 200, 0), K(0.7, 0, 360, 0));
        anims.Add(BB.Anim("cast_bolt", "once", 0.7, bolt));

        // ---- cast_blade (MA_Fool_S8_PaperCutter): the projectile is a cutting edge, so it
        // comes off the sword — a fast horizontal flick across the body, the shortest
        // animation in the set. ----
        var blade = BB.Pose();
        blade["body"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 0, 11, 0), K(0.28, -3, -14, 0), K(0.6, 0, 0, 0));
        blade["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.28, -6, 0, 0), K(0.6, 0, 0, 0));
        blade["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.12, -56, 0, -22), K(0.28, 46, 0, -34),
                                    K(0.42, 22, 0, -16), K(0.6, 0, 0, 0));
        blade["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -30, 0, 0), K(0.32, 34, 0, 0), K(0.6, 0, 0, 0));
        blade["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -34, 0, -12), K(0.32, 62, 0, 18), K(0.46, 26, 0, 8), K(0.6, 0, 0, 0));
        blade["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.14, 18, 0, 8), K(0.3, -20, 0, 12), K(0.6, 0, 0, 0));
        blade["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 10, 0, 0), K(0.36, -14, 0, 0), K(0.6, 0, 0, 0));
        blade["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.2, 12, 0, 0), K(0.38, -16, 0, 0), K(0.6, 0, 0, 0));
        blade["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.24, 9, 0, 0), K(0.42, -12, 0, 0), K(0.6, 0, 0, 0));
        blade["core"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 0, 180, 0), K(0.6, 0, 360, 0));
        anims.Add(BB.Anim("cast_blade", "once", 0.6, blade));

        // ---- cast_summon (MA_Fool_S5_Puppets, fires under 35% HP): arms thrown wide and up,
        // helm tipped back, the core pushed out in front — then both hands SLAM down on the
        // ground, which is the frame the puppets appear on. ----
        var sum = BB.Pose();
        sum["body"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.35, 12, 0, 0), K(0.7, 12, 0, 0), K(0.95, -18, 0, 0), K(1.15, -6, 0, 0), K(1.4, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.35, 0, 1.4, 0), K(0.7, 0, 1.4, 0), K(0.95, 0, -1.1, 0), K(1.4, 0, 0, 0)));
        sum["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 20, 0, 0), K(0.7, 20, 0, 0), K(0.95, -18, 0, 0), K(1.4, 0, 0, 0));
        sum["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 24, 0, 58), K(0.7, 22, 0, 56), K(0.95, -28, 0, 10), K(1.15, -12, 0, 6), K(1.4, 0, 0, 0));
        sum["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 24, 0, -58), K(0.7, 22, 0, -56), K(0.95, -28, 0, -10), K(1.15, -12, 0, -6), K(1.4, 0, 0, 0));
        sum["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -34, 0, 0), K(0.75, -58, 0, 0), K(1.0, 30, 0, 0), K(1.4, 0, 0, 0));
        sum["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -34, 0, 0), K(0.75, -58, 0, 0), K(1.0, 30, 0, 0), K(1.4, 0, 0, 0));
        sum["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -48, 0, 0), K(0.95, 30, 0, 0), K(1.4, 0, 0, 0));
        sum["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.7, 0, 420, 0), K(1.4, 0, 720, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.35, 0, 0.6, -2.2), K(0.7, 0, 0.6, -2.4), K(0.95, 0, -0.4, 0.4), K(1.4, 0, 0, 0)));
        sum["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -16, 0, 0), K(1.0, 22, 0, 0), K(1.4, 0, 0, 0));
        sum["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -20, 90, 0), K(1.0, 28, 220, 0), K(1.4, 0, 300, 0));
        sum["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -16, -110, 0), K(1.1, 24, -240, 0), K(1.4, 0, -320, 0));
        anims.Add(BB.Anim("cast_summon", "once", 1.4, sum));

        // ---- recoil (MA_Fool_S9_DangerIntuition): the dodge. The body is yanked backward
        // first and the cloth and fog catch up a beat later, which is what makes it read as
        // being moved rather than posing. ----
        var rec = BB.Pose();
        rec["body"] = BB.Merge(
            BB.Pos(K(0, 0, 0, 0), K(0.1, 0, 0.6, 2.4), K(0.32, 0, 0.2, 0.9), K(0.5, 0, 0, 0)),
            BB.Rot(K(0, 0, 0, 0), K(0.1, 19, 0, -4), K(0.32, 7, 0, 2), K(0.5, 0, 0, 0)));
        rec["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.1, 14, 0, 0), K(0.5, 0, 0, 0));
        rec["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.14, -28, 0, 32), K(0.34, -8, 0, 12), K(0.5, 0, 0, 0));
        rec["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.14, -28, 0, -32), K(0.34, -8, 0, -12), K(0.5, 0, 0, 0));
        rec["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -22, 0, 0), K(0.5, 0, 0, 0));
        rec["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -22, 0, 0), K(0.5, 0, 0, 0));
        rec["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.2, 26, 0, 0), K(0.5, 0, 0, 0));
        rec["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 26, 0, 0), K(0.38, -8, 0, 0), K(0.5, 0, 0, 0));
        rec["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 30, 0, 0), K(0.4, -10, 0, 0), K(0.5, 0, 0, 0));
        rec["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 24, 0, 0), K(0.44, -8, 0, 0), K(0.5, 0, 0, 0));
        rec["core"] = BB.Rot(K(0, 0, 0, 0), K(0.25, 0, 160, 0), K(0.5, 0, 360, 0));
        anims.Add(BB.Anim("recoil", "once", 0.5, rec));

        // ---- flame_jump (MA_Fool_S7_FlameJump): the point-blank escape. Same direction as
        // recoil but a completely different shape — coil down first, then blow out backward
        // and upward off the flame burst, and come down. ----
        var fj = BB.Pose();
        fj["body"] = BB.Merge(
            BB.Pos(K(0, 0, 0, 0), K(0.15, 0, -1.3, -0.5), K(0.38, 0, 2.8, 2.6), K(0.62, 0, 1.0, 1.3), K(0.9, 0, 0, 0)),
            BB.Rot(K(0, 0, 0, 0), K(0.15, -13, 0, 0), K(0.38, 27, 0, -5), K(0.62, 11, 0, 2), K(0.9, 0, 0, 0)));
        fj["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.15, -14, 0, 0), K(0.38, 23, 0, 0), K(0.9, 0, 0, 0));
        fj["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.15, -22, 0, 14), K(0.38, -62, 0, 42), K(0.62, -24, 0, 18), K(0.9, 0, 0, 0));
        fj["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.15, -22, 0, -14), K(0.38, -62, 0, -42), K(0.62, -24, 0, -18), K(0.9, 0, 0, 0));
        fj["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -18, 0, 0), K(0.44, -44, 0, 0), K(0.9, 0, 0, 0));
        fj["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -18, 0, 0), K(0.44, -44, 0, 0), K(0.9, 0, 0, 0));
        fj["sword"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -14, 0, 0), K(0.44, 40, 0, 0), K(0.9, 0, 0, 0));
        fj["skirt"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -14, 0, 0), K(0.42, 42, 0, 0), K(0.68, 12, 0, 0), K(0.9, 0, 0, 0));
        fj["mist_hi"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -18, 0, 0), K(0.44, 48, 60, 0), K(0.7, 14, 100, 0), K(0.9, 0, 120, 0));
        fj["mist_lo"] = BB.Rot(K(0, 0, 0, 0), K(0.26, -14, 0, 0), K(0.5, 40, -70, 0), K(0.76, 11, -120, 0), K(0.9, 0, -140, 0));
        fj["core"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.44, 0, 300, 0), K(0.9, 0, 540, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.38, 0, 0, -1.2), K(0.9, 0, 0, 0)));
        anims.Add(BB.Anim("flame_jump", "once", 0.9, fj));
    }
}
