#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// 1.21.1 の vanilla の position_tex_color と同じく、アルファ 0.1 未満の画素を捨てる（26.2 の vanilla は 0 だけを捨てる）。
// 1.21.1 の position_tex_color_keep に当たる物（0 だけ捨てる）は 26.2 では vanilla のままでよいので持たない。
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color * ColorModulator;
}
