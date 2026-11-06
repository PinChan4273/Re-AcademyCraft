#version 330

// 原作 plasma_body.frag の 1 行ずつの写し: カメラ空間（z は反転）の最大 16 個の球の metaball の密度を、画素の 3 手前から
// 視線に沿って 0.15 ずつ 20 歩進み、密度で水色と桃色の間に色を付ける。uniform は 26.2 の std140 の block。
layout(std140) uniform PlasmaBody {
    vec4 Balls[16];
    int BallCount;
    float Alpha;
};

in vec3 camspace;

out vec4 fragColor;

float f(vec3 position) {
    float ret = 0.0;
    for (int i = 0; i < BallCount; ++i) {
        float distance = max(0.1, length(position - Balls[i].xyz));
        ret += Alpha * Balls[i].w / (distance * distance);
    }
    return clamp(ret, 0.0, 2.0);
}

vec4 rayMarch(vec3 begin, vec3 dir) {
    dir *= 0.15;
    vec3 pos = begin;
    vec4 accum = vec4(0.0);
    for (int i = 0; i < 20 && accum.a < 1.0; ++i) {
        float density = f(pos);
        float alpha = 0.075 * density;
        vec3 crl = mix(vec3(0.43, 0.74, 1.0), vec3(0.98, 0.51, 0.92), 1.0 - density / 2.0);
        accum.rgb = mix(accum.rgb, crl, alpha / (accum.a + alpha));
        accum.a += alpha;
        pos += dir;
    }
    if (accum.a < 0.2) {
        accum.a = 2.0 * accum.a - 0.2;
    }
    return accum;
}

void main() {
    vec3 cam = camspace;
    cam.z = -cam.z;
    vec3 dir = normalize(cam);
    vec4 rc = rayMarch(cam - dir * 3.0, dir);
    rc.a = clamp(rc.a, 0.0, 1.0) * (0.5 + Alpha * 0.5);
    fragColor = rc;
}
