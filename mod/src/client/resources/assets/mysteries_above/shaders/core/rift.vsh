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
out vec3 position;
out vec3 into;
out float depth;
out float seconds;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // The glow at the corridor's end; its alpha is how much of the door is there.
    vertexColor = Color;
    // UV0: where on the doorway, in blocks — across from its middle, up from its threshold.
    local = UV0;
    // UV1: how deep the corridor runs, in 1/64 of a block.
    depth = float(UV1.x) / 64.0;
    // UV2: seconds the door has stood (sent in sixteenths of a tick).
    seconds = float(UV2.x) / 320.0;
    // Relative to the camera: the eye is the origin.
    position = Position;
    // The way into the door.
    into = Normal;
}
