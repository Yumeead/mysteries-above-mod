#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Bent air: the frame behind the disc, read a little off from where this pixel stands.
//
// A lens swells the scene from its centre, as a drop of water does, with a hairline of light at its
// edge; a wave pushes the scene outwards in a ring that runs from the centre to the edge. Where the
// disc thins out it lets the real frame through, so it has no edge of its own.

// The frame's colour, copied after the solid entities were drawn.
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 local;
in vec2 shape;
in vec3 position;

out vec4 fragColor;

// How much a lens magnifies at its heart: it reads the scene this much of its radius nearer its centre.
const float SWELL = 0.45;
// How far a wave pushes the scene outwards, as a share of the disc's radius, and how thick its ring is.
const float PUSH = 0.07;
const float WAVE_WIDTH = 0.16;
// How bright the rim catches the light.
const float RIM = 0.55;
// Right at the eye a disc would fill the view: it is gone nearer than this.
const float NEAR_START = 0.5;
const float NEAR_END = 1.5;

void main() {
    float r = length(local);
    if (r >= 1.0) discard;

    // Pixels across one unit of the disc: the disc's radius on screen.
    vec2 step = fwidth(local);
    float pixels = 1.0 / max(max(step.x, step.y), 1e-5);
    vec2 dir = r > 1e-4 ? local / r : vec2(0.0);

    vec2 offset;
    float rim;
    float body;
    if (shape.x < 0.5) {
        // The scene is read nearer the centre the nearer the centre this pixel is: it swells.
        offset = -local * SWELL * (1.0 - r * r) * pixels;
        rim = smoothstep(0.80, 0.93, r) * (1.0 - smoothstep(0.93, 1.0, r));
        body = 1.0 - smoothstep(0.86, 1.0, r);
    } else {
        // A ring of air thrown outwards: the scene is pushed out across it, and its crest catches light.
        float d = (r - shape.y) / WAVE_WIDTH;
        float band = exp(-d * d);
        offset = -dir * band * PUSH * pixels;
        rim = exp(-d * d * 6.0) * 0.3;
        body = band * (1.0 - smoothstep(0.9, 1.0, r));
    }

    float near = smoothstep(NEAR_START, NEAR_END, length(position));
    float strength = vertexColor.a * near;
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 at = clamp(ivec2(gl_FragCoord.xy + offset * strength), ivec2(0), size - 1);
    vec3 scene = texelFetch(Sampler0, at, 0).rgb;
    vec3 color = scene + vertexColor.rgb * rim * RIM;
    fragColor = vec4(color, body * strength) * ColorModulator;
}
