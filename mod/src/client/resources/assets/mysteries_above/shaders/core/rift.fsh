#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The corridor behind a door of the Door pathway: a flat face in the doorway, and behind it a passage
// deeper than the wall it stands in — arch after arch receding in half-block steps, each one pixel
// wide, to a glow at its end in the colour of where it leads.
//
// Interior mapping: the view ray is followed into a virtual corridor of the arch's own cross-section.
// At each frame's depth it is either still inside the arch — on that frame's one-pixel line, or past
// it — or it has left the arch, and then it struck the corridor's wall between this frame and the
// last. Everything is snapped to sixteenths of a block, so the corridor steps the way blocks do.
// No stencil, no second scene: one quad, and no depth fight with the wall it is cut into.

in vec4 vertexColor;
in vec2 local;
in vec3 position;
in vec3 into;
in float depth;
in float seconds;

out vec4 fragColor;

const float PIXEL = 1.0 / 16.0;
// The arch: a block across, straight jambs to SPRING, then a half circle.
const float HALF = 0.5;
const float SPRING = 1.5;
// A frame every half block, at most this many.
const float STEP = 0.5;
const int MAX_FRAMES = 16;
// How bright a frame is at the mouth and at the end; the walls near and far, lit from the end.
const float FRAME_NEAR = 0.8;
const float FRAME_FAR = 0.3;
const float WALL_NEAR = 0.07;
const float WALL_FAR = 0.38;
// The end glows, brightest at its middle.
const float END = 1.0;
const float END_EDGE = 0.55;
// Light this bright washes towards white.
const float WHITE = 0.35;
// The frames pulse inwards, slowly: the corridor breathes.
const float PULSE = 0.15;
// Right at the eye the corridor would be a wall across the view: it thins out nearer than this.
const float NEAR_START = 0.3;
const float NEAR_END = 1.2;

vec2 snapped(vec2 p) {
    return (floor(p / PIXEL) + 0.5) * PIXEL;
}

bool inside(vec2 p) {
    p = snapped(p);
    if (p.y < 0.0) return false;
    if (p.y <= SPRING) return abs(p.x) < HALF;
    vec2 q = vec2(p.x, p.y - SPRING);
    return dot(q, q) < HALF * HALF;
}

// Away from the corridor's middle, square to its wall: the jambs face sideways, the arch outwards.
vec2 outward(vec2 p) {
    return p.y <= SPRING ? vec2(sign(p.x), 0.0) : normalize(vec2(p.x, p.y - SPRING));
}

void main() {
    vec3 f = normalize(into);
    vec3 across = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 ray = normalize(position);
    vec3 d = vec3(dot(ray, across), ray.y, dot(ray, f));
    vec2 mouth = local;
    // From behind, a door in the air opens the other way: the same corridor, mirrored.
    if (d.z < 0.0) {
        d.z = -d.z;
        d.x = -d.x;
        mouth.x = -mouth.x;
    }
    d.z = max(d.z, 1e-3);

    int frames = min(int(depth / STEP), MAX_FRAMES);
    float light = -1.0;
    for (int k = 1; k <= MAX_FRAMES; k++) {
        if (k > frames) break;
        float w = float(k) * STEP;
        float t = w / max(depth, STEP);
        vec2 p = mouth + d.xy * (w / d.z);
        if (!inside(p)) {
            light = mix(WALL_NEAR, WALL_FAR, t);
            break;
        }
        if (!inside(p + outward(p) * PIXEL)) {
            light = mix(FRAME_NEAR, FRAME_FAR, t) * (1.0 - PULSE + PULSE * sin(seconds * 2.0 - w * 1.5));
            break;
        }
    }
    if (light < 0.0) {
        vec2 p = mouth + d.xy * (depth / d.z);
        if (inside(p)) {
            vec2 centre = vec2(0.0, SPRING * 0.6);
            light = mix(END, END_EDGE, clamp(distance(snapped(p), centre) / (SPRING * 0.8), 0.0, 1.0));
        } else {
            light = WALL_FAR;
        }
    }

    vec3 color = mix(vertexColor.rgb, vec3(1.0), WHITE * light) * light;
    float eye = length(position);
    float fog = total_fog_value(eye, fog_cylindrical_distance(position), FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd);
    color = mix(color, FogColor.rgb, fog * FogColor.a);
    float alpha = vertexColor.a * smoothstep(NEAR_START, NEAR_END, eye);
    // Premultiplied: the corridor is another place, so where it stands it hides what is behind it.
    fragColor = vec4(color * alpha, alpha) * ColorModulator;
}
