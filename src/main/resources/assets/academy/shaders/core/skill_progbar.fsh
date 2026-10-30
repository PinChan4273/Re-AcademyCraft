#version 150

// Legacy skill_progbar.frag: a pixel of the circle shows only where the radial mask's red is below
// the progress, so the ring fills clockwise from the top as the progress grows.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float Progress;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float threshold = texture(Sampler1, texCoord0).r;
    if (!(Progress > threshold)) discard;
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
