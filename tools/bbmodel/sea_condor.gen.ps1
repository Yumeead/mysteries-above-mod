# sea_condor — генератор моделі "Морський Кондор з Драконячим Оком" (Tyrant, Sequence 6).
#
# Джерело правди для src/main/resources/bettermodel/models/sea_condor.bbmodel — правки
# роблять ТУТ і перегенеровують, руками .bbmodel не редагують
# (див. .claude/rules/bettermodel-models.md).
#
# Самодостатній: власний растеризатор текстури + серіалізація, engine.cs зі скіла bbmodel
# йому не потрібен (модель зроблена до розділення скіла на engine + <model>.cs).
#
#   powershell -File tools\bbmodel\sea_condor.gen.ps1 -Out src\main\resources\bettermodel\models
#
# Імена анімацій — контракт із BetterModel, не косметика:
#   * idle — МАХИ КРИЛАМИ. Так, махи під іменем "idle", і це не помилка. На MC 1.21.11
#     (NMS-модуль BetterModel v1_21_R7) "у польоті" для будь-якого моба, що не FlyingAnimal,
#     означає Mob.isNoAi() — клас FlyingMob Mojang прибрав. Тобто фантом зі своїм AI для
#     BetterModel НЕ летить: idle_fly і walk_fly на ньому мертві, walk теж (навігацією він
#     не рухається). Лишається idle — єдина вбудована без предиката, вона грає завжди.
#     Кондор у повітрі 100% часу, тож його "завжди" — це змахи.
#   * perch (сидить), glide (ширяє), takeoff (зліт) вбудованими НЕ є і самі не запускаються —
#     лише через state{} з MythicMobs, коли з'явиться механіка посадки.
#     ⚠️ Не називай нічого idle_fly: щойно механіка посадки зробить setAI(false), isNoAi()
#     стане true, і BetterModel НЕГАЙНО перемкне моба на анімацію з цим іменем.
param(
    [string]$Out = (Join-Path $PSScriptRoot '..\..\src\main\resources\bettermodel\models'),
    [string]$Scratch = $env:TEMP
)

$ErrorActionPreference = 'Stop'
foreach ($d in @($Out, $Scratch)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}

$src = @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Web.Script.Serialization;

public static class SeaCondor
{
    const int W = 64, H = 64;
    static int[][][] img;
    static string[][] claimed;
    static Dictionary<string, int[]> regions = new Dictionary<string, int[]>();
    static List<object> elements = new List<object>();
    static Dictionary<string, string> groupIds = new Dictionary<string, string>();

    delegate void Put(int u, int v, int[] c);
    delegate void Painter(Put put, int w, int h);

    static int[] C(int r, int g, int b) { return new int[] { r, g, b, 255 }; }

    static readonly int[] F0 = C(11, 13, 24), F1 = C(22, 27, 48), F2 = C(36, 46, 74), F3 = C(58, 76, 104), F4 = C(87, 112, 139);
    static readonly int[] A1 = C(35, 68, 92), A2 = C(51, 99, 127), A3 = C(79, 139, 163), A4 = C(121, 183, 199);
    static readonly int[] K0 = C(60, 38, 47), K1 = C(79, 52, 64), K2 = C(109, 74, 81), K3 = C(140, 99, 96), K4 = C(169, 126, 112);
    static readonly int[] B1 = C(23, 26, 36), B2 = C(43, 49, 64), B3 = C(63, 74, 92), B4 = C(91, 106, 124), BP = C(154, 161, 171);
    static readonly int[] EO = C(23, 13, 6), EA = C(193, 118, 31), EH = C(240, 196, 82), ES = C(110, 48, 14);

