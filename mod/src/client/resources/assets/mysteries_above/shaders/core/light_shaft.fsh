#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A shaft of light standing in hazy air, the way one falls through a gap in the clouds.
//
// What reaches the eye from such a shaft is the light the haze scatters along the line of sight, so
// a pixel is as bright as the lit air its view ray crosses: most across the middle, none at the rim,
// and more the more along the shaft the eye looks. Real light is brightest at its heart and thins out
// smoothly, with no edge anywhere — so the haze falls off as a softened chord and the core as a bell.
// The geometry is a single strip turned to face the camera; this shader is what makes it a volume.

// The frame's depth after the opaque terrain: where the shaft meets the ground it thins out.
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 shaft;
in vec2 size;
in vec2 clock;
in vec3 position;
in vec3 axis;

out vec4 fragColor;

// The core's spread, as a share of the haze's radius.
const float CORE = 0.16;
// How much each part gives off: the wide haze carries the colour, the core burns, motes glint.
const float HAZE_GLOW = 0.42;
const float CORE_GLOW = 0.42;
const float MOTE_GLOW = 0.7;
// How much the haze and the core veil what is behind them. This is what keeps the colour against a
// bright sky: light added to the noon sky only whitens it, light that also hides the sky stays gold.
// The haze veils no more than it lights, or its rim would dim the sky to grey; the core veils more.
const float VEIL = 0.45;
const float CORE_VEIL = 0.5;
const float MAX_VEIL = 0.92;
// How white the core is: sunlight is white-hot in its heart and coloured at its edges.
const float WHITE = 0.22;
// Light in haze reads more saturated than the pale colour a pathway is drawn in on a flask.
const float SATURATION = 1.6;
// From its source the shaft gathers over this many of its own radii: a thin ray leaves the hand at once.
const float SOURCE_RADII = 3.0;
// A shaft out of the sky is full only over its last this many blocks; above, the air thins it out
// towards its source, though never below this share — it still runs up into the sun.
const float FULL_LENGTH = 48.0;
const float HIGH_SHARE = 0.4;
// A broad shaft is thin haze: past this radius (blocks) it thins out with its breadth, to no less
// than this share. Light out of the sun is a wide band you see the sky through, not a solid post.
const float DENSE_RADIUS = 2.0;
const float MIN_DENSITY = 0.3;
// Seen along its length a ray crosses more lit air; past this much more it is as bright as it gets.
const float MAX_PATH = 2.5;
// Right at the eye the haze would be a wall across the view: it is not drawn nearer than this.
const float NEAR_START = 0.3;
const float NEAR_END = 1.0;
// How far in front of what stands behind it the haze thins out, in its own radii — and at the least.
const float SOFT_RADII = 0.45;
const float MIN_SOFT = 0.4;
// The blocks over which the shaft's far end thins, once it has landed.
const float END_SOFT = 0.3;
// Motes: one in this many cells of this size (blocks) holds a speck this wide, sinking this fast.
const float MOTE_SHARE = 0.14;
const float MOTE_CELL = 0.45;
const float MOTE_SIZE = 0.04;
const float MOTE_SINK = 0.8;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// The lit air a ray crosses at distance x from the axis, softened so it reaches the rim with no edge.
float haze_at(float x) {
    float inside = max(0.0, 1.0 - x * x);
    return inside * sqrt(inside);
}

// A speck at [p] (blocks across and along): each cell holds at most one, at a place of its own, drawn
// as a round dot — never a dash, whatever the noise does. A speck smaller than a pixel would flicker,
// so it is drawn no smaller than one, and dimmer by as much as it grew.
float mote_at(vec2 p, float pixel) {
    vec2 cell = floor(p / MOTE_CELL);
    if (hash(cell) > MOTE_SHARE) return 0.0;
    vec2 centre = (cell + 0.25 + 0.5 * vec2(hash(cell + 17.0), hash(cell + 31.0))) * MOTE_CELL;
    float radius = max(MOTE_SIZE, pixel);
    float spot = 1.0 - smoothstep(radius * 0.5, radius, distance(p, centre));
    return spot * (MOTE_SIZE * MOTE_SIZE) / (radius * radius);
}

// Window depth back to the distance in front of the eye. The projection is a perspective one either
// way; whether its depth runs 0..1 or -1..1 depends on the device, so the caller says which.
float eye_distance(float windowDepth, bool zeroToOne) {
    float ndc = zeroToOne ? windowDepth : windowDepth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

void main() {
    float across = shaft.x;
    float along = shaft.y;
    float span = size.x;
    float radius = size.y;
    float seconds = clock.x;

    // How slanting the view is: looked at along its length, a ray crosses that much more lit air.
    float slant = length(cross(normalize(position), normalize(axis)));
    float path = min(1.0 / max(slant, 0.01), MAX_PATH);

    float haze = haze_at(across);
    float core = exp(-across * across / (2.0 * CORE * CORE));

    // The haze is not even: streaks run along the shaft and drift slowly down it, as dust in sunlight.
    // They drift by the shaft's own clock: the day's wraps every twenty minutes, and the noise with it.
    float streaks = 0.55 + 0.3 * noise(vec2(across * radius * 2.2, along * 0.3 - seconds * 0.5))
        + 0.15 * noise(vec2(across * radius * 5.0, along * 0.9 - seconds * 1.1));
    vec2 speck = vec2(across * radius, along - seconds * MOTE_SINK);
    float motes = mote_at(speck, length(fwidth(speck))) * haze;

    float source = smoothstep(0.0, radius * SOURCE_RADII, along)
        * mix(HIGH_SHARE, 1.0, smoothstep(0.0, max(span - FULL_LENGTH, 1.0), along));
    float end = smoothstep(0.0, max(END_SOFT, clock.y), span - along);
    float eye = length(position);
    float near = smoothstep(NEAR_START, NEAR_END, eye);

    // Soft where it meets things: the less air stands between the shaft and what is behind it, the thinner.
    float own = -(ModelViewMat * vec4(position, 1.0)).z;
    bool zeroToOne = abs(eye_distance(gl_FragCoord.z, true) - own) < abs(eye_distance(gl_FragCoord.z, false) - own);
    float behind = eye_distance(texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).r, zeroToOne);
    float soft = clamp((behind - own) / max(radius * SOFT_RADII, MIN_SOFT), 0.0, 1.0);

    // Only the air's own fog: the render-distance fog would cut a shaft out of the sky short of the sun.
    float fog = linear_fog_value(eye, FogEnvironmentalStart, FogEnvironmentalEnd);
    float density = clamp(DENSE_RADIUS / radius, MIN_DENSITY, 1.0);
    float fade = source * end * near * soft * density * vertexColor.a * (1.0 - fog);

    // The light never goes past full: past it the gold would burn out to white.
    float light = min((haze * streaks * HAZE_GLOW + core * CORE_GLOW) * path + motes * MOTE_GLOW, 1.0) * fade;
    float veil = min((haze * streaks * VEIL + core * CORE_VEIL) * path, MAX_VEIL) * fade;
    vec3 tint = vertexColor.rgb;
    tint = clamp(mix(vec3(dot(tint, vec3(0.3, 0.59, 0.11))), tint, SATURATION), 0.0, 1.0);
    vec3 color = mix(tint, vec3(1.0), core * WHITE);
    // Premultiplied: the light adds itself, and the haze veils what is behind it.
    fragColor = vec4(color * light, veil) * ColorModulator;
}
