import Foundation
import Metal
import simd
let root=CommandLine.arguments[1],out=CommandLine.arguments[2]
try FileManager.default.createDirectory(atPath:out,withIntermediateDirectories:true)
let dev=MTLCreateSystemDefaultDevice()!,queue=dev.makeCommandQueue()!
let src=try String(contentsOfFile:root+"/src/main/resources/assets/anodic/shaders/temporal.metal",encoding:.utf8)
let lib=try dev.makeLibrary(source:src,options:nil)
let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=lib.makeFunction(name:"taa_vs");desc.fragmentFunction=lib.makeFunction(name:"taa_fs");desc.colorAttachments[0].pixelFormat = .rgba16Float
let pipe=try dev.makeRenderPipelineState(descriptor:desc)
let w=256,h=192
func texture(_ format:MTLPixelFormat,_ usage:MTLTextureUsage)->MTLTexture {
 let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:format,width:w,height:h,mipmapped:false)
 d.usage=usage;d.storageMode = .shared;return dev.makeTexture(descriptor:d)!
}
let color=texture(.rgba8Unorm,.shaderRead),depth=texture(.depth32Float,[.shaderRead,.renderTarget])
let history=[texture(.rgba16Float,[.shaderRead,.renderTarget]),texture(.rgba16Float,[.shaderRead,.renderTarget])]
struct Params {var reprojection:simd_float4x4;var skyReprojection:simd_float4x4=matrix_identity_float4x4;var jitter:SIMD2<Float>;var weight:Float;var valid:UInt32}
func halton(_ index:Int,_ base:Int)->Float {var i=index,f:Float=1,r:Float=0;while i>0 {f/=Float(base);r+=f*Float(i%base);i/=base};return r}
func scene(_ x:Float,_ y:Float)->Float {return x>70+y*0.37 ? 0.85:0.12}
var initial=[UInt16](repeating:0,count:w*h*4)
for t in history {t.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&initial,bytesPerRow:w*8)}
var source=[UInt8](repeating:255,count:w*h*4),depths=[Float](repeating:0.5,count:w*h)
var index=0,plainError=0.0,taaError=0.0,samples=0
var times:[Double]=[]
func encode(_ params:Params)->[UInt16] {
 let cb=queue.makeCommandBuffer()!,d=MTLRenderPassDescriptor();d.colorAttachments[0].texture=history[index];d.colorAttachments[0].loadAction = .dontCare;d.colorAttachments[0].storeAction = .store
 let e=cb.makeRenderCommandEncoder(descriptor:d)!;e.setRenderPipelineState(pipe);e.setFragmentTexture(color,index:0);e.setFragmentTexture(depth,index:1);e.setFragmentTexture(history[1-index],index:2)
 var p=params;e.setFragmentBytes(&p,length:MemoryLayout<Params>.stride,index:0);e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);e.endEncoding();cb.commit();cb.waitUntilCompleted()
 precondition(cb.status == .completed,"GPU error \(String(describing:cb.error))")
 times.append((cb.gpuEndTime-cb.gpuStartTime)*1000)
 var data=[UInt16](repeating:0,count:w*h*4);history[index].getBytes(&data,bytesPerRow:w*8,from:MTLRegionMake2D(0,0,w,h),mipmapLevel:0);index=1-index;return data
}
var last:[UInt16]=[]
for frame in 0..<48 {
 let camera=Float(frame)*0.23,jx=halton(frame%8+1,2)-0.5,jy=halton(frame%8+1,3)-0.5
 for y in 0..<h {for x in 0..<w {let v=UInt8((scene(Float(x)+0.5+camera-jx,Float(y)+0.5-jy)*255).rounded());for c in 0..<3 {source[(y*w+x)*4+c]=v}}}
 color.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&source,bytesPerRow:w*4)
 depth.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&depths,bytesPerRow:w*4)
 var matrix=matrix_identity_float4x4;matrix.columns.3.x=2*0.23/Float(w)
 last=encode(Params(reprojection:matrix,jitter:SIMD2(jx/Float(w),jy/Float(h)),weight:0.9,valid:frame==0 ? 0:1))
 if frame>=16 {for y in 4..<h-4 {for x in 4..<w-4 {
  var ideal:Float=0;for sy in 0..<8 {for sx in 0..<8 {ideal+=scene(Float(x)+Float(sx+1)/9+camera,Float(y)+Float(sy+1)/9)/64}}
  let raw=scene(Float(x)+0.5+camera,Float(y)+0.5),filtered=Float(Float16(bitPattern:last[(y*w+x)*4]))
  precondition(filtered.isFinite);plainError+=pow(Double(raw-ideal),2);taaError+=pow(Double(filtered-ideal),2);samples+=1
 }}}
}
precondition(taaError<plainError,"Temporal AA did not improve moving diagonal coverage: \(taaError) vs \(plainError)")
// A new foreground surface has unrelated bright history at the same pixel.
// Depth rejection must select the current image exactly in its flat interior.
for i in stride(from:0,to:source.count,by:4){source[i]=26;source[i+1]=26;source[i+2]=26}
depths=[Float](repeating:0.8,count:w*h)
color.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&source,bytesPerRow:w*4)
depth.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&depths,bytesPerRow:w*4)
let disoccluded=encode(Params(reprojection:matrix_identity_float4x4,jitter:.zero,weight:0.9,valid:1))
for i in stride(from:0,to:disoccluded.count,by:4){precondition(abs(Float(Float16(bitPattern:disoccluded[i]))-26.0/255)<0.001,"Disocclusion trail")}
// Cut/reset discards history, even without a depth change.
for i in stride(from:0,to:source.count,by:4){source[i]=180;source[i+1]=180;source[i+2]=180}
color.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&source,bytesPerRow:w*4)
let reset=encode(Params(reprojection:matrix_identity_float4x4,jitter:.zero,weight:0.9,valid:0))
for i in stride(from:0,to:reset.count,by:4){precondition(abs(Float(Float16(bitPattern:reset[i]))-180.0/255)<0.001,"Reset retained history")}
let report:[String:Any]=["device":dev.name,"frames":48,"unfilteredMSE":plainError/Double(samples),"temporalMSE":taaError/Double(samples),"improvementPercent":100*(1-taaError/plainError),"disocclusion":"pass","reset":"pass","notes":"Synthetic translating plane, camera reprojection, 8-phase jitter. Not a Minecraft benchmark or object-motion test."]
let data=try JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys]);try data.write(to:URL(fileURLWithPath:out+"/temporal-report.json"));print(String(data:data,encoding:.utf8)!)
// Fixed camera: changing sample positions must not make stationary edges crawl.
// Include clear-depth sky and a foreground/background depth boundary.
var stationaryResults:[[String:Any]]=[]
for kind in ["surface", "sky", "silhouette", "thin-line"] {
 var values=[[Float]]()
 for frame in 0..<128 {
  let jx=halton(frame%8+1,2)-0.5,jy=halton(frame%8+1,3)-0.5
  for y in 0..<h {for x in 0..<w {
   let sx=Float(x)+0.5-jx,sy=Float(y)+0.5-jy
   let v:Float=kind == "thin-line" ? (abs(sx-70-sy*0.37)<0.35 ? 0.85:0.12):scene(sx,sy)
   for c in 0..<3 {source[(y*w+x)*4+c]=UInt8((v*255).rounded())}
   depths[y*w+x]=kind == "sky" ? 0 : ((kind == "silhouette" || kind == "thin-line") && v<0.5 ? 0.1:0.5)
  }}
  color.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&source,bytesPerRow:w*4)
  depth.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&depths,bytesPerRow:w*4)
  let output=encode(Params(reprojection:matrix_identity_float4x4,jitter:SIMD2(jx/Float(w),jy/Float(h)),weight:0.9,valid:frame==0 ? 0:1))
  if frame>=112 {values.append(stride(from:0,to:output.count,by:4).map{Float(Float16(bitPattern:output[$0]))})}
 }
 var maxRange:Float=0
 for y in 4..<h-4 {for x in 4..<w-4 {
  let sequence=values.map{$0[y*w+x]};maxRange=max(maxRange,sequence.max()!-sequence.min()!)
 }}
 stationaryResults.append(["scene":kind,"maxTemporalRange":maxRange])
 print("Stationary \(kind): \(maxRange)")
 let limit:Float=kind == "thin-line" ? 0.035:0.025
 precondition(maxRange<limit,"Stationary \(kind) flickers across jitter phases: \(maxRange)")
 if kind == "thin-line" {
  // Stability must not be achieved by erasing the line. Check retained signal
  // against its analytical integrated coverage in the interior rows.
  var signal:Float=0
  for y in 4..<h-4 {for x in 4..<w-4 {signal+=values.last![y*w+x]-31.0/255}}
  let idealSignal:Float=0.7*Float(h-8)*(0.85-0.12)
  precondition(signal>idealSignal*0.6,"Temporal filtering erased the thin line")
  print("Thin-line retained signal: \(signal/idealSignal)")
  // Remove the line without a depth change: neighborhood clipping must still
  // reject stale history, even with the increased resting history weight.
  for i in stride(from:0,to:source.count,by:4){source[i]=31;source[i+1]=31;source[i+2]=31}
  color.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:&source,bytesPerRow:w*4)
  let removed=encode(Params(reprojection:matrix_identity_float4x4,jitter:.zero,weight:0.9,valid:1))
  for i in stride(from:0,to:removed.count,by:4){precondition(abs(Float(Float16(bitPattern:removed[i]))-31.0/255)<0.001,"Removed line left a trail")}
 }
}
let stationaryData=try JSONSerialization.data(withJSONObject:stationaryResults,options:[.prettyPrinted,.sortedKeys])
try stationaryData.write(to:URL(fileURLWithPath:out+"/stationary-report.json"));print(String(data:stationaryData,encoding:.utf8)!)
if CommandLine.arguments.contains("--correctness-only") {exit(0)}
// Measure both temporal resolve and copy-back; no CPU readback in timed passes.
let copyDesc=MTLRenderPipelineDescriptor();copyDesc.vertexFunction=lib.makeFunction(name:"taa_vs");copyDesc.fragmentFunction=lib.makeFunction(name:"copy_fs");copyDesc.colorAttachments[0].pixelFormat = .rgba8Unorm
let copyPipe=try dev.makeRenderPipelineState(descriptor:copyDesc)
func benchmark(_ width:Int,_ height:Int)->[String:Any] {
 func make(_ format:MTLPixelFormat,_ usage:MTLTextureUsage)->MTLTexture {
  let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:format,width:width,height:height,mipmapped:false);d.storageMode = .shared;d.usage=usage;return dev.makeTexture(descriptor:d)!
 }
 let input=make(.rgba8Unorm,.shaderRead),d=make(.depth32Float,[.shaderRead,.renderTarget]),output=make(.rgba8Unorm,.renderTarget)
 let buffers=[make(.rgba16Float,[.shaderRead,.renderTarget]),make(.rgba16Float,[.shaderRead,.renderTarget])]
 var bytes=[UInt8](repeating:255,count:width*height*4),z=[Float](repeating:0.5,count:width*height)
 for y in 0..<height {for x in 0..<width {let v:UInt8=((x+y/3)/24+(y+x/5)/24)%2==0 ? 30:220;for c in 0..<3 {bytes[(y*width+x)*4+c]=v}}}
 input.replace(region:MTLRegionMake2D(0,0,width,height),mipmapLevel:0,withBytes:&bytes,bytesPerRow:width*4)
 d.replace(region:MTLRegionMake2D(0,0,width,height),mipmapLevel:0,withBytes:&z,bytesPerRow:width*4)
 var durations:[Double]=[]
 for frame in 0..<36 {
  let cb=queue.makeCommandBuffer()!
  if frame==0 {let clear=MTLRenderPassDescriptor();clear.colorAttachments[0].texture=buffers[1];clear.colorAttachments[0].loadAction = .clear;clear.colorAttachments[0].storeAction = .store;cb.makeRenderCommandEncoder(descriptor:clear)!.endEncoding()}
  let pass=MTLRenderPassDescriptor();pass.colorAttachments[0].texture=buffers[frame%2];pass.colorAttachments[0].loadAction = .dontCare;pass.colorAttachments[0].storeAction = .store
  let e=cb.makeRenderCommandEncoder(descriptor:pass)!;e.setRenderPipelineState(pipe);e.setFragmentTexture(input,index:0);e.setFragmentTexture(d,index:1);e.setFragmentTexture(buffers[1-frame%2],index:2)
  var p=Params(reprojection:matrix_identity_float4x4,jitter:SIMD2((halton(frame%8+1,2)-0.5)/Float(width),(halton(frame%8+1,3)-0.5)/Float(height)),weight:0.9,valid:frame==0 ? 0:1)
  e.setFragmentBytes(&p,length:MemoryLayout<Params>.stride,index:0);e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);e.endEncoding()
  let copy=MTLRenderPassDescriptor();copy.colorAttachments[0].texture=output;copy.colorAttachments[0].loadAction = .dontCare;copy.colorAttachments[0].storeAction = .store
  let ce=cb.makeRenderCommandEncoder(descriptor:copy)!;ce.setRenderPipelineState(copyPipe);ce.setFragmentTexture(buffers[frame%2],index:0);ce.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);ce.endEncoding()
  cb.commit();cb.waitUntilCompleted();precondition(cb.status == .completed)
  if frame>=6 {durations.append((cb.gpuEndTime-cb.gpuStartTime)*1000)}
 }
 durations.sort();return ["width":width,"height":height,"qualityMedianMs":durations[15],"qualityP95Ms":durations[28],"historyMiB":Double(width*height*16)/1048576]
}
let benchmarks=[[1920,1080],[2560,1440],[3840,2160]].map {size in autoreleasepool {benchmark(size[0],size[1])}}
let timingData=try JSONSerialization.data(withJSONObject:["device":dev.name,"timings":benchmarks,"notes":"Offscreen two-pass Quality cost, excludes upstream render and presentation. Different workload behavior from live gameplay; not an FPS/latency benchmark."],options:[.prettyPrinted,.sortedKeys])
try timingData.write(to:URL(fileURLWithPath:out+"/temporal-timing.json"));print(String(data:timingData,encoding:.utf8)!)
