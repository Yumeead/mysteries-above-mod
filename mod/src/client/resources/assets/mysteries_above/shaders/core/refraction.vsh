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
out vec2 local;
out vec2 shape;
out vec3 position;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // The rim's colour; its alpha is how strongly the disc bends.
    vertexColor = Color;
    // UV0: where on the disc, -1 to 1 both ways.
    local = UV0;
    // UV1: lens (0) or wave (1), and how far out the wave's ring has run, in 1/1024 of the disc.
    shape = vec2(float(UV1.x), float(UV1.y) / 1024.0);
    // Relative to the camera: the eye is the origin.
    position = Position;
}
