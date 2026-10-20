package vn.paperworld.phidoi.engine

/** GLSL ES 3.00 sources, ported from the web build's three.js materials and custom shaders */
object Shaders {
    const val HEAD = "#version 300 es\nprecision highp float;\nprecision highp int;\n"

    const val NOISE = """
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 p){float v=0.,a=.5;for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+vec2(1.7,9.2);a*=.5;}return v;}
"""

    const val ATTRS = """
layout(location=0) in vec3 position;
layout(location=1) in vec3 normal;
layout(location=2) in vec2 uv;
layout(location=3) in vec4 color;
layout(location=4) in vec3 onormal;
#ifdef INSTANCED
layout(location=5) in mat4 instanceMatrix;
#endif
#ifdef ICOLOR
layout(location=9) in vec3 instanceColor;
#endif
uniform mat4 uProj, uView, uModel;
"""

    /* ---------- toon (MeshToonMaterial) ---------- */
    const val TOON_VS = ATTRS + """
uniform mat3 uNormalMat;
#ifdef SHADOW
uniform mat4 uShadowMat;
uniform float uNormalBias;
out vec4 vShadow;
#endif
out vec3 vN; out vec3 vWP; out vec3 vColor; out vec2 vUv; out float vFogDepth;
#ifdef BUILDING
out float vSeed; out float vBase;
#endif
void main(){
  vec4 p = vec4(position, 1.0);
  vec3 n = normal;
#ifdef INSTANCED
  mat3 im = mat3(instanceMatrix);
  n /= vec3(dot(im[0], im[0]), dot(im[1], im[1]), dot(im[2], im[2]));
  n = im * n;
  p = instanceMatrix * p;
#endif
  vec4 wp = uModel * p;
  vN = normalize(uNormalMat * n);
  vWP = wp.xyz;
  vColor = vec3(1.0);
#ifdef VCOLOR
  vColor = color.rgb;
#endif
#ifdef ICOLOR
  vColor *= instanceColor;
#endif
  vUv = uv;
#ifdef BUILDING
#ifdef INSTANCED
  vSeed = instanceMatrix[3][0] * 0.0131 + instanceMatrix[3][2] * 0.0173;
  vBase = instanceMatrix[3][1] - length(instanceMatrix[1].xyz) * 0.5;
#else
  vSeed = 0.0; vBase = uModel[3][1] - length(uModel[1].xyz) * 0.5;
#endif
#endif
  vec4 mv = uView * wp;
  vFogDepth = -mv.z;
#ifdef SHADOW
  vShadow = uShadowMat * vec4(wp.xyz + vN * uNormalBias, 1.0);
#endif
  gl_Position = uProj * mv;
}
"""

