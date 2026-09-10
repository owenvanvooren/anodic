#include <metal_stdlib>
using namespace metal;

// Spatial edge reconstruction in the existing presentation pass. Coordinates
// match Metallum 0.0.23's vertically flipped presentation, including Retina.
struct VertexOut { float4 position [[position]]; float2 uv; };
struct Settings { float relativeThreshold; float minimumThreshold; float subpixel; uint searchSteps; };
vertex VertexOut anodic_vs(uint id [[vertex_id]]) {
    const float2 p[3] = {float2(-1,1), float2(3,1), float2(-1,-3)};
    const float2 uv[3] = {float2(0,1), float2(2,1), float2(0,-1)};
    return {float4(p[id],0,1), uv[id]};
}
// Presentation textures are display-referred UNORM in this backend.
inline float luma(half3 c) { return float(dot(c, half3(0.299h,0.587h,0.114h))); }
constexpr sampler linearClamp(coord::normalized, address::clamp_to_edge, filter::linear);
inline float lum(texture2d<half> t, float2 uv) { return luma(t.sample(linearClamp, uv, level(0)).rgb); }
fragment half4 anodic_fs(VertexOut in [[stage_in]], texture2d<half> t [[texture(0)]], constant Settings& s [[buffer(0)]]) {
    float2 px = 1.0 / float2(t.get_width(), t.get_height());
    float2 uv = in.uv;
    half4 center = t.sample(linearClamp, uv, level(0));
    float m = luma(center.rgb);
    float n = lum(t, uv+float2(0,-px.y)), so = lum(t, uv+float2(0,px.y));
    float w = lum(t, uv+float2(-px.x,0)), e = lum(t, uv+float2(px.x,0));
    float lo = min(m,min(min(n,so),min(w,e))), hi = max(m,max(max(n,so),max(w,e)));
    float range = hi-lo;
    // Flat regions return exactly their original sample; no global sharpening.
    if (range < max(s.minimumThreshold, hi*s.relativeThreshold)) return center;
    float nw=lum(t,uv+float2(-px.x,-px.y)), ne=lum(t,uv+float2(px.x,-px.y));
    float sw=lum(t,uv+float2(-px.x,px.y)), se=lum(t,uv+float2(px.x,px.y));
    float horizontal = abs(nw+sw-2*w)+2*abs(n+so-2*m)+abs(ne+se-2*e);
    float vertical = abs(nw+ne-2*n)+2*abs(w+e-2*m)+abs(sw+se-2*so);
    bool alongX = horizontal >= vertical;
    float a = alongX ? n : w, b = alongX ? so : e;
    bool negative = abs(a-m) >= abs(b-m);
    float neighbor = negative ? a : b;
    float gradient = abs(neighbor-m);
    float average = (neighbor+m)*0.5;
    float2 normal = alongX ? float2(0,px.y) : float2(px.x,0);
    normal *= negative ? -1.0 : 1.0;
    float2 tangent = alongX ? float2(px.x,0) : float2(0,px.y);
    float2 edgeUV = uv+normal*0.5;
    float distA=1, distB=1;
    float deltaA=lum(t,edgeUV-tangent)-average, deltaB=lum(t,edgeUV+tangent)-average;
    float cutoff = gradient*0.25;
    bool doneA=abs(deltaA)>=cutoff, doneB=abs(deltaB)>=cutoff;
    // Bounded edge search, compile-time ceiling. Only edge pixels do this work.
    for (uint i=0; i<10; ++i) {
        if (i>=s.searchSteps || (doneA && doneB)) break;
        float step = i<4 ? 1.5 : (i<7 ? 2.0 : 4.0);
        if (!doneA) { distA+=step; deltaA=lum(t,edgeUV-tangent*distA)-average; doneA=abs(deltaA)>=cutoff; }
        if (!doneB) { distB+=step; deltaB=lum(t,edgeUV+tangent*distB)-average; doneB=abs(deltaB)>=cutoff; }
    }
    bool nearerA=distA<distB;
    float endpoint=nearerA ? deltaA : deltaB;
    bool found=nearerA ? doneA : doneB;
    float edgeBlend=(found && ((endpoint<0)!=(m<average))) ? 0.5-min(distA,distB)/(distA+distB) : 0.0;
    float neighborhood=(2*(n+so+w+e)+nw+ne+sw+se)/12.0;
    float sub=clamp(abs(neighborhood-m)/max(range,1e-6),0.0,1.0);
    sub=sub*sub*(3-2*sub); sub=sub*sub*s.subpixel;
    half4 result=t.sample(linearClamp,uv+normal*max(edgeBlend,sub),level(0));
    result.a=center.a;
    return result;
}
