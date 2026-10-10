#version 150

// Legacy plasma_body.vert: the camera-space position goes to the fragment shader.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 camspace;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    camspace = view.xyz / view.w;
}