    const val TOON_FS = """
in vec3 vN; in vec3 vWP; in vec3 vColor; in vec2 vUv; in float vFogDepth;
#ifdef BUILDING
in float vSeed; in float vBase;
uniform float uGlow, uFloor, uCell, uLitP; uniform vec3 uGlass, uWarm;
#endif
#ifdef SHADOW
in vec4 vShadow;
uniform highp sampler2DShadow uShadowMap;
uniform float uShadowBias; uniform vec2 uShadowTexel;
#endif
#ifdef MAP
uniform sampler2D uMap;
#endif
uniform vec3 uColor; uniform float uOpacity; uniform vec3 uEmissive;
uniform vec3 uSunDir, uSunColor, uHemiSky, uHemiGround;
uniform vec3 uFogColor; uniform float uFogNear, uFogFar;
out vec4 fragColor;
void main(){
  vec4 diffuse = vec4(uColor, uOpacity);
#ifdef MAP
  diffuse *= texture(uMap, vUv);
#endif
  diffuse.rgb *= vColor;
  float winLit = 0.0;
#ifdef BUILDING
  vec3 wn = normalize(vN);
  if (abs(wn.y) < 0.5) {
    float hc = abs(wn.x) > abs(wn.z) ? vWP.z : vWP.x;
    float fy = (vWP.y - vBase) / uFloor;
    vec2 cl = vec2(hc / uCell, fy), f = fract(cl);
    float win = step(.2, f.x) * step(f.x, .8) * step(.3, f.y) * step(f.y, .8) * step(1., fy);
    diffuse.rgb = mix(diffuse.rgb, uGlass, win * .78);
    float r = fract(sin(dot(floor(cl) + vec2(vSeed * 7.1, vSeed * 3.3), vec2(12.9898, 78.233))) * 43758.5453);
    winLit = win * step(1. - uLitP, r) * (.7 + .3 * fract(r * 13.));
  } else if (wn.y > .5) { diffuse.rgb *= .86; }
#endif
  vec3 N = normalize(vN);
#ifdef DOUBLE
  if (!gl_FrontFacing) N = -N;
#endif
  float u = dot(N, uSunDir) * 0.5 + 0.5;
  float grad = u < 0.33333 ? 0.41176 : (u < 0.66667 ? 0.72549 : 1.0);
  float sh = 1.0;
#ifdef SHADOW
  vec3 sc = vShadow.xyz / vShadow.w;
  if (sc.x >= 0.0 && sc.x <= 1.0 && sc.y >= 0.0 && sc.y <= 1.0 && sc.z <= 1.0) {
    sc.z += uShadowBias;
    float s = 0.0;
    for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) s += texture(uShadowMap, vec3(sc.xy + vec2(float(i), float(j)) * uShadowTexel, sc.z));
    sh = s / 9.0;
  }
#endif
  vec3 irr = grad * uSunColor * sh;
  vec3 hemi = mix(uHemiGround, uHemiSky, N.y * 0.5 + 0.5);
  vec3 col = diffuse.rgb * 0.31830988618 * (irr + hemi);
  vec3 em = uEmissive;
#ifdef EMTINT
  em *= vColor;
#endif
#ifdef EMMAP
  em *= texture(uMap, vUv).rgb;
#endif
  col += em;
#ifdef BUILDING
  col += uWarm * winLit * uGlow * 1.5;
#endif
#ifdef FOG
  col = mix(col, uFogColor, smoothstep(uFogNear, uFogFar, vFogDepth));
#endif
  fragColor = vec4(col, diffuse.a);
}
"""

    /* ---------- unlit (MeshBasicMaterial / LineBasicMaterial) ---------- */
    const val BASIC_VS = ATTRS + """
out vec4 vColor; out vec2 vUv; out float vFogDepth;
void main(){
  vec4 p = vec4(position, 1.0);
#ifdef INSTANCED
  p = instanceMatrix * p;
#endif
  vColor = vec4(1.0);
#ifdef VCOLOR
  vColor = color;
#endif
#ifdef ICOLOR
  vColor.rgb *= instanceColor;
#endif
  vUv = uv;
  vec4 mv = uView * uModel * p;
  vFogDepth = -mv.z;
  gl_Position = uProj * mv;
}
"""
    const val BASIC_FS = """
in vec4 vColor; in vec2 vUv; in float vFogDepth;
uniform vec3 uColor; uniform float uOpacity;
uniform vec3 uFogColor; uniform float uFogNear, uFogFar;
#ifdef MAP
uniform sampler2D uMap;
#endif
out vec4 fragColor;
void main(){
  vec4 c = vec4(uColor, uOpacity) * vColor;
#ifdef MAP
  c *= texture(uMap, vUv);
#endif
#ifdef FOG
  c.rgb = mix(c.rgb, uFogColor, smoothstep(uFogNear, uFogFar, vFogDepth));
#endif
  fragColor = c;
}
"""

