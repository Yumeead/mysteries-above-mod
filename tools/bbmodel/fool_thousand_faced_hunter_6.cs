// fool_thousand_faced_hunter_6 — "Тисячоликий Мисливець" (Fool pathway, Sequence 6 "Faceless").
//
// Lore: the novel never describes it — it exists only as the source of the Faceless potion's
// main ingredients (a MUTATED PITUITARY GLAND and 80 ml of blood, alongside a Human-skin
// Shadow's Characteristic), and the finished potion is DARK GREEN "like something alive".
// Those two facts drive the whole design: the swollen gland is the model's single focal
// point and its only emissive zone, and the green of that gland is the green of the potion.
//
// Silhouette rule for this mob: TALL, HUNCHED, LONG-ARMED — and FACELESS. Its own head is a
// blank keratin plate that splits down the middle; the faces it wears belong to other people
// and are stitched onto its chest. It must read as an ANIMAL wearing human faces, never as a
// man in a mask. At 20 blocks with the texture off: hunched shoulders, dangling arms to the
// shins, digitigrade Z-legs, and a lump on the back of the skull.
//
// BetterModel conventions (load-bearing names, not cosmetic):
//   * "idle"/"walk"/"spawn"/"death"/"damage" are BUILT-IN. The base entity is a WITCH: its
//     movement comes from rangedstance/provoke, i.e. through pathfinding navigation, so the
//     "walk" predicate (PathNavigation.isInProgress) fires. It is never noAi, so idle_fly /
//     walk_fly would be dead and are deliberately ABSENT.
//   * "attack" is fired by state{} on the mob (~onAttack); "cast_bolt", "cast_blade",
//     "flame_jump" and "recoil" are fired from inside the shared Fool metaskills in
//     mythic-pack/Skills/fool.yml. MA_Fool_S6's kit is exactly AirBullet + PaperCutter +
//     FlameJump + DangerIntuition, so those four are the whole cast set — cast_marionette /
//     cast_summon belong to Sequence 5 and are not modelled here.
//   * bone tag "hi_" makes hi_head AND its children (skull, gland, both mask halves) follow
//     the entity's head rotation. Look-tracking ADDS to animation rotation, so no animation
//     keys head YAW — pitch and roll only.
//
// REST ROTATIONS (static, NOT negated; animation values ADD on top of them):
//   torso  -14  lean forward = the hunch
//   neck   +14  cancels the lean so the skull stays level
//   arm_*  +16  hands swing forward of the shifted shoulders instead of trailing behind
//   shroud +14  cancels the lean so the skin cape hangs vertically
// Every animation number below is therefore RELATIVE to that hunched rest pose: negative X
// on the torso = hunch deeper, positive = straighten up.
//
// All animation numbers are VISUAL: +X right, +Y up, -Z forward (north).
// BB.Rot / BB.Pos apply Blockbench's axis negation for you.

using System;
using System.Collections.Generic;

public static class Model
{
    // ---- palette ----
    // Waxy corpse mauve. Hue-shifted: shadows cooler and more saturated, highlights warmer
    // (toward pink) and washed out. Off pure gray on purpose — a straight value ramp read as
    // stone, and this thing has to read as meat.
    static readonly int[] FL0 = BB.HSV(272, 30, 14);
    static readonly int[] FL1 = BB.HSV(276, 22, 24);
    static readonly int[] FL2 = BB.HSV(280, 16, 37);
    static readonly int[] FL3 = BB.HSV(288, 12, 50);
    static readonly int[] FL4 = BB.HSV(300, 9, 62);
    static readonly int[][] FLR = new int[][] { FL0, FL1, FL2, FL3, FL4 };

    // Keratin: the blank face plate, claws, brow ridges. Warm bone, not white.
    static readonly int[] KE0 = BB.HSV(44, 30, 26);
    static readonly int[] KE1 = BB.HSV(46, 26, 42);
    static readonly int[] KE2 = BB.HSV(48, 21, 58);
    static readonly int[] KE3 = BB.HSV(50, 15, 74);

    // Stolen human faces + the skin cape. Deliberately WARMER than the body so they read as
    // "not his" at a glance — this contrast is the whole point of the creature.
    static readonly int[] SK0 = BB.HSV(20, 46, 28);
    static readonly int[] SK1 = BB.HSV(24, 42, 43);
    static readonly int[] SK2 = BB.HSV(28, 36, 59);
    static readonly int[] SK3 = BB.HSV(32, 28, 74);
    static readonly int[] SKD = BB.HSV(16, 52, 14);   // eye/mouth marks: solid, low internal contrast

    // The Faceless potion's dark green: gland, weeping seams, the void behind the mask.
    static readonly int[] GR0 = BB.HSV(140, 62, 20);
    static readonly int[] GR1 = BB.HSV(112, 58, 38);
    static readonly int[] GR2 = BB.HSV(90, 55, 58);
    static readonly int[] GR3 = BB.HSV(78, 46, 80);   // hot core, ~4-6 px total on the model

    // Fool pathway brand leather (#754B26 = HSV 28/68/46) — straps and stitching only.
    static readonly int[] LE0 = BB.HSV(24, 70, 26);
    static readonly int[] LE1 = BB.HSV(28, 68, 46);

    static readonly int[] VD = BB.HSV(270, 34, 9);    // "nothing is in there", darker than FL0
    static readonly int[] TR = new int[] { 0, 0, 0, 0 };

    // ---- painters ----

    static int[] F(int i)
    {
        if (i < 0) i = 0;
        if (i > 4) i = 4;
        return FLR[i];
    }

