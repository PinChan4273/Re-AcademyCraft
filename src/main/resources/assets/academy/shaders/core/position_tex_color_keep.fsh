#version 150

// Minecraft's position_tex_color, but dropping only fully clear fragments: AcademyCraft 1.12.2 drew these effects with
// the alpha test off (glDisable(GL_ALPHA_TEST)), where the vanilla shader drops everything under alpha 0.1.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