    /* ---------- points (PointsMaterial) ---------- */
    const val POINTS_VS = ATTRS + """
uniform float uSize, uScale;
out vec3 vColor; out float vFogDepth;
void main(){
  vColor = vec3(1.0);
#ifdef VCOLOR
  vColor = color.rgb;
#endif
  vec4 mv = uView * uModel * vec4(position, 1.0);
  vFogDepth = -mv.z;
  gl_PointSize = uSize;
#ifdef ATTEN
  gl_PointSize *= uScale / -mv.z;
#endif
  gl_Position = uProj * mv;
}
"""
    const val POINTS_FS = """
in vec3 vColor; in float vFogDepth;
uniform vec3 uColor; uniform float uOpacity;
uniform vec3 uFogColor; uniform float uFogNear, uFogFar;
#ifdef MAP
uniform sampler2D uMap;
#endif
out vec4 fragColor;
void main(){
  vec4 c = vec4(uColor * vColor, uOpacity);
#ifdef MAP
  c *= texture(uMap, vec2(gl_PointCoord.x, 1.0 - gl_PointCoord.y));
#endif
#ifdef FOG
  c.rgb = mix(c.rgb, uFogColor, smoothstep(uFogNear, uFogFar, vFogDepth));
#endif
  fragColor = c;
}
"""

    /* ---------- ink outline: inverted hull ---------- */
    const val OUTLINE_VS = ATTRS + """
uniform float uThick; uniform vec3 uCamPos;
out float vDist;
void main(){
  mat4 mw = uModel;
#ifdef INSTANCED
  mw = mw * instanceMatrix;
#endif
  vec4 wp = mw * vec4(position, 1.0);
  vec3 n = normalize(mat3(mw) * onormal);
  float d = distance(uCamPos, wp.xyz);
  wp.xyz += n * uThick * (0.7 + d * 0.009);
  vDist = d;
  gl_Position = uProj * uView * wp;
}
"""
    const val OUTLINE_FS = """
uniform vec3 uInk, uFogColor; uniform float uFogNear, uFogFar;
in float vDist;
out vec4 fragColor;
void main(){ fragColor = vec4(mix(uInk, uFogColor, smoothstep(uFogNear * 0.6, uFogFar * 0.7, vDist)), 1.0); }
"""

    /* ---------- shadow depth ---------- */
    const val DEPTH_VS = ATTRS + """
void main(){
  vec4 p = vec4(position, 1.0);
#ifdef INSTANCED
  p = instanceMatrix * p;
#endif
  gl_Position = uProj * uView * uModel * p;
}
"""
    const val DEPTH_FS = "out vec4 fragColor;\nvoid main(){ fragColor = vec4(1.0); }\n"

    /* ---------- sky: watercolour clouds with ink edges ---------- */
    const val SKY_VS = ATTRS + """
out vec3 vDir;
void main(){ vDir = position; gl_Position = uProj * uView * uModel * vec4(position, 1.0); }
"""
    const val SKY_FS = NOISE + """
uniform float uTime, uStars, uRain, uMoon;
uniform vec3 uZen, uHor, uSun, uSunDir, uCloudLit, uCloudShade, uInk;
in vec3 vDir;
out vec4 fragColor;
void main(){
  vec3 d = normalize(vDir); float h = d.y;
  vec3 col = mix(uHor, uZen, smoothstep(-.02, .6, h));
  float sd = max(dot(d, uSunDir), 0.);
  col += uSun * (pow(sd, 320.) * 1.6 + pow(sd, 14.) * .25 + pow(sd, 3.) * .09);
  vec3 md = normalize(vec3(-.35, .55, .5)); float mm = max(dot(d, md), 0.);
  col += vec3(.95, .93, .8) * uMoon * (smoothstep(.9994, .9997, mm) * 1.2 + pow(mm, 40.) * .08);
  if (uStars > 0.) { vec2 sp = d.xz / (abs(h) + .3) * 90.; float s = step(.992, hash(floor(sp))) * smoothstep(.0, .2, h); col += vec3(s) * uStars * .9; }
  if (h > -.05) {
    vec2 cp = d.xz / (h + .14) * .75 + vec2(uTime * .006, uTime * .002);
    float n = fbm(cp * 1.25);
    float th = .56 - uRain * .18;
    float fw = fwidth(n) * 1.3 + 1e-4;
    float c = smoothstep(th, th + fw, n);
    float lit = smoothstep(th, th + .2, fbm(cp * 1.25 + uSunDir.xz * .06 + vec2(.02, .05)));
    vec3 cc = mix(uCloudShade, uCloudLit, lit);
    float fade = smoothstep(-.02, .12, h);
    col = mix(col, cc, c * fade);
    float line = (1. - smoothstep(0., fw * 1.6, abs(n - th))) * fade * smoothstep(.0, .25, h + .1);
    col = mix(col, uInk, line * .45);
  }
  fragColor = vec4(col, 1.);
}
"""

