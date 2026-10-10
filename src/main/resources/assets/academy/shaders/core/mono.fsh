#version 150

// LambdaLib2 mono.frag: the texture times the colour, its red, green and blue averaged.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 result = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (result.a == 0.0) discard;
    float c = (result.r + result.g + result.b) / 3.0;
    fragColor = vec4(c, c, c, result.a);
}
