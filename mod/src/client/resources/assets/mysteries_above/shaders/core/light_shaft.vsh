#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 shaft;
out vec2 size;
out vec2 clock;
out vec3 position;
out vec3 axis;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    // UV0: across the shaft (-1 at one rim, 1 at the other) and blocks from its source.
    shaft = UV0;
    // UV1: seconds the shaft has stood (sent in sixteenths of a tick) and how far its front thins.
    clock = vec2(float(UV1.x) / 320.0, float(UV1.y) / 64.0);
    // UV2: the shaft's length and radius, in 1/64 of a block — two shorts with room to spare.
    size = vec2(UV2) / 64.0;
    // Relative to the camera: the eye is the origin.
    position = Position;
    axis = Normal;
}
