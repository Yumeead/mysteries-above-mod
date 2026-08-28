#version 330

// Втрата глузду очима гравця. Один прохід, одна ручка: Amount 0..1.
//
// Власний шейдер, а не ланцюг із ванільних `color_convolve` + `bits`: обидва вони
// піднімають насиченість константою в тілі (1.8 і 1.5), тож вицвітання довелося б
// підбирати наосліп проти множника 2.7. Тут те саме коштує двадцять рядків і
// налаштовується одним числом.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SanityConfig {
    float Amount;
};

out vec4 fragColor;

const vec3 Luma = vec3(0.3, 0.59, 0.11);

void main() {
    // Світ грубішає: що менше глузду, то більший піксель. Мозаїка, а не розмиття —
    // розмиття читалось би як окуляри, мозаїка як «око вже не збирає деталі».
    float mosaic = mix(1.0, 4.0, Amount);
    vec2 blocks = InSize / mosaic;
    vec2 coord = (floor(texCoord * blocks) + 0.5) / blocks;

    vec3 color = texture(InSampler, coord).rgb;

    // Колір вицвітає до сірого й тягне в хворобливу зелень.
    float grey = dot(color, Luma);
    color = mix(color, vec3(grey) * vec3(0.92, 1.0, 0.86), Amount * 0.85);

    // Краї зору змикаються.
    vec2 offset = texCoord - 0.5;
    float vignette = 1.0 - smoothstep(0.25, 0.72, dot(offset, offset) * 2.0) * Amount * 0.70;

    fragColor = vec4(color * vignette * mix(1.0, 0.78, Amount), 1.0);
}