    // Entity convention: top/front brighter than bottom/back. Side falloff of one step so the
    // slab reads as a curved body instead of a flat card.
    static void FleshFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.18 ? 4 : (t < 0.46 ? 3 : (t < 0.78 ? 2 : 1));
            for (int u = 0; u < w; u++)
                put(u, v, F(w > 2 && (u == 0 || u == w - 1) ? i - 1 : i));
        }
    }

    static void FleshSide(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.20 ? 3 : (t < 0.66 ? 2 : 1);
            for (int u = 0; u < w; u++) put(u, v, F(i));
        }
    }

    static void FleshBack(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int i = t < 0.16 ? 2 : (t < 0.60 ? 1 : 0);
            for (int u = 0; u < w; u++) put(u, v, F(i));
        }
    }

    static void FleshTop(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int i = (v < h / 2) ? 4 : 3;
                if (w > 2 && (u == 0 || u == w - 1)) i -= 1;
                put(u, v, F(i));
            }
    }

    static void PaintFlesh(string p)
    {
        BB.Paint(p + "_n", FleshFront);
        BB.Paint(p + "_s", FleshBack);
        BB.Paint(p + "_e", FleshSide);
        BB.Paint(p + "_w", FleshSide);
        BB.Paint(p + "_u", FleshTop);
        BB.Paint(p + "_d", BB.Flat(FL0));
    }

    // One continuous dashed seam down the flank: this body is patchwork. A dashed line reads
    // as stitching; scattered dots on a face this big would read as noise.
    static void TorsoSide(BB.Put put, int w, int h)
    {
        FleshSide(put, w, h);
        for (int v = 1; v < h - 1; v++) if (v % 2 == 0) put(w / 2, v, LE0);
    }

    static void NeckSide(BB.Put put, int w, int h)
    {
        FleshSide(put, w, h);
        for (int u = 0; u < w; u++) if (u % 2 == 0) put(u, h / 2, F(1));   // one crease ring
    }

    static void SkullSide(BB.Put put, int w, int h)
    {
        FleshSide(put, w, h);
        for (int u = 0; u < w; u++) put(u, 0, KE1);                        // temple ridge
        for (int u = 0; u < w; u++) if ((u * 3) % 5 == 0) put(u, 1, KE0);  // its shadow
    }

    static void SkullTop(BB.Put put, int w, int h)
    {
        FleshTop(put, w, h);
        int c = w / 2;
        for (int v = 0; v < h; v++)
        {
            put(c, v, KE1);                                               // low dorsal crest
            if (v % 2 == 0 && c + 1 < w) put(c + 1, v, KE0);
        }
    }

    // Revealed only when the mask splits: an empty socket with a green fissure in it. This is
    // the payoff of the attack animation, so it is the highest-contrast patch on the model.
    static void SkullFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++) put(u, v, VD);
        int c = w / 2;
        for (int v = 1; v < h - 1; v++)
        {
            int[] col = (v < h / 2) ? GR2 : GR1;
            put(c - 1, v, col);
            put(c, v, col);
        }
        put(c - 1, h / 2, GR3);
        put(c, h / 2, GR3);
        for (int v = 1; v < h - 1; v += 2) { put(c - 2, v, GR0); put(c + 1, v, GR0); }
    }

    // Half of the blank face plate. The seam sits on the INNER edge (u = w-1); the right half
    // reuses this painter mirrored, which puts its seam at u = 0 so the two meet in the middle.
    // The eye pit is one solid 2 px mark — a pit split by a lighter pixel would read as two.
    static void MaskFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.20 ? KE3 : (t < 0.55 ? KE2 : (t < 0.85 ? KE1 : KE0));
            for (int u = 0; u < w; u++) put(u, v, c);
        }
        for (int v = 0; v < h; v++) put(w - 1, v, KE0);                   // centre seam
        int py = h / 2 - 1;
        put(1, py, KE0); put(1, py + 1, KE0);                             // eye pit
        put(w - 2, h - 2, KE0);                                           // hairline crack off the seam
    }

    static void MaskInner(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++) put(u, v, (u + v) % 3 == 0 ? GR0 : VD);
    }

    // The mutated pituitary gland: a lit sphere, not a pillow. One top-left-to-bottom-right
    // gradient plus two constant-step veins.
    static void Gland(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                double du = w <= 1 ? 0 : (double)u / (w - 1);
                double dv = h <= 1 ? 0 : (double)v / (h - 1);
                double d = du * 0.55 + dv * 0.45;
                put(u, v, d < 0.26 ? GR3 : (d < 0.54 ? GR2 : (d < 0.82 ? GR1 : GR0)));
            }
        for (int v = 0; v < h; v++)
        {
            int u = h <= 1 ? 0 : (v * (w - 1)) / (h - 1);
            put(u, v, GR0);
        }
    }

    // A stolen face, on a plane with transparent corners. variant 0 = caught mid-scream,
    // variant 1 = sewn shut. Differentiated by the SHAPE of the dark mass, not by detail:
    // anything finer than this is a mixel at 5x6.
    static BB.Painter StolenFace(int variant)
    {
        return delegate(BB.Put put, int w, int h)
        {
            for (int v = 0; v < h; v++)
                for (int u = 0; u < w; u++)
                {
                    bool corner = (u == 0 || u == w - 1) && (v == 0 || v == h - 1);
                    if (corner) { put(u, v, TR); continue; }               // rounded silhouette
                    if (v == 0 || v == h - 1) { put(u, v, LE1); continue; } // stitched rim
                    double t = (double)v / (h - 1);
                    put(u, v, t < 0.34 ? SK3 : (t < 0.66 ? SK2 : SK1));
                }
            int ey = h / 3;
            put(1, ey, SKD); put(w - 2, ey, SKD);                          // two eyes, 2 px apart
            if (variant == 0)
            {
                for (int u = 1; u < w - 1; u++) put(u, h - 2, SKD);         // open mouth
                put(w / 2, h - 3, SKD);
            }
            else
            {
                for (int u = 1; u < w - 1; u++) put(u, h - 2, (u % 2 == 0) ? SKD : SK0);
            }
            put(w - 2, 1, GR1);                                            // weeping at the seam
        };
    }

    // The skin cape: human hide, stitched in panels, hem cut to hard ragged teeth. Hard cuts,
    // never an alpha fade — a soft gradient is not a Minecraft silhouette.
    static void Shroud(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
        {
            double t = h <= 1 ? 0 : (double)v / (h - 1);
            int[] c = t < 0.20 ? SK2 : (t < 0.58 ? SK1 : SK0);
            for (int u = 0; u < w; u++) put(u, v, c);
        }
        for (int u = 2; u < w; u += 4)
            for (int v = 1; v < h - 4; v++) if (v % 2 == 0) put(u, v, LE0);
        for (int u = 0; u < w; u++)
        {
            int m = (u * 5) % 7;
            int cut = m < 3 ? 3 : (m < 5 ? 2 : 1);
            for (int k = 0; k < cut; k++) put(u, h - 1 - k, TR);
            put(u, h - 1 - cut, SK0);
        }
        for (int u = 1; u < w - 1; u += 3) put(u, 0, GR0);
    }

    // Two talons readable from the front, a third carried on the side faces.
    static void HandFront(BB.Put put, int w, int h)
    {
        FleshFront(put, w, h);
        int y0 = h / 2;
        for (int u = 0; u < w; u++)
        {
            bool claw = (u % 2 == 0);
            for (int v = y0; v < h; v++)
            {
                double t = (double)(v - y0) / Math.Max(1, h - 1 - y0);
                put(u, v, claw ? (t < 0.4 ? KE2 : (t < 0.78 ? KE1 : KE0)) : F(1));
            }
        }
    }

    static void HandSide(BB.Put put, int w, int h)
    {
        FleshSide(put, w, h);
        for (int u = 0; u < w; u++) put(u, h - 1, KE1);
    }

    // Toe splits run along the length of the foot, so the painter does not depend on which
    // texture row happens to be the front.
    static void FootTop(BB.Put put, int w, int h)
    {
        FleshTop(put, w, h);
        if (w >= 3)
            for (int v = 0; v < h; v++)
            {
                put(w / 3, v, F(1));
                put(w - 1 - w / 3, v, F(1));
            }
    }

    static void FootFront(BB.Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
                put(u, v, (u % 2 == 0) ? (v == 0 ? KE2 : KE1) : F(1));
    }

    public static string Build(string outDir, string scratchDir)
    {
        BB.Init(64, 64);

        // ---- geometry ----
        // 32 units tall = 2.0 blocks, which is the WITCH's own height; Options.Scale 1.35 in
        // Mobs/fool.yml then takes visual AND hitbox to ~2.7 together. Never add scale= to
        // model{} on top of that — it multiplies.
        // Left-side limbs own the UV; right-side ones reuse it via BB.Faces6.
        string chest = BB.Box("chest", "ch", new double[] { -4, 17, -3 }, new double[] { 4, 25, 2 });
        string gut = BB.Box("gut", "gt", new double[] { -3, 13, -2.5 }, new double[] { 3, 18, 2.5 });
        string neck = BB.Box("neck", "nk", new double[] { -1.5, 24, -1.5 }, new double[] { 1.5, 27, 1.5 });
        string skull = BB.Box("skull", "sk", new double[] { -3.5, 26, -6 }, new double[] { 3.5, 32, 2 });
        string gland = BB.Box("gland", "gl", new double[] { -2.5, 27, 1 }, new double[] { 2.5, 31, 6 });

        // The face plate, in two halves that hinge open at the centre seam. Held 0.2 clear of
        // the skull's front face so the two never z-fight.
        string maskL = BB.Box("mask_plate_left", "msk", new double[] { 0, 26, -7.2 }, new double[] { 4, 32, -6.2 });
        string maskR = BB.Box("mask_plate_right", "mskr", new double[] { -4, 26, -7.2 }, new double[] { 0, 32, -6.2 });

        string armL = BB.Box("arm_left", "arm", new double[] { 4, 17, -1.5 }, new double[] { 7, 25, 1.5 });
        string armR = BB.Cube("arm_right", new double[] { -7, 17, -1.5 }, new double[] { -4, 25, 1.5 }, BB.Faces6("arm"));
        string fraL = BB.Box("forearm_left", "fra", new double[] { 4, 8, -1.5 }, new double[] { 7, 17, 1.5 });
        string fraR = BB.Cube("forearm_right", new double[] { -7, 8, -1.5 }, new double[] { -4, 17, 1.5 }, BB.Faces6("fra"));
        string hndL = BB.Box("hand_left", "hnd", new double[] { 4, 4, -1 }, new double[] { 7, 8, 1 });
        string hndR = BB.Cube("hand_right", new double[] { -7, 4, -1 }, new double[] { -4, 8, 1 }, BB.Faces6("hnd"));

        // Digitigrade Z-leg by offset, not rotation: thigh forward, shin behind it, foot
        // forward again. Keeps every bone's rest rotation at 0 so animation values are absolute.
        string thgL = BB.Box("thigh_left", "thg", new double[] { 1, 8, -3 }, new double[] { 5, 14, 1 });
        string thgR = BB.Cube("thigh_right", new double[] { -5, 8, -3 }, new double[] { -1, 14, 1 }, BB.Faces6("thg"));
        string shnL = BB.Box("shin_left", "shn", new double[] { 1.5, 2, 0 }, new double[] { 4.5, 9, 3 });
        string shnR = BB.Cube("shin_right", new double[] { -4.5, 2, 0 }, new double[] { -1.5, 9, 3 }, BB.Faces6("shn"));
        string ftL = BB.Box("foot_left", "ft", new double[] { 1.5, 0, -4 }, new double[] { 4.5, 2, 2 });
        string ftR = BB.Cube("foot_right", new double[] { -4.5, 0, -4 }, new double[] { -1.5, 2, 2 }, BB.Faces6("ft"));

        // Trophies. Two planes at different depths so the overlapping strip reads as skins
        // layered over each other rather than z-fighting.
        string faceA = BB.Plane("face_stolen_a", "fca", new double[] { -4.5, 19, -3.2 }, new double[] { 0.5, 25, -3.2 }, "z");
        string faceB = BB.Plane("face_stolen_b", "fcb", new double[] { -0.5, 17.5, -3.5 }, new double[] { 4.5, 23.5, -3.5 }, "z");
        string shroud = BB.Plane("shroud", "shr", new double[] { -5, 12, 3 }, new double[] { 5, 26, 3 }, "z");

        // ---- texture ----
        PaintFlesh("ch"); BB.Paint("ch_e", TorsoSide); BB.Paint("ch_w", TorsoSide);
        PaintFlesh("gt"); BB.Paint("gt_e", TorsoSide); BB.Paint("gt_w", TorsoSide);
        PaintFlesh("nk"); BB.Paint("nk_e", NeckSide); BB.Paint("nk_w", NeckSide);
        PaintFlesh("sk");
        BB.Paint("sk_n", SkullFront); BB.Paint("sk_u", SkullTop);
        BB.Paint("sk_e", SkullSide); BB.Paint("sk_w", SkullSide);

        BB.Paint("gl_n", Gland); BB.Paint("gl_s", Gland);
        BB.Paint("gl_e", Gland); BB.Paint("gl_w", Gland);
        BB.Paint("gl_u", Gland); BB.Paint("gl_d", Gland);

        BB.Paint("msk_n", MaskFront); BB.Paint("msk_s", MaskInner);
        BB.Paint("msk_e", BB.Flat(KE1)); BB.Paint("msk_w", BB.Flat(KE0));
        BB.Paint("msk_u", BB.Flat(KE2)); BB.Paint("msk_d", BB.Flat(KE0));
        BB.Paint("mskr_n", MaskFront, true); BB.Paint("mskr_s", MaskInner, true);
        BB.Paint("mskr_e", BB.Flat(KE0)); BB.Paint("mskr_w", BB.Flat(KE1));
        BB.Paint("mskr_u", BB.Flat(KE2)); BB.Paint("mskr_d", BB.Flat(KE0));

        PaintFlesh("arm");
        PaintFlesh("fra");
        PaintFlesh("hnd"); BB.Paint("hnd_n", HandFront);
        BB.Paint("hnd_e", HandSide); BB.Paint("hnd_w", HandSide); BB.Paint("hnd_d", BB.Flat(KE0));
        PaintFlesh("thg");
        PaintFlesh("shn");
        PaintFlesh("ft"); BB.Paint("ft_u", FootTop); BB.Paint("ft_n", FootFront);

        BB.Paint("fca_n", StolenFace(0)); BB.Paint("fca_s", BB.Flat(SK0));
        BB.Paint("fcb_n", StolenFace(1)); BB.Paint("fcb_s", BB.Flat(SK0));
        BB.Paint("shr_n", Shroud); BB.Paint("shr_s", Shroud, true);

        // ---- bones ----
        // hips and torso are SIBLINGS under body: the torso carries a -14 rest lean, and if
        // the legs hung off it the hunch would tip the feet off the ground.
        var outliner = new List<object> {
            BB.Group("body", new double[] { 0, 1, 0 }, BB.Kids(
                BB.Group("hips", new double[] { 0, 16, 0 }, BB.Kids(
                    gut,
                    BB.Group("leg_left", new double[] { 3, 14, -1 }, BB.Kids(
                        thgL,
                        BB.Group("shin_left", new double[] { 3, 9, 1.5 }, BB.Kids(
                            shnL,
                            BB.Group("foot_left", new double[] { 3, 2, 1 }, BB.Kids(ftL))
                        ))
                    )),
                    BB.Group("leg_right", new double[] { -3, 14, -1 }, BB.Kids(
                        thgR,
                        BB.Group("shin_right", new double[] { -3, 9, 1.5 }, BB.Kids(
                            shnR,
                            BB.Group("foot_right", new double[] { -3, 2, 1 }, BB.Kids(ftR))
                        ))
                    ))
                )),
                BB.Group("torso", new double[] { 0, 18, 0 }, BB.Kids(
                    chest,
                    BB.Group("face_a", new double[] { -2, 25, -3.2 }, BB.Kids(faceA)),
                    BB.Group("face_b", new double[] { 2, 23.5, -3.5 }, BB.Kids(faceB)),
                    BB.Group("shroud", new double[] { 0, 25.5, 3 }, BB.Kids(shroud), new double[] { 14, 0, 0 }),
                    BB.Group("neck", new double[] { 0, 24, 0 }, BB.Kids(
                        neck,
                        BB.Group("hi_head", new double[] { 0, 26, 0 }, BB.Kids(
                            skull,
                            BB.Group("gland", new double[] { 0, 29, 3.5 }, BB.Kids(gland)),
                            BB.Group("mask_left", new double[] { 0, 29, -6.7 }, BB.Kids(maskL)),
                            BB.Group("mask_right", new double[] { 0, 29, -6.7 }, BB.Kids(maskR))
                        ))
                    ), new double[] { 14, 0, 0 }),
                    BB.Group("arm_left", new double[] { 5.5, 24.5, 0 }, BB.Kids(
                        armL,
                        BB.Group("forearm_left", new double[] { 5.5, 17, 0 }, BB.Kids(
                            fraL,
                            BB.Group("hand_left", new double[] { 5.5, 8, 0 }, BB.Kids(hndL))
                        ))
                    ), new double[] { 16, 0, 0 }),
                    BB.Group("arm_right", new double[] { -5.5, 24.5, 0 }, BB.Kids(
                        armR,
                        BB.Group("forearm_right", new double[] { -5.5, 17, 0 }, BB.Kids(
                            fraR,
                            BB.Group("hand_right", new double[] { -5.5, 8, 0 }, BB.Kids(hndR))
                        ))
                    ), new double[] { 16, 0, 0 })
                ), new double[] { -14, 0, 0 })
            ))
        };

        var anims = new List<object>();
        Animate(anims);
        return BB.Write("fool_thousand_faced_hunter_6", outDir, scratchDir, outliner, anims, null);
    }

    static double[] K(double t, double a, double b, double c)
    {
        return new double[] { t, a, b, c };
    }

    // ---- animations ----
    // Every "once" animation starts and ends on all-zeros, and idle's first and last keyframes
    // are all-zeros too, so a state{} cast hands back to the idle loop without a pop.
    static void Animate(List<object> anims)
    {
        Idle(anims);
        Walk(anims);
        Reactions(anims);
        Casts(anims);
    }

    // ---- idle (BUILT-IN, and the eternal base layer — it runs UNDER everything else, so it
    // holds only what is true in every state: slow breath, the gland throbbing on its own
    // period, the stolen faces shifting, fingers twitching). No head yaw: hi_head already
    // tracks its target and a keyed turn would add to it and slide the mask off whoever it is
    // looking at. ----
    static void Idle(List<object> anims)
    {
        var idle = BB.Pose();
        idle["torso"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(1.8, -2, 0, 0.8), K(3.6, 1, 0, -0.8), K(6, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(1.8, 0, 0.35, 0), K(3.6, 0, -0.15, 0), K(6, 0, 0, 0)));
        idle["neck"] = BB.Rot(K(0, 0, 0, 0), K(1.6, 2, 0, 0), K(3.4, -1, 0, 0), K(6, 0, 0, 0));
        // Predator head: short snap, long hold. Never a slow sweep.
        idle["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(2.0, 0, 0, 0), K(2.15, -9, 0, -2), K(3.4, -9, 0, -2),
                                 K(3.55, 4, 0, 1.5), K(4.8, 4, 0, 1.5), K(4.95, 0, 0, 0), K(6, 0, 0, 0));
        // The gland is the only thing on the model with its own pulse — 8 beats per idle cycle,
        // deliberately out of step with the breath so the two never look linked.
        idle["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.75, 0, 0.3, 0.15), K(1.5, 0, 0, 0), K(2.25, 0, 0.3, 0.15),
                               K(3, 0, 0, 0), K(3.75, 0, 0.3, 0.15), K(4.5, 0, 0, 0), K(5.25, 0, 0.3, 0.15),
                               K(6, 0, 0, 0));
        // The plate is never quite still — 2.5 deg of play, opposite phases, so the face looks
        // like it is being worn rather than being part of the skull.
        idle["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(2.4, 0, 2.5, 0), K(4.6, 0, -1.2, 0), K(6, 0, 0, 0));
        idle["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(2.9, 0, -2.5, 0), K(5.1, 0, 1.2, 0), K(6, 0, 0, 0));
        idle["face_a"] = BB.Rot(K(0, 0, 0, 0), K(2.2, 0, 0, 2), K(4.4, 0, 0, -1), K(6, 0, 0, 0));
        idle["face_b"] = BB.Rot(K(0, 0, 0, 0), K(2.7, 0, 0, -2.4), K(5.0, 0, 0, 1.2), K(6, 0, 0, 0));
        idle["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(2.1, -3, 0, 2), K(4.3, 3, 0, -1), K(6, 0, 0, 0));
        idle["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(2.6, 3, 0, -2), K(4.8, -2, 0, 1), K(6, 0, 0, 0));
        idle["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(2.35, -4, 0, 0), K(4.55, 3, 0, 0), K(6, 0, 0, 0));
        idle["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(2.85, 4, 0, 0), K(5.05, -3, 0, 0), K(6, 0, 0, 0));
        idle["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(1.2, -7, 0, 0), K(1.35, 3, 0, 0), K(3.1, 3, 0, 0),
                                   K(3.25, -6, 0, 0), K(6, 0, 0, 0));
        idle["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(2.05, 5, 0, 0), K(2.2, -4, 0, 0), K(4.4, -4, 0, 0),
                                    K(4.55, 4, 0, 0), K(6, 0, 0, 0));
        idle["shroud"] = BB.Rot(K(0, 0, 0, 0), K(2.4, -2.5, 0, 2), K(4.8, 2, 0, -1.5), K(6, 0, 0, 0));
        idle["hips"] = BB.Rot(K(0, 0, 0, 0), K(2.6, 0, 0, 1), K(5.0, 0, 0, -1), K(6, 0, 0, 0));
        idle["shin_left"] = BB.Rot(K(0, 0, 0, 0), K(3.0, -1.5, 0, 0), K(6, 0, 0, 0));
        idle["shin_right"] = BB.Rot(K(0, 0, 0, 0), K(3.4, -1.5, 0, 0), K(6, 0, 0, 0));
        anims.Add(BB.Anim("idle", "loop", 6, idle));
    }

    // ---- walk (BUILT-IN). A stalk, not a march: 1 s per two steps, the torso carried 3 deg
    // lower than rest, the head held level while the hips bob under it. The right leg runs the
    // same curve offset by half a cycle; child bones (shin, foot, forearm, hand, shroud) lag
    // the parent by ~0.15 s with slightly larger amplitude, which is what makes it organic. ----
    static void Walk(List<object> anims)
    {
        var walk = BB.Pose();
        walk["hips"] = BB.Merge(
            BB.Pos(K(0, 0, 0, 0), K(0.25, 0, 0.35, 0), K(0.5, 0, 0, 0), K(0.75, 0, 0.35, 0), K(1, 0, 0, 0)),
            BB.Rot(K(0, 0, 4, 1.5), K(0.5, 0, -4, -1.5), K(1, 0, 4, 1.5)));
        walk["torso"] = BB.Merge(
            BB.Rot(K(0, -3, -4, -1), K(0.5, -3, 4, 1), K(1, -3, -4, -1)),
            BB.Pos(K(0, 0, 0, -0.3), K(1, 0, 0, -0.3)));
        walk["neck"] = BB.Rot(K(0, 2, 0, 0.8), K(0.5, 2, 0, -0.8), K(1, 2, 0, 0.8));
        walk["hi_head"] = BB.Rot(K(0, -2, 0, 0), K(0.5, -4, 0, 0), K(1, -2, 0, 0));

        walk["leg_left"] = BB.Rot(K(0, 24, 0, 0), K(0.3, 2, 0, 0), K(0.5, -18, 0, 0), K(0.62, -6, 0, 0),
                                  K(0.8, 28, 0, 0), K(1, 24, 0, 0));
        walk["shin_left"] = BB.Rot(K(0, -12, 0, 0), K(0.3, -4, 0, 0), K(0.5, -8, 0, 0), K(0.65, -50, 0, 0),
                                   K(0.82, -28, 0, 0), K(1, -12, 0, 0));
        walk["foot_left"] = BB.Rot(K(0, 10, 0, 0), K(0.3, 0, 0, 0), K(0.52, -22, 0, 0), K(0.7, 16, 0, 0),
                                   K(1, 10, 0, 0));
        walk["leg_right"] = BB.Rot(K(0, -18, 0, 0), K(0.12, -6, 0, 0), K(0.3, 28, 0, 0), K(0.5, 24, 0, 0),
                                   K(0.8, 2, 0, 0), K(1, -18, 0, 0));
        walk["shin_right"] = BB.Rot(K(0, -8, 0, 0), K(0.15, -50, 0, 0), K(0.32, -28, 0, 0), K(0.5, -12, 0, 0),
                                    K(0.8, -4, 0, 0), K(1, -8, 0, 0));
        walk["foot_right"] = BB.Rot(K(0, -22, 0, 0), K(0.2, 16, 0, 0), K(0.5, 10, 0, 0), K(0.8, 0, 0, 0),
                                    K(1, -22, 0, 0));

        walk["arm_left"] = BB.Rot(K(0, -14, 0, 2), K(0.5, 14, 0, 4), K(1, -14, 0, 2));
        walk["arm_right"] = BB.Rot(K(0, 14, 0, -2), K(0.5, -14, 0, -4), K(1, 14, 0, -2));
        walk["forearm_left"] = BB.Rot(K(0, -8, 0, 0), K(0.15, -12, 0, 0), K(0.65, 10, 0, 0), K(1, -8, 0, 0));
        walk["forearm_right"] = BB.Rot(K(0, 10, 0, 0), K(0.15, 12, 0, 0), K(0.65, -10, 0, 0), K(1, 10, 0, 0));
        walk["hand_left"] = BB.Rot(K(0, -10, 0, 0), K(0.3, -14, 0, 0), K(0.8, 8, 0, 0), K(1, -10, 0, 0));
        walk["hand_right"] = BB.Rot(K(0, 8, 0, 0), K(0.3, 12, 0, 0), K(0.8, -10, 0, 0), K(1, 8, 0, 0));
        walk["shroud"] = BB.Rot(K(0, -12, 0, 2), K(0.5, -16, 0, -2), K(1, -12, 0, 2));
        walk["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.25, 0, -0.2, 0), K(0.5, 0, 0, 0), K(0.75, 0, -0.2, 0),
                               K(1, 0, 0, 0));
        walk["face_a"] = BB.Rot(K(0, 0, 0, 1.5), K(0.5, 0, 0, -1.5), K(1, 0, 0, 1.5));
        walk["face_b"] = BB.Rot(K(0, 0, 0, -1.5), K(0.5, 0, 0, 1.5), K(1, 0, 0, -1.5));
        anims.Add(BB.Anim("walk", "loop", 1, walk));
    }

    // ---- spawn / death / damage (all BUILT-IN) ----
    static void Reactions(List<object> anims)
    {
        // spawn: it arrives PUTTING ITS FACE ON. Sunk and folded double, both mask halves hanging
        // wide open, then it rises, the plate SNAPS shut at 1.05, and the head comes up on the
        // last beat — that snap is the moment it decides you are prey.
        var spawn = BB.Pose();
        spawn["body"] = BB.Pos(K(0, 0, -16, 0), K(0.5, 0, -6, 0), K(0.95, 0, 0.6, 0), K(1.2, 0, -0.2, 0),
                               K(1.4, 0, 0, 0));
        spawn["hips"] = BB.Merge(
            BB.Pos(K(0, 0, -4, 0), K(0.6, 0, -2, 0), K(1.4, 0, 0, 0)),
            BB.Rot(K(0, 8, 0, 0), K(0.6, 5, 0, 0), K(1.4, 0, 0, 0)));
        spawn["leg_left"] = BB.Rot(K(0, 46, 0, 0), K(0.55, 30, 0, 0), K(1.0, -6, 0, 0), K(1.4, 0, 0, 0));
        spawn["leg_right"] = BB.Rot(K(0, 46, 0, 0), K(0.6, 28, 0, 0), K(1.05, -6, 0, 0), K(1.4, 0, 0, 0));
        spawn["shin_left"] = BB.Rot(K(0, -72, 0, 0), K(0.55, -44, 0, 0), K(1.0, 6, 0, 0), K(1.4, 0, 0, 0));
        spawn["shin_right"] = BB.Rot(K(0, -72, 0, 0), K(0.6, -42, 0, 0), K(1.05, 6, 0, 0), K(1.4, 0, 0, 0));
        spawn["foot_left"] = BB.Rot(K(0, 26, 0, 0), K(0.6, 14, 0, 0), K(1.4, 0, 0, 0));
        spawn["foot_right"] = BB.Rot(K(0, 26, 0, 0), K(0.65, 12, 0, 0), K(1.4, 0, 0, 0));
        spawn["torso"] = BB.Rot(K(0, -30, 0, 0), K(0.55, -20, 0, 0), K(1.0, 6, 0, 0), K(1.4, 0, 0, 0));
        spawn["neck"] = BB.Rot(K(0, -22, 0, 0), K(0.6, -16, 0, 0), K(1.1, 5, 0, 0), K(1.4, 0, 0, 0));
        spawn["hi_head"] = BB.Rot(K(0, -34, 0, 0), K(0.7, -28, 0, 0), K(1.15, 12, 0, 0), K(1.4, 0, 0, 0));
        spawn["mask_left"] = BB.Rot(K(0, 0, 74, 0), K(0.75, 0, 68, 0), K(1.05, 0, 4, 0), K(1.4, 0, 0, 0));
        spawn["mask_right"] = BB.Rot(K(0, 0, -74, 0), K(0.75, 0, -68, 0), K(1.05, 0, -4, 0), K(1.4, 0, 0, 0));
        spawn["arm_left"] = BB.Rot(K(0, -34, 0, 20), K(0.7, -22, 0, 12), K(1.15, 8, 0, -3), K(1.4, 0, 0, 0));
        spawn["arm_right"] = BB.Rot(K(0, -34, 0, -20), K(0.7, -22, 0, -12), K(1.15, 8, 0, 3), K(1.4, 0, 0, 0));
        spawn["forearm_left"] = BB.Rot(K(0, -58, 0, 0), K(0.8, -36, 0, 0), K(1.4, 0, 0, 0));
        spawn["forearm_right"] = BB.Rot(K(0, -58, 0, 0), K(0.8, -36, 0, 0), K(1.4, 0, 0, 0));
        spawn["hand_left"] = BB.Rot(K(0, -40, 0, 0), K(0.85, -24, 0, 0), K(1.4, 0, 0, 0));
        spawn["hand_right"] = BB.Rot(K(0, -40, 0, 0), K(0.85, -24, 0, 0), K(1.4, 0, 0, 0));
        spawn["shroud"] = BB.Rot(K(0, 30, 0, 0), K(0.7, 20, 0, 0), K(1.15, -8, 0, 0), K(1.4, 0, 0, 0));
        spawn["gland"] = BB.Pos(K(0, 0, -1.2, 0), K(0.8, 0, 0.4, 0), K(1.4, 0, 0, 0));
        anims.Add(BB.Anim("spawn", "once", 1.4, spawn));

        // death ("hold", so the corpse stays down). One jolt, then the legs give out, the plate
        // falls open for good, and the stolen faces come UNSTITCHED and drop off the chest — the
        // trophies leaving is what reads as the death, not the fall itself. The body sinks
        // rather than toppling flat, so it never intersects the ground plane.
        var death = BB.Pose();
        death["body"] = BB.Pos(K(0, 0, 0, 0), K(0.25, 0, 0.5, 0.5), K(0.8, 0, -2.5, 0), K(1.8, 0, -10, -1));
        death["hips"] = BB.Rot(K(0, 0, 0, 0), K(0.25, -8, 0, 4), K(0.9, 16, 0, -6), K(1.8, 24, 0, -8));
        death["leg_left"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 18, 0, 0), K(1.0, 52, 0, 10), K(1.8, 62, 0, 14));
        death["leg_right"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 14, 0, 0), K(1.05, 48, 0, -10), K(1.8, 58, 0, -14));
        death["shin_left"] = BB.Rot(K(0, 0, 0, 0), K(0.4, -20, 0, 0), K(1.1, -62, 0, 0), K(1.8, -74, 0, 0));
        death["shin_right"] = BB.Rot(K(0, 0, 0, 0), K(0.45, -18, 0, 0), K(1.15, -58, 0, 0), K(1.8, -70, 0, 0));
        death["foot_left"] = BB.Rot(K(0, 0, 0, 0), K(1.2, 24, 0, 0), K(1.8, 30, 0, 0));
        death["foot_right"] = BB.Rot(K(0, 0, 0, 0), K(1.25, 22, 0, 0), K(1.8, 28, 0, 0));
        death["torso"] = BB.Rot(K(0, 0, 0, 0), K(0.25, 14, 0, -5), K(0.85, -26, 0, 7), K(1.8, -40, 0, 9));
        death["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.25, 18, 0, 0), K(0.9, -20, 0, 4), K(1.8, -30, 0, 6));
        death["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.25, 24, 0, 0), K(0.95, -26, 0, 8), K(1.8, -36, 0, 12));
        death["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 0, 30, 0), K(1.0, 0, 78, 0), K(1.8, 0, 86, 0));
        death["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.35, 0, -30, 0), K(1.05, 0, -78, 0), K(1.8, 0, -86, 0));
        death["face_a"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.5, 22, 0, -6), K(1.1, 78, 0, -14), K(1.8, 92, 0, -18)),
            BB.Pos(K(0, 0, 0, 0), K(0.5, 0, -0.6, -0.4), K(1.1, -0.5, -4.5, -1.2), K(1.8, -0.8, -8, -1.6)));
        death["face_b"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.6, 18, 0, 8), K(1.2, 74, 0, 16), K(1.8, 88, 0, 20)),
            BB.Pos(K(0, 0, 0, 0), K(0.6, 0, -0.5, -0.3), K(1.2, 0.6, -4, -1.1), K(1.8, 0.9, -7.5, -1.5)));
        death["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.3, -34, 0, 24), K(1.0, 30, 0, 12), K(1.8, 44, 0, 8));
        death["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.35, -30, 0, -24), K(1.05, 32, 0, -12), K(1.8, 46, 0, -8));
        death["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.45, -26, 0, 0), K(1.15, 26, 0, 0), K(1.8, 38, 0, 0));
        death["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.5, -24, 0, 0), K(1.2, 24, 0, 0), K(1.8, 36, 0, 0));
        death["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.55, -20, 0, 0), K(1.8, 30, 0, 0));
        death["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.6, -18, 0, 0), K(1.8, 28, 0, 0));
        death["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.4, 22, 0, 0), K(1.1, -14, 0, 0), K(1.8, -22, 0, 0));
        // The gland goes out last: it rides up out of the collapsing skull and stops.
        death["gland"] = BB.Merge(
            BB.Pos(K(0, 0, 0, 0), K(0.5, 0, 1.2, -0.6), K(1.8, 0, 3.5, -1.5)),
            BB.Rot(K(0, 0, 0, 0), K(1.8, -22, 0, 0)));
        anims.Add(BB.Anim("death", "hold", 1.8, death));

        // damage (fires on EVERY hit). Deliberately tiny — the big evasive lurch is "recoil",
        // which MythicMobs fires only when Danger Intuition actually procs, and when both play
        // at once the larger one has to win.
        var dmg = BB.Pose();
        dmg["torso"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.09, 6, 0, -3), K(0.2, -2, 0, 1), K(0.3, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.09, 0, 0, 0.6), K(0.3, 0, 0, 0)));
        dmg["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.09, 8, 0, -3), K(0.3, 0, 0, 0));
        dmg["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.11, -10, 0, 6), K(0.3, 0, 0, 0));
        dmg["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.11, -10, 0, -6), K(0.3, 0, 0, 0));
        dmg["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.1, 0, 12, 0), K(0.3, 0, 0, 0));
        dmg["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.1, 0, -12, 0), K(0.3, 0, 0, 0));
        dmg["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.13, 10, 0, 0), K(0.3, 0, 0, 0));
        dmg["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 8, 0, 0), K(0.3, 0, 0, 0));
        dmg["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.14, 7, 0, 0), K(0.3, 0, 0, 0));
        anims.Add(BB.Anim("damage", "once", 0.3, dmg));
    }

    // ---- cast animations ----
    // None of these are built-in. "attack" comes from state{} on the mob (~onAttack); the other
    // three are fired from inside the matching metaskill in mythic-pack/Skills/fool.yml, so the
    // gesture always matches the skill that actually rolled out of kitcast. All are "once": a
    // "loop" fired from state{} would never stop on its own.
    //
    // Recurring motif: the blank plate SPLITS. Wide open on the melee lunge, a flinch-crack on
    // recoil, a hairline on the ranged casts. Both halves hinge about Y at the centre seam —
    // mask_left's geometry runs +X from the pivot so POSITIVE Y opens it forward, mask_right's
    // runs -X so it is negative.
    static void Casts(List<object> anims)
    {
        // attack: the lunge. Coil, then drop into it — and the face comes APART on the strike
        // frame, so what actually lands the hit is the green socket behind the mask.
        var atk = BB.Pose();
        atk["body"] = BB.Pos(K(0, 0, 0, 0), K(0.16, 0, -0.4, 0.6), K(0.3, 0, 0, -1.6), K(0.5, 0, 0, -0.6),
                             K(0.8, 0, 0, 0));
        atk["torso"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.16, 7, 0, 0), K(0.3, -15, 0, -4), K(0.5, -6, 0, -1), K(0.8, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.3, 0, -0.4, -0.8), K(0.8, 0, 0, 0)));
        atk["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -6, 0, 0), K(0.3, 12, 0, 0), K(0.8, 0, 0, 0));
        atk["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -8, 0, 0), K(0.3, 11, 0, 0), K(0.52, 4, 0, 0),
                                K(0.8, 0, 0, 0));
        atk["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 0, 58, 0), K(0.4, 0, 64, 0), K(0.56, 0, 6, 0),
                                  K(0.8, 0, 0, 0));
        atk["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 0, -58, 0), K(0.4, 0, -64, 0), K(0.56, 0, -6, 0),
                                   K(0.8, 0, 0, 0));
        atk["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.3, 0, 0.5, 0.7), K(0.8, 0, 0, 0));
        // The claw hand whips across the body: X forward, Z positive to carry a -X limb inward.
        atk["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -38, 0, -16), K(0.32, 66, 0, 26), K(0.48, 30, 0, 12),
                                  K(0.8, 0, 0, 0));
        atk["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -30, 0, 0), K(0.36, 44, 0, 0), K(0.54, 18, 0, 0),
                                      K(0.8, 0, 0, 0));
        atk["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.24, -24, 0, 0), K(0.4, 36, 0, 0), K(0.8, 0, 0, 0));
        atk["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 22, 0, 10), K(0.34, -26, 0, 14), K(0.8, 0, 0, 0));
        atk["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 18, 0, 0), K(0.4, -20, 0, 0), K(0.8, 0, 0, 0));
        atk["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.26, 14, 0, 0), K(0.46, -16, 0, 0), K(0.8, 0, 0, 0));
        atk["hips"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.16, 0, 6, 0), K(0.32, 0, -8, 0), K(0.8, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.16, 0, -0.8, 0), K(0.32, 0, 0.2, 0), K(0.8, 0, 0, 0)));
        atk["leg_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 12, 0, 0), K(0.32, -10, 0, 0), K(0.8, 0, 0, 0));
        atk["leg_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 10, 0, 0), K(0.32, -8, 0, 0), K(0.8, 0, 0, 0));
        atk["shin_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -16, 0, 0), K(0.36, 8, 0, 0), K(0.8, 0, 0, 0));
        atk["shin_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -14, 0, 0), K(0.36, 7, 0, 0), K(0.8, 0, 0, 0));
        atk["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.2, 12, 0, 0), K(0.42, -18, 0, 0), K(0.8, 0, 0, 0));
        atk["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 10, 0, -3), K(0.44, -8, 0, 2), K(0.8, 0, 0, 0));
        atk["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.26, 9, 0, 3), K(0.48, -7, 0, -2), K(0.8, 0, 0, 0));
        anims.Add(BB.Anim("attack", "once", 0.8, atk));

        // cast_bolt (MA_Fool_S7_AirBullet): the projectile is instant, so the gesture is too —
        // a right-hand palm thrust with a body twist behind it and a hairline mask crack on
        // the release.
        var bolt = BB.Pose();
        bolt["torso"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 0, -10, 0), K(0.34, -4, 12, 0), K(0.7, 0, 0, 0));
        bolt["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 4, 0, 0), K(0.34, -5, 0, 0), K(0.7, 0, 0, 0));
        bolt["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 5, 0, 0), K(0.34, -7, 0, 0), K(0.7, 0, 0, 0));
        bolt["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -44, 0, -12), K(0.34, 88, 0, 6), K(0.5, 62, 0, 4),
                                   K(0.7, 0, 0, 0));
        bolt["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -36, 0, 0), K(0.38, 30, 0, 0), K(0.7, 0, 0, 0));
        bolt["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.24, -28, 0, 0), K(0.4, 24, 0, 0), K(0.7, 0, 0, 0));
        bolt["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 14, 0, 8), K(0.34, -12, 0, 5), K(0.7, 0, 0, 0));
        bolt["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 12, 0, 0), K(0.4, -10, 0, 0), K(0.7, 0, 0, 0));
        bolt["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.34, 0, 16, 0), K(0.52, 0, 4, 0), K(0.7, 0, 0, 0));
        bolt["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.34, 0, -16, 0), K(0.52, 0, -4, 0), K(0.7, 0, 0, 0));
        bolt["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.34, 0, 0.35, 0.4), K(0.7, 0, 0, 0));
        bolt["hips"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 0, 8, 0), K(0.34, 0, -6, 0), K(0.7, 0, 0, 0));
        bolt["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.24, 8, 0, -4), K(0.44, -10, 0, 3), K(0.7, 0, 0, 0));
        bolt["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.26, 6, 0, -2), K(0.7, 0, 0, 0));
        bolt["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.3, 5, 0, 2), K(0.7, 0, 0, 0));
        anims.Add(BB.Anim("cast_bolt", "once", 0.7, bolt));

        // cast_blade (MA_Fool_S8_PaperCutter): the projectile is a cutting edge, so it comes off
        // the CLAWS — the shortest animation in the set. Left arm winds up and out (Z positive
        // raises a +X limb), then whips down and across (Z negative carries it inward).
        var blade = BB.Pose();
        blade["torso"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 0, 12, 0), K(0.28, -3, -15, 0), K(0.6, 0, 0, 0));
        blade["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.28, -5, 0, 0), K(0.6, 0, 0, 0));
        blade["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 4, 0, 3), K(0.28, -7, 0, -3), K(0.6, 0, 0, 0));
        blade["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.12, -46, 0, 42), K(0.28, 52, 0, -26), K(0.42, 22, 0, -10),
                                   K(0.6, 0, 0, 0));
        blade["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -34, 0, 0), K(0.32, 46, 0, 0), K(0.46, 18, 0, 0),
                                       K(0.6, 0, 0, 0));
        blade["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -26, 0, 0), K(0.34, 38, 0, 0), K(0.6, 0, 0, 0));
        blade["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.14, 18, 0, -8), K(0.3, -20, 0, -12), K(0.6, 0, 0, 0));
        blade["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 14, 0, 0), K(0.36, -16, 0, 0), K(0.6, 0, 0, 0));
        blade["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.28, 0, 12, 0), K(0.6, 0, 0, 0));
        blade["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.28, 0, -12, 0), K(0.6, 0, 0, 0));
        blade["hips"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 0, -10, 0), K(0.28, 0, 8, 0), K(0.6, 0, 0, 0));
        blade["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 10, 0, 5), K(0.38, -12, 0, -4), K(0.6, 0, 0, 0));
        blade["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.2, 7, 0, 3), K(0.6, 0, 0, 0));
        blade["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.24, 6, 0, -3), K(0.6, 0, 0, 0));
        anims.Add(BB.Anim("cast_blade", "once", 0.6, blade));

        // recoil (MA_Fool_S9_DangerIntuition): the dodge. The body is YANKED back first and the
        // cape, faces and gland catch up a beat later — that lag is what makes it read as being
        // moved rather than posing. The mask cracks in the flinch and shuts again immediately.
        var rec = BB.Pose();
        rec["body"] = BB.Pos(K(0, 0, 0, 0), K(0.1, 0, 0.5, 2.4), K(0.3, 0, 0.2, 0.9), K(0.5, 0, 0, 0));
        rec["torso"] = BB.Rot(K(0, 0, 0, 0), K(0.1, 20, 0, -5), K(0.3, 7, 0, 2), K(0.5, 0, 0, 0));
        rec["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 14, 0, 0), K(0.32, 4, 0, 0), K(0.5, 0, 0, 0));
        rec["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 16, 0, -4), K(0.5, 0, 0, 0));
        rec["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 0, 34, 0), K(0.3, 0, 8, 0), K(0.5, 0, 0, 0));
        rec["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 0, -34, 0), K(0.3, 0, -8, 0), K(0.5, 0, 0, 0));
        rec["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.18, 0, 0.2, -0.8), K(0.5, 0, 0, 0));
        rec["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.14, -30, 0, 30), K(0.34, -10, 0, 12), K(0.5, 0, 0, 0));
        rec["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.14, -30, 0, -30), K(0.34, -10, 0, -12), K(0.5, 0, 0, 0));
        rec["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -26, 0, 0), K(0.5, 0, 0, 0));
        rec["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -26, 0, 0), K(0.5, 0, 0, 0));
        rec["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -20, 0, 0), K(0.5, 0, 0, 0));
        rec["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -20, 0, 0), K(0.5, 0, 0, 0));
        rec["hips"] = BB.Merge(
            BB.Rot(K(0, 0, 0, 0), K(0.1, -10, 0, 4), K(0.5, 0, 0, 0)),
            BB.Pos(K(0, 0, 0, 0), K(0.1, 0, -0.6, 0), K(0.5, 0, 0, 0)));
        rec["leg_left"] = BB.Rot(K(0, 0, 0, 0), K(0.12, 26, 0, 0), K(0.34, 8, 0, 0), K(0.5, 0, 0, 0));
        rec["leg_right"] = BB.Rot(K(0, 0, 0, 0), K(0.12, -18, 0, 0), K(0.34, -6, 0, 0), K(0.5, 0, 0, 0));
        rec["shin_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -30, 0, 0), K(0.5, 0, 0, 0));
        rec["shin_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 14, 0, 0), K(0.5, 0, 0, 0));
        rec["foot_left"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 18, 0, 0), K(0.5, 0, 0, 0));
        rec["foot_right"] = BB.Rot(K(0, 0, 0, 0), K(0.18, -14, 0, 0), K(0.5, 0, 0, 0));
        rec["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 30, 0, 0), K(0.38, -8, 0, 0), K(0.5, 0, 0, 0));
        rec["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.18, 16, 0, -4), K(0.38, -5, 0, 1), K(0.5, 0, 0, 0));
        rec["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.22, 14, 0, 4), K(0.42, -4, 0, -1), K(0.5, 0, 0, 0));
        anims.Add(BB.Anim("recoil", "once", 0.5, rec));

        // flame_jump (MA_Fool_S7_FlameJump): the point-blank escape. Same direction as recoil,
        // completely different shape — coil deep on the legs first, blow out backward and up off
        // the burst, then absorb the landing on bent knees.
        var fj = BB.Pose();
        fj["body"] = BB.Pos(K(0, 0, 0, 0), K(0.16, 0, -1.6, -0.4), K(0.4, 0, 3.2, 2.8), K(0.64, 0, 0.8, 1.4),
                            K(0.9, 0, 0, 0));
        fj["hips"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -12, 0, 0), K(0.4, 22, 0, 0), K(0.9, 0, 0, 0));
        fj["leg_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 40, 0, 0), K(0.4, -22, 0, 0), K(0.66, 26, 0, 0),
                                K(0.9, 0, 0, 0));
        fj["leg_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 38, 0, 0), K(0.42, -20, 0, 0), K(0.68, 24, 0, 0),
                                 K(0.9, 0, 0, 0));
        fj["shin_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -58, 0, 0), K(0.4, 10, 0, 0), K(0.66, -40, 0, 0),
                                 K(0.9, 0, 0, 0));
        fj["shin_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -56, 0, 0), K(0.42, 8, 0, 0), K(0.68, -38, 0, 0),
                                  K(0.9, 0, 0, 0));
        fj["foot_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 22, 0, 0), K(0.4, -26, 0, 0), K(0.68, 16, 0, 0),
                                 K(0.9, 0, 0, 0));
        fj["foot_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, 20, 0, 0), K(0.42, -24, 0, 0), K(0.7, 14, 0, 0),
                                  K(0.9, 0, 0, 0));
        fj["torso"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -16, 0, 0), K(0.4, 28, 0, -5), K(0.64, 10, 0, 2),
                             K(0.9, 0, 0, 0));
        fj["neck"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -12, 0, 0), K(0.4, 20, 0, 0), K(0.9, 0, 0, 0));
        fj["hi_head"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -14, 0, 0), K(0.4, 24, 0, 0), K(0.9, 0, 0, 0));
        fj["mask_left"] = BB.Rot(K(0, 0, 0, 0), K(0.4, 0, 26, 0), K(0.64, 0, 6, 0), K(0.9, 0, 0, 0));
        fj["mask_right"] = BB.Rot(K(0, 0, 0, 0), K(0.4, 0, -26, 0), K(0.64, 0, -6, 0), K(0.9, 0, 0, 0));
        fj["gland"] = BB.Pos(K(0, 0, 0, 0), K(0.4, 0, -0.6, -1.0), K(0.9, 0, 0, 0));
        fj["arm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -24, 0, 14), K(0.4, -64, 0, 44), K(0.64, -24, 0, 18),
                                K(0.9, 0, 0, 0));
        fj["arm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.16, -24, 0, -14), K(0.4, -64, 0, -44), K(0.64, -24, 0, -18),
                                 K(0.9, 0, 0, 0));
        fj["forearm_left"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -20, 0, 0), K(0.46, -48, 0, 0), K(0.9, 0, 0, 0));
        fj["forearm_right"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -20, 0, 0), K(0.46, -48, 0, 0), K(0.9, 0, 0, 0));
        fj["hand_left"] = BB.Rot(K(0, 0, 0, 0), K(0.24, -16, 0, 0), K(0.5, -34, 0, 0), K(0.9, 0, 0, 0));
        fj["hand_right"] = BB.Rot(K(0, 0, 0, 0), K(0.24, -16, 0, 0), K(0.5, -34, 0, 0), K(0.9, 0, 0, 0));
        fj["shroud"] = BB.Rot(K(0, 0, 0, 0), K(0.2, -16, 0, 0), K(0.44, 46, 0, 0), K(0.7, 12, 0, 0),
                              K(0.9, 0, 0, 0));
        fj["face_a"] = BB.Rot(K(0, 0, 0, 0), K(0.22, -8, 0, 3), K(0.46, 20, 0, -4), K(0.9, 0, 0, 0));
        fj["face_b"] = BB.Rot(K(0, 0, 0, 0), K(0.26, -7, 0, -3), K(0.5, 18, 0, 4), K(0.9, 0, 0, 0));
        anims.Add(BB.Anim("flame_jump", "once", 0.9, fj));
    }
}
