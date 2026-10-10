#version 150

// Legacy cpbar_cp.frag: the CP bar's texture, with the category's overlay icon cut out of it where
// the icon sits in the bar (65 by 65 at 857, 43 of the 964 by 147 bar).
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const vec2 origSize = vec2(964.0, 147.0);
const vec2 iconOffset = vec2(857.0, 43.0);
const vec2 iconMul = vec2(1.0 / 65.0, 1.0 / 65.0);

void main() {
    vec2 temp = ((texCoord0 * origSize) - iconOffset) * iconMul;
    float maskColor;
    if (temp.s < 0.0 || temp.s > 1.0 || temp.t < 0.0 || temp.t > 1.0) {
        maskColor = 1.0;
    } else {
        maskColor = 1.0 - texture(Sampler1, temp.st).a;
    }
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vertexColor * ColorModulator * vec4(texColor.rgb, texColor.a * maskColor);
}
