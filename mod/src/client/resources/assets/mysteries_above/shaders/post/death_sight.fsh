#version 330

// Death's Spirit Vision: the world seen as a graveyard. One pass, one knob: Amount 0..1.
//
// Not the sanity shader with another number: that one also coarsens pixels and closes the edges
// of sight — illness. This is the opposite, a calm, cold clarity: colour drains to a grey with a
// faint green of the pathway, contrast stays, nothing blurs.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform DeathSightConfig {
    float Amount;
};

out vec4 fragColor;

const vec3 Luma = vec3(0.3, 0.59, 0.11);
const vec3 GraveTint = vec3(0.88, 0.97, 0.90);

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float grey = dot(color, Luma);
    // A slight lift of the darks: the dead are seen, not hidden in the gloom.
    vec3 grave = vec3(pow(grey, 0.9)) * GraveTint;
    fragColor = vec4(mix(color, grave, Amount), 1.0);
}