    static void R(string name, int x, int y, int w, int h)
    {
        if (x < 0 || y < 0 || x + w > W || y + h > H) throw new Exception("out of bounds: " + name);
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++)
            {
                if (claimed[yy][xx] != null) throw new Exception("UV overlap: " + name + " vs " + claimed[yy][xx] + " at " + xx + "," + yy);
                claimed[yy][xx] = name;
            }
        regions[name] = new int[] { x, y, w, h };
    }

    static void Paint(string name, Painter fn) { Paint(name, fn, false); }
    static void Paint(string name, Painter fn, bool mirror)
    {
        int[] r = regions[name]; int x = r[0], y = r[1], w = r[2], h = r[3];
        Put put = delegate(int u, int v, int[] c)
        {
            int uu = mirror ? (w - 1 - u) : u;
            img[y + v][x + uu] = c;
        };
        fn(put, w, h);
    }

    static void Fill(Put put, int w, int h, int[] c)
    {
        for (int v = 0; v < h; v++) for (int u = 0; u < w; u++) put(u, v, c);
    }

    // ---------- painters ----------
    static void PBodyU(Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int[] c = F3;
                if (v <= 1 && u >= 1 && u <= 4) c = F4;
                if (v >= 2 && v <= 4 && (u == 2 || u == 3)) c = F4;
                if (v >= 3 && v <= 6 && (u == 0 || u == 5)) c = F2;
                if (v >= 10) c = (u == 2 || u == 3) ? F3 : F2;
                if (v == 13) c = (u == 2 || u == 3) ? F2 : F1;
                put(u, v, c);
            }
        put(1, 7, F2); put(4, 8, F2); put(2, 10, F2);
    }

    static void PBodyD(Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
                put(u, v, (u == 0 || u == 5 || v >= 11) ? F0 : F1);
        put(2, 4, F0); put(3, 8, F0);
    }

    static void PBodyN(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, F4); put(u, 1, F3); put(u, 2, F3);
            put(u, 3, (u >= 1 && u <= 4) ? F3 : F2);
            put(u, 4, F2);
        }
    }

    static void PBodyS(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        { put(u, 0, F2); put(u, 1, F1); put(u, 2, F1); put(u, 3, F0); put(u, 4, F0); }
    }

    static void PBodySide(Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int[] c = F2;
                if (v == 0) c = F3;
                if (v == 4) c = F1;
                put(u, v, c);
            }
        put(3, 1, F1); put(7, 2, F1); put(11, 1, F1);
    }

    static void PHeadU(Put put, int w, int h)
    {
        Fill(put, w, h, F3);
        for (int u = 0; u < w; u++) { put(u, 0, F4); put(u, 4, F2); }
        put(2, 1, F4); put(2, 2, F4);
    }

    static void PHeadD(Put put, int w, int h)
    {
        Fill(put, w, h, F1);
        for (int v = 0; v < h; v++) { put(0, v, F0); put(4, v, F0); }
    }

    static void PHeadN(Put put, int w, int h)
    {
        Fill(put, w, h, F2);
        for (int u = 0; u < w; u++) { put(u, 0, F3); put(u, 1, F3); }
        for (int v = 2; v <= 4; v++) for (int u = 1; u <= 3; u++) put(u, v, F1);
        put(0, 4, F1); put(4, 4, F1);
    }

    static void PHeadS(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        { put(u, 0, F2); put(u, 1, F1); put(u, 2, F1); put(u, 3, F1); put(u, 4, F0); }
    }

    static void PHeadSide(Put put, int w, int h)
    {
        Fill(put, w, h, F2);
        for (int u = 0; u < w; u++) { put(u, 0, F3); put(u, 4, F1); }
        put(2, 1, A1);
    }

    static void PNeckU(Put put, int w, int h)
    {
        Fill(put, w, h, K3);
        put(1, 0, K4); put(2, 0, K4);
        for (int u = 0; u < w; u++) put(u, 3, K2);
    }

    static void PNeckD(Put put, int w, int h)
    {
        Fill(put, w, h, K1);
        for (int v = 0; v < h; v++) { put(0, v, K0); put(3, v, K0); }
    }

    static void PNeckN(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, (u == 1 || u == 2) ? K4 : K3);
            put(u, 1, K3);
            put(u, 2, (u == 1 || u == 2) ? K1 : K2);
            put(u, 3, K2);
        }
    }

    static void PNeckS(Put put, int w, int h) { Fill(put, w, h, K1); }

    static void PNeckSide(Put put, int w, int h)
    {
        Fill(put, w, h, K2);
        for (int u = 0; u < w; u++) { put(u, 0, K3); put(u, 3, K1); }
        put(1, 1, K1); put(2, 2, K1);
    }

    static int TailTip(int u)
    {
        int f = u / 2;
        return f == 2 ? 8 : (f == 1 || f == 3) ? 7 : 6;
    }

    static void PTailU(Put put, int w, int h)
    {
        for (int u = 0; u < 10; u++)
        {
            int T = TailTip(u);
            for (int v = 0; v <= T; v++)
            {
                int[] c;
                if (v == 0) c = F2;
                else if (v <= 2) c = (u == 4 || u == 5) ? F4 : F3;
                else if (v == T) c = A3;
                else if (v == T - 1) c = A2;
                else if (v <= 4) c = F3;
                else c = F2;
                put(u, v, c);
            }
        }
        for (int u = 2; u <= 8; u += 2)
        {
            int T = TailTip(u);
            for (int v = 3; v <= T - 1; v++) put(u, v, F1);
        }
    }

    static void PTailD(Put put, int w, int h)
    {
        for (int u = 0; u < 10; u++)
        {
            int T = TailTip(u);
            for (int v = 0; v <= T; v++)
            {
                int[] c;
                if (v == 0) c = F0;
                else if (v == T) c = A2;
                else if (v == T - 1) c = A1;
                else c = F1;
                put(u, v, c);
            }
        }
        for (int u = 2; u <= 8; u += 2)
        {
            int T = TailTip(u);
            for (int v = 3; v <= T - 1; v++) put(u, v, F0);
        }
    }

    static void PBeakU(Put put, int w, int h)
    {
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++)
            {
                int[] c = B3;
                if (u == 1) c = B4;
                if (v == 3 && u != 1) c = B2;
                put(u, v, c);
            }
    }

    static void PBeakD(Put put, int w, int h)
    {
        Fill(put, w, h, B1);
        for (int v = 0; v < h; v++) put(1, v, B2);
    }

    static void PBeakN(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++) { put(u, 0, u == 1 ? B4 : B3); put(u, 1, B2); }
    }

    static void PBeakS(Put put, int w, int h) { Fill(put, w, h, B2); }

    static void PBeakSide(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++) { put(u, 0, B3); put(u, 1, B2); }
        put(2, 0, B1); put(0, 1, B1);
    }

    static void PHookU(Put put, int w, int h) { Fill(put, w, h, BP); }
    static void PHookD(Put put, int w, int h) { Fill(put, w, h, B2); }
    static void PHookN(Put put, int w, int h) { for (int u = 0; u < w; u++) { put(u, 0, BP); put(u, 1, B4); } }
    static void PHookS(Put put, int w, int h) { Fill(put, w, h, B2); }
    static void PHookSide(Put put, int w, int h) { put(0, 0, BP); put(0, 1, B4); }

    static readonly string[] EYE_ROWS = { " ooo ", "ohsao", "oasao", " ooo " };

    static void PEyeN(Put put, int w, int h)
    {
        for (int v = 0; v < EYE_ROWS.Length; v++)
            for (int u = 0; u < 5; u++)
            {
                char ch = EYE_ROWS[v][u];
                if (ch == ' ') continue;
                int[] c = ch == 'o' ? EO : ch == 'h' ? EH : ch == 's' ? ES : EA;
                put(u, v, c);
            }
    }

    static void PEyeS(Put put, int w, int h)
    {
        for (int v = 0; v < EYE_ROWS.Length; v++)
            for (int u = 0; u < 5; u++)
                if (EYE_ROWS[v][u] != ' ') put(u, v, v == 1 ? F2 : F1);
    }

    static void PGillOut(Put put, int w, int h)
    {
        for (int u = 0; u < 6; u++)
        {
            bool slat = (u == 1 || u == 4);
            put(u, 0, slat ? BP : K3);
            put(u, 1, slat ? B4 : K2);
            put(u, 2, slat ? B4 : K1);
        }
        put(1, 3, B3); put(4, 3, B3);
    }

    static void PGillIn(Put put, int w, int h)
    {
        for (int u = 0; u < 6; u++)
            for (int v = 0; v < 3; v++)
                put(u, v, (u == 1 || u == 4) ? B3 : K1);
        put(1, 3, B2); put(4, 3, B2);
    }

    static void PWingIU(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, F4); put(u, 1, F3); put(u, 2, F3);
            put(u, 3, F2); put(u, 4, A1); put(u, 5, A2);
        }
        put(3, 2, F2); put(7, 2, F2); put(11, 2, F2);
        put(4, 5, F1); put(8, 5, F1);
        put(11, 4, A2); put(12, 4, A3); put(13, 4, A3); put(13, 5, A3);
        for (int v = 0; v < 6; v++) put(0, v, F2);
        put(0, 0, F3);
    }

    static void PWingID(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, F2); put(u, 1, F1); put(u, 2, F1); put(u, 3, F1);
            put(u, 4, F1); put(u, 5, A1);
        }
        put(4, 5, F0); put(8, 5, F0);
        put(12, 5, A2); put(13, 5, A2); put(13, 4, A1);
        for (int v = 0; v < 6; v++) put(0, v, F0);
    }

    static void PWingIN(Put put, int w, int h) { Fill(put, w, h, F4); }

    static void PWingIS(Put put, int w, int h)
    {
        Fill(put, w, h, A1);
        put(4, 0, F1); put(8, 0, F1); put(12, 0, F1);
    }

    static void PWingOU(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, F4); put(u, 1, F3);
            put(u, 2, u >= 8 ? A2 : F2);
            put(u, 3, u >= 8 ? A3 : A2);
        }
        put(11, 1, A2); put(12, 1, A3); put(12, 0, A3);
        put(2, 3, F1); put(5, 3, F1);
        put(12, 3, A4);
        for (int v = 0; v < 4; v++) put(0, v, F2);
    }

    static void PWingOD(Put put, int w, int h)
    {
        for (int u = 0; u < w; u++)
        {
            put(u, 0, F2); put(u, 1, F1);
            put(u, 2, u >= 9 ? A1 : F1);
            put(u, 3, u >= 9 ? A2 : A1);
        }
        put(2, 3, F0); put(5, 3, F0);
        put(12, 3, A3);
        for (int v = 0; v < 4; v++) put(0, v, F1);
    }

    static void PWingON(Put put, int w, int h) { Fill(put, w, h, F4); }

    static void PWingOS(Put put, int w, int h)
    {
        Fill(put, w, h, A2);
        put(2, 0, F1); put(5, 0, F1); put(8, 0, F1);
        put(11, 0, A3); put(12, 0, A3);
    }

    static Painter LegCol(int[][] cols)
    {
        return delegate(Put put, int w, int h)
        {
            for (int v = 0; v < h; v++) put(0, v, cols[v]);
        };
    }

    static Painter Cap(int[] c)
    {
        return delegate(Put put, int w, int h) { Fill(put, w, h, c); };
    }

    static void PFootU(Put put, int w, int h)
    {
        put(1, 0, K3);
        put(0, 1, K3); put(1, 1, K4); put(2, 1, K3);
        put(0, 2, K2); put(1, 2, K3); put(2, 2, K2);
        put(0, 3, K1); put(1, 3, K2); put(2, 3, K1);
    }

    static void PFootD(Put put, int w, int h)
    {
        put(1, 0, K1);
        put(0, 1, K1); put(1, 1, K2); put(2, 1, K1);
        put(0, 2, K1); put(1, 2, K1); put(2, 2, K1);
        put(0, 3, K0); put(1, 3, K1); put(2, 3, K0);
    }

    // ---------- model helpers ----------
    static Dictionary<string, object> Fc(string reg)
    {
        int[] r = regions[reg];
        var d = new Dictionary<string, object>();
        d["uv"] = new int[] { r[0], r[1], r[0] + r[2], r[1] + r[3] };
        d["texture"] = 0;
        return d;
    }

    static Dictionary<string, object> NB()
    {
        var d = new Dictionary<string, object>();
        d["uv"] = new int[] { 0, 0, 0, 0 };
        d["texture"] = null;
        return d;
    }

    static string Cube(string name, double[] f, double[] t, Dictionary<string, object> faces, double[] origin, double[] rot)
    {
        var e = new Dictionary<string, object>();
        string id = Guid.NewGuid().ToString();
        e["name"] = name; e["box_uv"] = false; e["rescale"] = false; e["locked"] = false;
        e["render_order"] = "default"; e["allow_mirror_modeling"] = true;
        e["from"] = f; e["to"] = t; e["autouv"] = 0; e["color"] = 0;
        e["visibility"] = true;
        e["origin"] = origin != null ? origin : new double[] { 0, 0, 0 };
        if (rot != null) e["rotation"] = rot;
        e["faces"] = faces; e["type"] = "cube"; e["uuid"] = id;
        elements.Add(e);
        return id;
    }

    static Dictionary<string, object> Faces6(string p)
    {
        var d = new Dictionary<string, object>();
        d["north"] = Fc(p + "_n"); d["east"] = Fc(p + "_e"); d["south"] = Fc(p + "_s");
        d["west"] = Fc(p + "_w"); d["up"] = Fc(p + "_u"); d["down"] = Fc(p + "_d");
        return d;
    }

    static Dictionary<string, object> FacesUD(string p)
    {
        var d = new Dictionary<string, object>();
        d["north"] = NB(); d["east"] = NB(); d["south"] = NB(); d["west"] = NB();
        d["up"] = Fc(p + "_u"); d["down"] = Fc(p + "_d");
        return d;
    }

    static Dictionary<string, object> Group(string name, double[] origin, List<object> children, double[] rot)
    {
        var g = new Dictionary<string, object>();
        g["name"] = name; g["origin"] = origin; g["color"] = 0; g["uuid"] = Guid.NewGuid().ToString();
        g["export"] = true; g["mirror_uv"] = false; g["isOpen"] = true;
        g["locked"] = false; g["visibility"] = true; g["autouv"] = 0;
        if (rot != null) g["rotation"] = rot;
        g["children"] = children;
        groupIds[name] = (string)g["uuid"];
        return g;
    }

    // ---------- animation helpers ----------
    static string Num(double d)
    {
        return d.ToString(System.Globalization.CultureInfo.InvariantCulture);
    }

    static Dictionary<string, object> KF(string channel, double time, double x, double y, double z)
    {
        var dp = new Dictionary<string, object>();
        dp["x"] = Num(x); dp["y"] = Num(y); dp["z"] = Num(z);
        var k = new Dictionary<string, object>();
        k["channel"] = channel;
        k["data_points"] = new object[] { dp };
        k["uuid"] = Guid.NewGuid().ToString();
        k["time"] = time;
        k["color"] = -1;
        k["interpolation"] = "catmullrom";
        return k;
    }

    // each entry: {time, x, y, z}
    static List<object> Rot(params double[][] kfs)
    {
        var l = new List<object>();
        foreach (var k in kfs) l.Add(KF("rotation", k[0], k[1], k[2], k[3]));
        return l;
    }

    static List<object> Pos(params double[][] kfs)
    {
        var l = new List<object>();
        foreach (var k in kfs) l.Add(KF("position", k[0], k[1], k[2], k[3]));
        return l;
    }

    static List<object> Merge(List<object> a, List<object> b)
    {
        var l = new List<object>(a); l.AddRange(b); return l;
    }

    static readonly string[] ALL_BONES = {
        "body", "neck", "head", "tail",
        "wing_left", "wing_left_tip", "wing_right", "wing_right_tip",
        "leg_left", "leg_right"
    };

    // every animation must key every bone, otherwise a previously played
    // animation leaks its pose into this one (Blockbench preview and BetterModel alike)
    static void EnsureAll(Dictionary<string, List<object>> d)
    {
        foreach (string b in ALL_BONES)
            if (!d.ContainsKey(b)) d[b] = Rot(new double[] { 0, 0, 0, 0 });

        foreach (string b in ALL_BONES)
        {
            bool hasRot = false, hasPos = false;
            foreach (var k in d[b])
            {
                string ch = (string)((Dictionary<string, object>)k)["channel"];
                if (ch == "rotation") hasRot = true;
                if (ch == "position") hasPos = true;
            }
            if (!hasRot) d[b] = Merge(d[b], Rot(new double[] { 0, 0, 0, 0 }));
            if (!hasPos) d[b] = Merge(d[b], Pos(new double[] { 0, 0, 0, 0 }));
        }
    }

    static Dictionary<string, object> Anim(string name, string loop, double length, Dictionary<string, List<object>> byBone)
    {
        EnsureAll(byBone);
        var animators = new Dictionary<string, object>();
        foreach (var kv in byBone)
        {
            var a = new Dictionary<string, object>();
            a["name"] = kv.Key;
            a["type"] = "bone";
            a["keyframes"] = kv.Value;
            animators[groupIds[kv.Key]] = a;
        }
        var an = new Dictionary<string, object>();
        an["uuid"] = Guid.NewGuid().ToString();
        an["name"] = name;
        an["loop"] = loop;
        an["override"] = true;
        an["length"] = length;
        an["snapping"] = 24;
        an["selected"] = false;
        an["anim_time_update"] = "";
        an["blend_weight"] = "";
        an["start_delay"] = "";
        an["loop_delay"] = "";
        an["animators"] = animators;
        return an;
    }

    static byte[] PngOf(int w, int h, Func<int, int, int[]> pix)
    {
        using (var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb))
        {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    int[] p = pix(x, y);
                    bmp.SetPixel(x, y, Color.FromArgb(p[3], p[0], p[1], p[2]));
                }
            using (var ms = new MemoryStream())
            {
                bmp.Save(ms, ImageFormat.Png);
                return ms.ToArray();
            }
        }
    }

    public static string Generate(string outDir, string scratchDir)
    {
        img = new int[H][][];
        claimed = new string[H][];
        for (int y = 0; y < H; y++)
        {
            img[y] = new int[W][];
            claimed[y] = new string[W];
            for (int x = 0; x < W; x++) img[y][x] = new int[] { 0, 0, 0, 0 };
        }

        // UV layout (1 px per model unit)
        R("body_u", 14, 0, 6, 14); R("body_d", 20, 0, 6, 14);
        R("body_w", 0, 14, 14, 5); R("body_n", 14, 14, 6, 5); R("body_e", 20, 14, 14, 5); R("body_s", 34, 14, 6, 5);
        R("head_u", 45, 0, 5, 5); R("head_d", 50, 0, 5, 5);
        R("head_w", 40, 5, 5, 5); R("head_n", 45, 5, 5, 5); R("head_e", 50, 5, 5, 5); R("head_s", 55, 5, 5, 5);
        R("neck_u", 44, 10, 4, 4); R("neck_d", 48, 10, 4, 4);
        R("neck_w", 40, 14, 4, 4); R("neck_n", 44, 14, 4, 4); R("neck_e", 48, 14, 4, 4); R("neck_s", 52, 14, 4, 4);
        R("tail_u", 0, 44, 10, 9); R("tail_d", 10, 44, 10, 9);
        R("beak_u", 4, 19, 3, 4); R("beak_d", 7, 19, 3, 4);
        R("beak_w", 0, 23, 4, 2); R("beak_n", 4, 23, 3, 2); R("beak_e", 7, 23, 4, 2); R("beak_s", 11, 23, 3, 2);
        R("hook_u", 15, 19, 2, 1); R("hook_d", 17, 19, 2, 1);
        R("hook_w", 14, 20, 1, 2); R("hook_n", 15, 20, 2, 2); R("hook_e", 17, 20, 1, 2); R("hook_s", 18, 20, 2, 2);
        R("eye_n", 20, 19, 5, 4); R("eye_s", 25, 19, 5, 4);
        R("gill_out", 30, 19, 6, 4); R("gill_in", 36, 19, 6, 4);
        R("wli_u", 0, 26, 14, 6); R("wli_d", 14, 26, 14, 6);
        R("wli_n", 0, 32, 14, 1); R("wli_s", 0, 33, 14, 1); R("wli_e", 14, 32, 6, 1); R("wli_w", 14, 33, 6, 1);
        R("wlo_u", 28, 26, 13, 4); R("wlo_d", 28, 30, 13, 4);
        R("wlo_n", 41, 26, 13, 1); R("wlo_s", 41, 27, 13, 1); R("wlo_e", 41, 28, 4, 1); R("wlo_w", 45, 28, 4, 1);
        R("leg_n", 50, 28, 1, 7); R("leg_e", 51, 28, 1, 7); R("leg_s", 52, 28, 1, 7); R("leg_w", 53, 28, 1, 7);
        R("leg_u", 50, 35, 1, 1); R("leg_d", 51, 35, 1, 1);
        R("foot_u", 56, 24, 3, 4); R("foot_d", 59, 24, 3, 4);
        R("wri_u", 0, 36, 14, 6); R("wri_d", 14, 36, 14, 6);
        R("wri_n", 0, 42, 14, 1); R("wri_s", 0, 43, 14, 1); R("wri_e", 14, 42, 6, 1); R("wri_w", 14, 43, 6, 1);
        R("wro_u", 28, 36, 13, 4); R("wro_d", 28, 40, 13, 4);
        R("wro_n", 41, 36, 13, 1); R("wro_s", 41, 37, 13, 1); R("wro_e", 41, 38, 4, 1); R("wro_w", 45, 38, 4, 1);

        // painting
        Paint("body_u", PBodyU); Paint("body_d", PBodyD);
        Paint("body_n", PBodyN); Paint("body_s", PBodyS);
        Paint("body_e", PBodySide); Paint("body_w", PBodySide);
        Paint("head_u", PHeadU); Paint("head_d", PHeadD);
        Paint("head_n", PHeadN); Paint("head_s", PHeadS);
        Paint("head_e", PHeadSide); Paint("head_w", PHeadSide);
        Paint("neck_u", PNeckU); Paint("neck_d", PNeckD);
        Paint("neck_n", PNeckN); Paint("neck_s", PNeckS);
        Paint("neck_e", PNeckSide); Paint("neck_w", PNeckSide);
        Paint("tail_u", PTailU); Paint("tail_d", PTailD);
        Paint("beak_u", PBeakU); Paint("beak_d", PBeakD);
        Paint("beak_n", PBeakN); Paint("beak_s", PBeakS);
        Paint("beak_e", PBeakSide); Paint("beak_w", PBeakSide);
        Paint("hook_u", PHookU); Paint("hook_d", PHookD);
        Paint("hook_n", PHookN); Paint("hook_s", PHookS);
        Paint("hook_e", PHookSide); Paint("hook_w", PHookSide);
        Paint("eye_n", PEyeN); Paint("eye_s", PEyeS);
        Paint("gill_out", PGillOut); Paint("gill_in", PGillIn);

        Paint("wli_u", PWingIU); Paint("wli_d", PWingID);
        Paint("wli_n", PWingIN); Paint("wli_s", PWingIS);
        Paint("wli_e", Cap(F2)); Paint("wli_w", Cap(F1));
        Paint("wlo_u", PWingOU); Paint("wlo_d", PWingOD);
        Paint("wlo_n", PWingON); Paint("wlo_s", PWingOS);
        Paint("wlo_e", Cap(A3)); Paint("wlo_w", Cap(F2));

        Paint("wri_u", PWingIU, true); Paint("wri_d", PWingID, true);
        Paint("wri_n", PWingIN, true); Paint("wri_s", PWingIS, true);
        Paint("wri_e", Cap(F1)); Paint("wri_w", Cap(F2));
        Paint("wro_u", PWingOU, true); Paint("wro_d", PWingOD, true);
        Paint("wro_n", PWingON, true); Paint("wro_s", PWingOS, true);
        Paint("wro_e", Cap(F2)); Paint("wro_w", Cap(A3));

        Paint("leg_n", LegCol(new int[][] { F1, K3, K3, K2, K2, K2, K1 }));
        Paint("leg_s", LegCol(new int[][] { F0, K1, K1, K1, K1, K1, K0 }));
        Paint("leg_e", LegCol(new int[][] { F1, K2, K1, K2, K2, K1, K1 }));
        Paint("leg_w", LegCol(new int[][] { F1, K2, K1, K2, K2, K1, K1 }));
        Paint("leg_u", Cap(F1)); Paint("leg_d", Cap(K0));
        Paint("foot_u", PFootU); Paint("foot_d", PFootD);

        // texture png
        byte[] texPng = PngOf(W, H, delegate(int x, int y) { return img[y][x]; });
        string b64 = Convert.ToBase64String(texPng);

        // preview x8 on checker
        int S = 8;
        byte[] prevPng = PngOf(W * S, H * S, delegate(int x, int y)
        {
            int[] p = img[y / S][x / S];
            if (p[3] == 0)
            {
                int g = ((x / S) + (y / S)) % 2 == 0 ? 46 : 38;
                return new int[] { g, g, g, 255 };
            }
            return p;
        });

        // elements
        string bodyId = Cube("body", new double[] { -3, 7, -7 }, new double[] { 3, 12, 7 }, Faces6("body"), null, null);
        string tailId = Cube("tail", new double[] { -5, 9.5, 7 }, new double[] { 5, 9.5, 16 }, FacesUD("tail"), null, null);
        string neckId = Cube("neck", new double[] { -2, 9, -11 }, new double[] { 2, 13, -7 }, Faces6("neck"), null, null);
        string headId = Cube("head", new double[] { -2.5, 11, -16 }, new double[] { 2.5, 16, -11 }, Faces6("head"), null, null);
        string beakId = Cube("beak", new double[] { -1.5, 12.5, -20 }, new double[] { 1.5, 14.5, -16 }, Faces6("beak"), null, null);
        string hookId = Cube("beak_hook", new double[] { -1, 11, -20 }, new double[] { 1, 13, -19 }, Faces6("hook"), null, null);

        var eyeFaces = new Dictionary<string, object>();
        eyeFaces["north"] = Fc("eye_n"); eyeFaces["south"] = Fc("eye_s");
        eyeFaces["east"] = NB(); eyeFaces["west"] = NB(); eyeFaces["up"] = NB(); eyeFaces["down"] = NB();
        string eyeId = Cube("eye", new double[] { -2.5, 14, -16.1 }, new double[] { 2.5, 18, -16.1 }, eyeFaces,
            new double[] { 0, 14, -16.1 }, new double[] { 40, 0, 0 });

        var gillLFaces = new Dictionary<string, object>();
        gillLFaces["east"] = Fc("gill_out"); gillLFaces["west"] = Fc("gill_in");
        gillLFaces["north"] = NB(); gillLFaces["south"] = NB(); gillLFaces["up"] = NB(); gillLFaces["down"] = NB();
        string gillLId = Cube("gill_left", new double[] { 2.6, 11, -18 }, new double[] { 2.6, 15, -12 }, gillLFaces,
            new double[] { 2.6, 13, -12 }, new double[] { 0, 15, 0 });

        var gillRFaces = new Dictionary<string, object>();
        gillRFaces["west"] = Fc("gill_out"); gillRFaces["east"] = Fc("gill_in");
        gillRFaces["north"] = NB(); gillRFaces["south"] = NB(); gillRFaces["up"] = NB(); gillRFaces["down"] = NB();
        string gillRId = Cube("gill_right", new double[] { -2.6, 11, -18 }, new double[] { -2.6, 15, -12 }, gillRFaces,
            new double[] { -2.6, 13, -12 }, new double[] { 0, -15, 0 });

        string wliId = Cube("wing_left_inner", new double[] { 2, 12, -5 }, new double[] { 16, 13, 1 }, Faces6("wli"), null, null);
        string wloId = Cube("wing_left_outer", new double[] { 15, 12, -4 }, new double[] { 28, 13, 0 }, Faces6("wlo"), null, null);
        string wriId = Cube("wing_right_inner", new double[] { -16, 12, -5 }, new double[] { -2, 13, 1 }, Faces6("wri"), null, null);
        string wroId = Cube("wing_right_outer", new double[] { -28, 12, -4 }, new double[] { -15, 13, 0 }, Faces6("wro"), null, null);

        string legLId = Cube("leg_left", new double[] { 1, 0, 1 }, new double[] { 2, 7, 2 }, Faces6("leg"), null, null);
        string footLId = Cube("foot_left", new double[] { 0, 0.25, -2 }, new double[] { 3, 0.25, 2 }, FacesUD("foot"), null, null);
        string legRId = Cube("leg_right", new double[] { -2, 0, 1 }, new double[] { -1, 7, 2 }, Faces6("leg"), null, null);
        string footRId = Cube("foot_right", new double[] { -3, 0.25, -2 }, new double[] { 0, 0.25, 2 }, FacesUD("foot"), null, null);

        var headChildren = new List<object> { headId, beakId, hookId, eyeId, gillLId, gillRId };
        var neckChildren = new List<object> { neckId, Group("head", new double[] { 0, 12, -11 }, headChildren, null) };

        var outliner = new List<object>
        {
            Group("body", new double[] { 0, 9.5, 0 }, new List<object>
            {
                bodyId,
                Group("neck", new double[] { 0, 11, -7 }, neckChildren, null),
                Group("wing_left", new double[] { 3, 12.5, -2 }, new List<object>
                {
                    wliId,
                    Group("wing_left_tip", new double[] { 16, 12.5, -2 }, new List<object> { wloId }, new double[] { 0, -14, 0 })
                }, new double[] { 0, -8, 0 }),
                Group("wing_right", new double[] { -3, 12.5, -2 }, new List<object>
                {
                    wriId,
                    Group("wing_right_tip", new double[] { -16, 12.5, -2 }, new List<object> { wroId }, new double[] { 0, 14, 0 })
                }, new double[] { 0, 8, 0 }),
                Group("leg_left", new double[] { 1.5, 7, 1.5 }, new List<object> { legLId, footLId }, null),
                Group("leg_right", new double[] { -1.5, 7, 1.5 }, new List<object> { legRId, footRId }, null),
                Group("tail", new double[] { 0, 9.5, 7 }, new List<object> { tailId }, new double[] { -8, 0, 0 })
            }, null)
        };

        // ---------- animations ----------
        var anims = new List<object>();

        // perch: wings folded, head snaps, subtle breathing — the ONLY grounded pose.
        // Emitted as "perch": "idle" is taken by the flap (it is the always-on layer here),
        // and a grounded pose must never be the always-on layer for a bird that never lands.
        var idle = new Dictionary<string, List<object>>();
        idle["wing_left"] = Merge(Rot(new double[] { 0, 0, 72, 0 }), Pos(new double[] { 0, 0, -0.5, 0 }));
        idle["wing_left_tip"] = Merge(Rot(new double[] { 0, 0, -180, 0 }), Pos(new double[] { 0, 0, 1, 0 }));
        idle["wing_right"] = Merge(Rot(new double[] { 0, 0, -72, 0 }), Pos(new double[] { 0, 0, -0.5, 0 }));
        idle["wing_right_tip"] = Merge(Rot(new double[] { 0, 0, 180, 0 }), Pos(new double[] { 0, 0, 1, 0 }));
        idle["head"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 1.8, 0, 0, 0 }, new double[] { 1.95, 0, 28, 0 },
            new double[] { 3.1, 0, 28, 0 }, new double[] { 3.25, 0, -18, 0 }, new double[] { 4.2, 0, -18, 0 },
            new double[] { 4.35, 0, 0, 0 }, new double[] { 5.0, 8, 0, 0 }, new double[] { 5.15, 0, 0, 0 },
            new double[] { 6, 0, 0, 0 });
        idle["body"] = Pos(new double[] { 0, 0, 0, 0 }, new double[] { 3, 0, 0.3, 0 }, new double[] { 6, 0, 0, 0 });
        anims.Add(Anim("perch", "loop", 6, idle));

        // fly: slow flap +-16, fast downstroke / slow upstroke, tip lag, body pitch.
        // Emitted as "idle" — the only built-in that plays on an AI-driven mob here (it is
        // the one with no predicate at all). A phantom is airborne 100% of the time, so its
        // permanent base layer must be the flap. See the header for why idle_fly is dead.
        var fly = new Dictionary<string, List<object>>();
        fly["wing_left"] = Rot(
            new double[] { 0, 0, 0, 16 }, new double[] { 0.55, 0, 0, -14 },
            new double[] { 0.7, 0, 0, -12 }, new double[] { 1.4, 0, 0, 16 });
        fly["wing_right"] = Rot(
            new double[] { 0, 0, 0, -16 }, new double[] { 0.55, 0, 0, 14 },
            new double[] { 0.7, 0, 0, 12 }, new double[] { 1.4, 0, 0, -16 });
        fly["wing_left_tip"] = Rot(
            new double[] { 0, 0, 0, 4 }, new double[] { 0.15, 0, 0, 10 },
            new double[] { 0.7, 0, 0, -13 }, new double[] { 0.85, 0, 0, -10 }, new double[] { 1.4, 0, 0, 4 });
        fly["wing_right_tip"] = Rot(
            new double[] { 0, 0, 0, -4 }, new double[] { 0.15, 0, 0, -10 },
            new double[] { 0.7, 0, 0, 13 }, new double[] { 0.85, 0, 0, 10 }, new double[] { 1.4, 0, 0, -4 });
        fly["body"] = Merge(
            Rot(new double[] { 0, -2, 0, 0 }, new double[] { 0.55, 3, 0, 0 }, new double[] { 1.4, -2, 0, 0 }),
            Pos(new double[] { 0, 0, -0.5, 0 }, new double[] { 0.55, 0, 1, 0 }, new double[] { 1.4, 0, -0.5, 0 }));
        fly["leg_left"] = Rot(new double[] { 0, 55, 0, 0 });
        fly["leg_right"] = Rot(new double[] { 0, 55, 0, 0 });
        fly["head"] = Rot(new double[] { 0, 5, 0, 0 });
        fly["tail"] = Rot(new double[] { 0, -4, 0, 0 });
        anims.Add(Anim("idle", "loop", 1.4, fly));

        // glide: static spread wings, asymmetric micro-roll, tail rudder, rare head snap.
        // Not built-in and deliberately not the base state: its wings barely move, so as a
        // permanent loop it reads as a frozen model rather than a soaring bird. Reserved for
        // state{} bursts.
        var glide = new Dictionary<string, List<object>>();
        glide["wing_left"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 1.6, 0, 0, 2.5 },
            new double[] { 3.8, 0, 0, -1.8 }, new double[] { 6, 0, 0, 0 });
        glide["wing_right"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 2.0, 0, 0, 2.0 },
            new double[] { 4.4, 0, 0, -1.5 }, new double[] { 6, 0, 0, 0 });
        glide["wing_left_tip"] = Rot(
            new double[] { 0, 0, 0, 1 }, new double[] { 0.9, 0, 0, -1.5 }, new double[] { 2.1, 0, 0, 1.2 },
            new double[] { 3.3, 0, 0, -0.8 }, new double[] { 4.4, 0, 0, 1.5 }, new double[] { 5.2, 0, 0, -1 },
            new double[] { 6, 0, 0, 1 });
        glide["wing_right_tip"] = Rot(
            new double[] { 0, 0, 0, -0.8 }, new double[] { 1.2, 0, 0, 1.4 }, new double[] { 2.6, 0, 0, -1.2 },
            new double[] { 3.9, 0, 0, 0.9 }, new double[] { 5.0, 0, 0, -1.4 }, new double[] { 6, 0, 0, -0.8 });
        glide["body"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 1.8, 0, 0, 2 },
            new double[] { 4.2, 0, 0, -2 }, new double[] { 6, 0, 0, 0 });
        glide["tail"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 1.5, -2, 4, 0 }, new double[] { 3.0, 0, 0, 0 },
            new double[] { 4.5, -1, -3, 0 }, new double[] { 6, 0, 0, 0 });
        glide["head"] = Rot(
            new double[] { 0, 5, 0, 0 }, new double[] { 3.4, 5, 0, 0 }, new double[] { 3.55, 5, 10, 0 },
            new double[] { 4.3, 5, 10, 0 }, new double[] { 4.45, 5, 0, 0 }, new double[] { 6, 5, 0, 0 });
        glide["leg_left"] = Rot(new double[] { 0, 55, 0, 0 });
        glide["leg_right"] = Rot(new double[] { 0, 55, 0, 0 });
        anims.Add(Anim("glide", "loop", 6, glide));

        // takeoff: crouch -> jump -> unfold + 2 flaps -> settle into spread pose
        var tko = new Dictionary<string, List<object>>();
        tko["wing_left"] = Merge(
            Rot(new double[] { 0, 0, 72, 0 }, new double[] { 0.3, 0, 72, 0 }, new double[] { 0.55, 0, 0, 18 },
                new double[] { 0.85, 0, 0, -16 }, new double[] { 1.15, 0, 0, 12 }, new double[] { 1.4, 0, 0, 0 }),
            Pos(new double[] { 0, 0, -0.5, 0 }, new double[] { 0.3, 0, -0.5, 0 }, new double[] { 0.55, 0, 0, 0 }));
        tko["wing_right"] = Merge(
            Rot(new double[] { 0, 0, -72, 0 }, new double[] { 0.3, 0, -72, 0 }, new double[] { 0.55, 0, 0, -18 },
                new double[] { 0.85, 0, 0, 16 }, new double[] { 1.15, 0, 0, -12 }, new double[] { 1.4, 0, 0, 0 }),
            Pos(new double[] { 0, 0, -0.5, 0 }, new double[] { 0.3, 0, -0.5, 0 }, new double[] { 0.55, 0, 0, 0 }));
        tko["wing_left_tip"] = Merge(
            Rot(new double[] { 0, 0, -180, 0 }, new double[] { 0.3, 0, -180, 0 }, new double[] { 0.55, 0, 0, 10 },
                new double[] { 0.95, 0, 0, -14 }, new double[] { 1.4, 0, 0, 0 }),
            Pos(new double[] { 0, 0, 1, 0 }, new double[] { 0.3, 0, 1, 0 }, new double[] { 0.55, 0, 0, 0 }));
        tko["wing_right_tip"] = Merge(
            Rot(new double[] { 0, 0, 180, 0 }, new double[] { 0.3, 0, 180, 0 }, new double[] { 0.55, 0, 0, -10 },
                new double[] { 0.95, 0, 0, 14 }, new double[] { 1.4, 0, 0, 0 }),
            Pos(new double[] { 0, 0, 1, 0 }, new double[] { 0.3, 0, 1, 0 }, new double[] { 0.55, 0, 0, 0 }));
        tko["body"] = Merge(
            Rot(new double[] { 0, 0, 0, 0 }, new double[] { 0.3, 4, 0, 0 },
                new double[] { 0.55, -4, 0, 0 }, new double[] { 1.4, 0, 0, 0 }),
            Pos(new double[] { 0, 0, 0, 0 }, new double[] { 0.3, 0, -1.5, 0 },
                new double[] { 0.55, 0, 2.5, 0 }, new double[] { 1.0, 0, 1, 0 }, new double[] { 1.4, 0, 0, 0 }));
        tko["leg_left"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 0.45, 0, 0, 0 },
            new double[] { 0.9, 55, 0, 0 }, new double[] { 1.4, 55, 0, 0 });
        tko["leg_right"] = Rot(
            new double[] { 0, 0, 0, 0 }, new double[] { 0.45, 0, 0, 0 },
            new double[] { 0.9, 55, 0, 0 }, new double[] { 1.4, 55, 0, 0 });
        tko["head"] = Rot(new double[] { 0, 0, 0, 0 }, new double[] { 1.4, 5, 0, 0 });
        anims.Add(Anim("takeoff", "once", 1.4, tko));

        var meta = new Dictionary<string, object>();
        meta["format_version"] = "4.10"; meta["model_format"] = "free"; meta["box_uv"] = false;

        var resolution = new Dictionary<string, object>();
        resolution["width"] = 64; resolution["height"] = 64;

        var tex = new Dictionary<string, object>();
        tex["path"] = ""; tex["name"] = "sea_condor.png"; tex["folder"] = ""; tex["namespace"] = ""; tex["id"] = "0";
        tex["group"] = ""; tex["width"] = 64; tex["height"] = 64; tex["uv_width"] = 64; tex["uv_height"] = 64;
        tex["particle"] = false; tex["use_as_default"] = false; tex["layers_enabled"] = false;
        tex["sync_to_project"] = ""; tex["render_mode"] = "default"; tex["render_sides"] = "auto";
        tex["frame_time"] = 1; tex["frame_order_type"] = "loop"; tex["frame_order"] = "";
        tex["frame_interpolate"] = false; tex["visible"] = true; tex["internal"] = true; tex["saved"] = false;
        tex["uuid"] = Guid.NewGuid().ToString();
        tex["source"] = "data:image/png;base64," + b64;

        var model = new Dictionary<string, object>();
        model["meta"] = meta;
        model["name"] = "sea_condor";
        model["model_identifier"] = "sea_condor";
        model["visible_box"] = new int[] { 5, 2, 0 };
        model["variable_placeholders"] = "";
        model["variable_placeholder_buttons"] = new object[0];
        model["timeline_setups"] = new object[0];
        model["unhandled_root_fields"] = new Dictionary<string, object>();
        model["resolution"] = resolution;
        model["elements"] = elements;
        model["outliner"] = outliner;
        model["textures"] = new object[] { tex };
        model["animations"] = anims;

        var ser = new JavaScriptSerializer();
        ser.MaxJsonLength = int.MaxValue;
        string json = ser.Serialize(model);

        File.WriteAllText(Path.Combine(outDir, "sea_condor.bbmodel"), json);
        // текстура вшита в .bbmodel як data-URI, тож у outDir (ресурси плагіна) .png не їде —
        // тільки в scratch, для очей
        File.WriteAllBytes(Path.Combine(scratchDir, "sea_condor.png"), texPng);
        File.WriteAllBytes(Path.Combine(scratchDir, "sea_condor_texture_preview.png"), prevPng);

        return "OK: elements=" + elements.Count + ", json=" + json.Length + " chars, written to " + outDir;
    }
}
'@

Add-Type -TypeDefinition $src -ReferencedAssemblies @("System.Drawing", "System.Web.Extensions")
[SeaCondor]::Generate((Resolve-Path $Out).Path, (Resolve-Path $Scratch).Path)
