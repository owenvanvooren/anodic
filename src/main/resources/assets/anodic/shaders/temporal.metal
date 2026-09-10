#include <metal_stdlib>
using namespace metal;
struct Out { float4 position [[position]]; float2 uv; };
struct Params { float4x4 reprojection; float4x4 skyReprojection; float2 jitterUV; float historyWeight; uint valid; };
constexpr sampler linearEdge(coord::normalized,address::clamp_to_edge,filter::linear);
constexpr sampler nearestEdge(coord::normalized,address::clamp_to_edge,filter::nearest);
// Cubic history reconstruction prevents repeated bilinear reprojection blur.
inline float4 weights(float x) {
    return float4(-.5*x+x*x-.5*x*x*x,1-2.5*x*x+1.5*x*x*x,
                  .5*x+2*x*x-1.5*x*x*x,-.5*x*x+.5*x*x*x);
}
inline float3 reconstruct(texture2d<half> t,float2 uv) {
    float2 size=float2(t.get_width(),t.get_height()),position=uv*size-.5;
    float2 base=floor(position),f=position-base;
    float4 wx=weights(f.x),wy=weights(f.y);
    float3 ax=float3(wx.x,wx.y+wx.z,wx.w),ay=float3(wy.x,wy.y+wy.z,wy.w);
    float3 tx=float3(base.x-.5,base.x+.5+wx.z/ax.y,base.x+2.5)/size.x;
    float3 ty=float3(base.y-.5,base.y+.5+wy.z/ay.y,base.y+2.5)/size.y;
    float3 result=0;
    for(int y=0;y<3;y++)for(int x=0;x<3;x++)
        result+=float3(t.sample(linearEdge,float2(tx[x],ty[y])).rgb)*ax[x]*ay[y];
    return result;
}
vertex Out taa_vs(uint id [[vertex_id]]) {
    // Native Metal texture orientation. Unlike final presentation, no Y flip.
    float2 pos[3]={float2(-1,1),float2(3,1),float2(-1,-3)};
    float2 uv[3]={float2(0,0),float2(2,0),float2(0,2)};
    return {float4(pos[id],0,1),uv[id]};
}
fragment half4 taa_fs(Out in [[stage_in]], texture2d<half> current [[texture(0)]],
    depth2d<float> depth [[texture(1)]], texture2d<half> history [[texture(2)]],constant Params& p [[buffer(0)]]) {
    float2 size=float2(current.get_width(),current.get_height()),px=1.0/size;
    float2 at=in.uv+p.jitterUV;
    half3 now=half3(clamp(reconstruct(current,at),0.0,1.0));
    // Reverse-Z foreground dilation keeps silhouette history ownership stable
    // as the jitter pattern alternates which side covers the nearest texel.
    float d=0;
    // Cover the cubic color reconstruction footprint, including its outer taps.
    for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++)
        d=max(d,depth.sample(nearestEdge,at+float2(x,y)*px));
    bool sky=d<=0;
    // Metallum flips vertex Y: native texture UV is (original NDC.xy+1)/2.
    float4 previous=(sky ? p.skyReprojection:p.reprojection)*float4(in.uv*2-1,d,1);
    float2 oldUV=previous.xy/max(previous.w,1e-8)*.5+.5;
    float expected=previous.z/max(previous.w,1e-8);
    float motion=length((oldUV-in.uv)*size);
    // A resting view can retain more history; movement returns to the normal
    // response immediately, while clipping still rejects changing content.
    float stationary=1-clamp(motion/.1,0.0,1.0);
    float weight=mix(p.historyWeight,.97,stationary);
    if (p.valid==0 || previous.w<=0 || any(oldUV<px*.5) || any(oldUV>1-px*.5) || (!sky && (expected<0 || expected>1))) return half4(now,half(d));
    half4 old=half4(half3(reconstruct(history,oldUV)),0);
    float oldDepth=float(history.sample(nearestEdge,oldUV).a);
    if (sky ? oldDepth>0 : (oldDepth<=0 || abs(oldDepth-expected)>max(0.00001,abs(expected)*.02))) return half4(now,half(d));
    // Variance clipping constrains camera-only history on moving objects,
    // animation, transparency and disocclusion. No entity velocity is invented.
    float3 sum=0,second=0,low=float3(now),high=float3(now);
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        float3 c=float3(current.sample(linearEdge,at+float2(x,y)*px).rgb);
        sum+=c;second+=c*c;low=min(low,c);high=max(high,c);
    }
    float3 mean=sum/9, sigma=sqrt(max(second/9-mean*mean,0.0));
    low=max(low,mean-sigma*1.25);high=min(high,mean+sigma*1.25);
    float3 clipped=clamp(float3(old.rgb),low,high);
    float difference=length(float3(old.rgb)-clipped);
    weight*=1-clamp(difference*3,0.0,.85);
    weight=min(weight,mix(.9+.07*stationary,.65,clamp(motion/24,0.0,1.0)));
    return half4(half3(mix(float3(now),clipped,weight)),half(d));
}
fragment half4 copy_fs(Out in [[stage_in]],texture2d<half> t [[texture(0)]]) {
    return half4(t.sample(nearestEdge,in.uv).rgb,1);
}