    /* ---------- sea: painted water that knows where the shore is ---------- */
    const val SEA_VS = ATTRS + """
out vec3 vW;
void main(){ vec4 w = uModel * vec4(position, 1.); vW = w.xyz; gl_Position = uProj * uView * w; }
"""
    const val SEA_FS = NOISE + """
uniform float uTime, uFogNear, uFogFar, uDark, uMapS, uGridN;
uniform vec2 uMap0; uniform sampler2D uMap;
uniform vec3 uDeep, uMid, uShallow, uFoam, uRiver, uFogColor, uSunDir, uSunCol, uCamPos;
in vec3 vW;
out vec4 fragColor;
void main(){
  vec2 uv = (vW.xz - uMap0) / uMapS * ((uGridN - 1.) / uGridN) + .5 / uGridN;
  vec4 m = texture(uMap, uv);
  float h = m.r * 80. - 40., riv = m.g, rock = m.b, depth = -h;
  vec2 p = vW.xz * .012;
  float n = fbm(p * 1.3 + uTime * .015);
  vec3 col = mix(uDeep, uMid, smoothstep(.25, .75, n));
  col = mix(col, uShallow, smoothstep(10., 1.2, depth) * .85 * (1. - rock * .5));
  col = mix(col, uRiver * (.88 + .25 * n), riv);
  float r = 1. - abs(noise(p * 5. + vec2(uTime * .12, -uTime * .07)) * 2. - 1.);
  col = mix(col, uFoam, smoothstep(.955, .99, r) * .4 * (1. - riv * .85));
  float shore = smoothstep(7., .6, depth) * (1. - riv);
  float wv = sin(depth * 2.4 + uTime * 1.6 + noise(p * 3.) * 4.);
  float wl = smoothstep(.8, .97, wv) * shore * (.55 + .45 * noise(p * 7. + uTime * .2));
  float edge = smoothstep(1.1, .25, depth) * (1. - riv * .8);
  col = mix(col, uFoam, clamp(wl * .9 + edge * (.55 + .45 * noise(p * 16. + uTime * .35)) + rock * smoothstep(3., .5, depth) * .5, 0., 1.));
  vec3 V = normalize(uCamPos - vW);
  vec3 N = normalize(vec3((noise(p * 9. + uTime * .4) - .5) * .32, 1., (noise(p * 9.3 - uTime * .37) - .5) * .32));
  float sp = pow(max(dot(reflect(-V, N), uSunDir), 0.), 140.);
  col += uSunCol * sp * 1.5 * (1. - uDark * .8);
  col += vec3(pow(noise(p * 38. + uTime * .7), 22.) * 1.1) * (1. - uDark);
  col *= 1. - uDark * .62;
  float fr = pow(1. - max(V.y, 0.), 4.);
  col = mix(col, uFogColor * 1.02, fr * .35);
  col = mix(col, uFogColor, smoothstep(uFogNear, uFogFar, distance(uCamPos, vW)));
  fragColor = vec4(col, 1.);
}
"""

    /* ---------- post: ink edges, watercolour wobble, paper grain, torn border, cloud mist ---------- */
    const val POST_VS = """
layout(location=0) in vec3 position;
out vec2 vUv;
void main(){ vUv = position.xy * .5 + .5; gl_Position = vec4(position.xy, 0., 1.); }
"""
    const val POST_FS = NOISE + """
uniform sampler2D tColor, tDepth; uniform vec2 uRes;
uniform float uNear, uFar, uTime, uPR, uSpeed, uFade, uNight, uMist, uHurt, uEdgeN, uEdgeF;
uniform vec3 uPaper, uInk, uMistCol;
in vec2 vUv;
out vec4 fragColor;
float invD(vec2 uv){ float z = texture(tDepth, uv).r; return (uFar - z * (uFar - uNear)) / (uNear * uFar); }
void main(){
  vec2 frag = vUv * uRes, px = 1. / uRes;
  vec2 wob = (vec2(noise(frag * .018 / uPR + 3.1), noise(frag * .018 / uPR + 7.7)) - .5) * px * 3. * uPR;
  vec3 col = texture(tColor, vUv + wob).rgb;
  col = col / (1. + col * .12);
  col = pow(clamp(col, 0., 1.), vec3(1. / 2.2));
  { float L = dot(col, vec3(.299, .587, .114)); col = clamp(mix(vec3(L), col, 1.22), 0., 1.); }
  { vec2 vq = vUv - .5; col *= 1. - .15 * smoothstep(.38, .9, length(vq * vec2(1.15, 1.))); col += vec3(.035, .02, 0.) * (1. - smoothstep(0., .9, length(vq - vec2(-.3, .35)))); }
  float o = max(1., uPR);
  float i0 = invD(vUv), il = invD(vUv - vec2(px.x * o, 0.)), ir = invD(vUv + vec2(px.x * o, 0.)), iu = invD(vUv + vec2(0., px.y * o)), id = invD(vUv - vec2(0., px.y * o));
  float lap = abs(il + ir + iu + id - 4. * i0) / max(i0, 1e-6);
  float dist = 1. / max(i0, 1e-6);
  float edge = smoothstep(.06, .22, lap) * (1. - smoothstep(uEdgeN, uEdgeF, dist));
  col = mix(col, uInk, edge * .8 * (1. - uMist * .8));
  vec2 fp = frag / uPR;
  float g = fbm(fp * .09), big = fbm(fp * .0035 + 2.);
  col *= .935 + .065 * g;
  col = mix(col, col * col * 1.12, .12 * (big - .35));
  float lum = dot(col, vec3(.299, .587, .114));
  float hl = abs(fract((fp.x - fp.y) / 5.5) - .5);
  col = mix(col, uInk, (1. - smoothstep(.06, .14, hl)) * (1. - smoothstep(.3, .52, lum)) * .15 * (1. - uNight * .7));
  if (uSpeed > 0.) { vec2 c = (vUv - .5) * vec2(uRes.x / uRes.y, 1.); float r = length(c), a = atan(c.y, c.x);
    float s = smoothstep(.72, .95, noise(vec2(a * 38., uTime * 9.))) * smoothstep(.24, .64, r); col = mix(col, vec3(1., .99, .9), s * uSpeed * .65); }
  if (uMist > 0.) { float mn = fbm(fp * .006 + vec2(uTime * .05, 0.)); col = mix(col, uMistCol * (.93 + .07 * mn), clamp(uMist * (.75 + .35 * mn), 0., 1.)); }
  if (uHurt > 0.) { vec2 vq = vUv - .5; col = mix(col, vec3(.84, .27, .18), uHurt * .35 * smoothstep(.25, .75, length(vq))); }
  vec2 sz = uRes / uPR;
  float e = min(min(fp.x, fp.y), min(sz.x - fp.x, sz.y - fp.y));
  float along = fp.x + fp.y * 1.37;
  float rag = 20. + 14. * fbm(vec2(along * .011, .5)) + 5. * noise(vec2(along * .16, 2.));
  float m = smoothstep(rag, rag + 9., e);
  col *= 1. - .13 * (1. - smoothstep(rag + 2., rag + 24., e)) * m;
  vec3 paper = uPaper * (.965 + .035 * g);
  col = mix(paper, col, m);
  col = mix(col, paper, uFade);
  fragColor = vec4(col, 1.);
}
"""
}
