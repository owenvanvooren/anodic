import Foundation
import Metal

let device = MTLCreateSystemDefaultDevice()!
let queue = device.makeCommandQueue()!
let root = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let out = CommandLine.arguments.count > 2 ? CommandLine.arguments[2] : "validation"
try FileManager.default.createDirectory(atPath: out, withIntermediateDirectories: true)
let source = try String(contentsOfFile: root + "/src/main/resources/assets/anodic/shaders/anodic.metal", encoding: .utf8)
let library = try device.makeLibrary(source: source + "\nfragment half4 baseline_fs(VertexOut in [[stage_in]], texture2d<half> t [[texture(0)]]) { return t.sample(linearClamp, in.uv, level(0)); }", options: nil)
func pipeline(_ name: String) throws -> MTLRenderPipelineState {
    let d=MTLRenderPipelineDescriptor()
    d.vertexFunction=library.makeFunction(name: "anodic_vs")
    d.fragmentFunction=library.makeFunction(name:name)
    d.colorAttachments[0].pixelFormat = .bgra8Unorm
    return try device.makeRenderPipelineState(descriptor:d)
}
let aa=try pipeline("anodic_fs"), baseline=try pipeline("baseline_fs")
struct Settings { var relative: Float; var minimum: Float; var subpixel: Float; var steps: UInt32 }
let presets: [(String, Settings)] = [
    ("Performance",Settings(relative:0.125,minimum:0.0312,subpixel:0.25,steps:5))]
func texture(_ w:Int,_ h:Int,_ output:Bool)->MTLTexture {
    let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:w,height:h,mipmapped:false)
    d.storageMode = .shared; d.usage = output ? [.renderTarget] : [.shaderRead]
    return device.makeTexture(descriptor:d)!
}
func render(_ input:MTLTexture,_ output:MTLTexture,_ p:MTLRenderPipelineState,_ settings:Settings)->Double {
    let b=queue.makeCommandBuffer()!
    let d=MTLRenderPassDescriptor(); d.colorAttachments[0].texture=output
    d.colorAttachments[0].loadAction = .dontCare; d.colorAttachments[0].storeAction = .store
    let e=b.makeRenderCommandEncoder(descriptor:d)!
    e.setRenderPipelineState(p); e.setFragmentTexture(input,index:0)
    var s=settings; e.setFragmentBytes(&s,length:MemoryLayout<Settings>.stride,index:0)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); e.endEncoding()
    b.commit(); b.waitUntilCompleted()
    precondition(b.status == .completed, "GPU failed: \(String(describing:b.error))")
    return (b.gpuEndTime-b.gpuStartTime)*1000
}
func read(_ t:MTLTexture)->[UInt8] {
    var bytes=[UInt8](repeating:0,count:t.width*t.height*4)
    t.getBytes(&bytes,bytesPerRow:t.width*4,from:MTLRegionMake2D(0,0,t.width,t.height),mipmapLevel:0)
    return bytes
}
func ppm(_ name:String,_ data:[UInt8],_ w:Int,_ h:Int) throws {
    var d=Data("P6\n\(w) \(h)\n255\n".utf8)
    for i in stride(from:0,to:data.count,by:4) { d.append(contentsOf:[data[i+2],data[i+1],data[i]]) }
    try d.write(to:URL(fileURLWithPath:out+"/"+name+".ppm"))
}
let w=512,h=384
// Hard diagonal edges at several slopes, circle silhouettes and flat fields.
func scene(_ x:Double,_ y:Double)->Double {
    if y<96 { return x > 25 + y*2.37 ? 0.9 : 0.1 }
    if y<192 { return x > 190 + (y-96)*0.31 ? 0.8 : 0.2 }
    if y<288 { let dx=x-256,dy=y-240; return dx*dx+dy*dy<43*43 ? 0.9 : 0.1 }
    return 0.37
}
var inputBytes=[UInt8](repeating:255,count:w*h*4),reference=inputBytes
for y in 0..<h { for x in 0..<w {
    let value=UInt8((scene(Double(x)+0.5,Double(y)+0.5)*255).rounded())
    let i=(y*w+x)*4
    for c in 0..<3 { inputBytes[i+c]=value }
    var sum=0.0
    for sy in 0..<16 { for sx in 0..<16 { sum+=scene(Double(x)+(Double(sx)+0.5)/16,Double(y)+(Double(sy)+0.5)/16) } }
    let ideal=UInt8((sum/256*255).rounded()),j=((h-1-y)*w+x)*4
    for c in 0..<3 { reference[j+c]=ideal }
} }
let input=texture(w,h,false),output=texture(w,h,true)
input.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:inputBytes,bytesPerRow:w*4)
_ = render(input,output,baseline,presets[0].1)
let original=read(output)
try ppm("original",original,w,h); try ppm("reference",reference,w,h)
func mse(_ bytes:[UInt8])->Double {
    var error=0.0
    // Exclude horizontal scene-band boundaries, which are separate patterns.
    for y in 0..<h { if [96,192,288].contains(where:{abs((h-1-y)-$0)<3}) { continue }
        for x in 0..<w { let i=(y*w+x)*4; let d=Double(bytes[i])-Double(reference[i]); error+=d*d }
    }
    return error/Double(w*h)
}
var results:[[String:Any]]=[]
for (name,s) in presets {
    _ = render(input,output,aa,s); let filtered=read(output)
    for y in 0..<80 { for x in 0..<w { let i=(y*w+x)*4; precondition(filtered[i] == original[i],"Flat field altered") } }
    precondition(stride(from:3,to:filtered.count,by:4).allSatisfy {filtered[$0]==255},"Alpha changed")
    precondition(mse(filtered)<mse(original),"No improvement on synthetic edges")
    try ppm(name.lowercased(),filtered,w,h)
    results.append(["preset":name,"baselineMSE":mse(original),"filteredMSE":mse(filtered),"improvementPercent":100*(1-mse(filtered)/mse(original))])
}
// Exercise awkward sizes and resize churn, including single-pixel dimensions.
for (tw,th) in [(1,1),(1,31),(37,1),(853,479)] {
    let t=texture(tw,th,false),o=texture(tw,th,true)
    let bytes=[UInt8](repeating:255,count:tw*th*4)
    t.replace(region:MTLRegionMake2D(0,0,tw,th),mipmapLevel:0,withBytes:bytes,bytesPerRow:tw*4)
    _ = render(t,o,aa,presets[0].1)
    precondition(read(o).allSatisfy {$0==255},"Border/size failure")
}
var timing:[[String:Any]]=[]
for (tw,th) in [(1920,1080),(2560,1440),(3840,2160)] {
    let t=texture(tw,th,false),o=texture(tw,th,true)
    var bytes=[UInt8](repeating:255,count:tw*th*4)
    for y in 0..<th { for x in 0..<tw { let v:UInt8 = ((x+y/3)/24+(y+x/5)/24)%2==0 ? 30 : 220; for c in 0..<3 { bytes[(y*tw+x)*4+c]=v } } }
    t.replace(region:MTLRegionMake2D(0,0,tw,th),mipmapLevel:0,withBytes:bytes,bytesPerRow:tw*4)
    for _ in 0..<6 { _ = render(t,o,aa,presets[0].1) }
    var raw:[Double]=[],smooth:[Double]=[]
    for _ in 0..<40 { raw.append(render(t,o,baseline,presets[0].1)); smooth.append(render(t,o,aa,presets[0].1)) }
    raw.sort();smooth.sort()
    timing.append(["width":tw,"height":th,"baselineMedianMs":raw[20],"performanceMedianMs":smooth[20],"incrementalMs":smooth[20]-raw[20],"performanceP95Ms":smooth[38]])
}
let report:[String:Any]=["device":device.name,"quality":results,"timings":timing,"notes":"Offscreen synthetic workload; not Minecraft FPS or input latency. API validation changes timings if enabled."]
let json=try JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys])
try json.write(to:URL(fileURLWithPath:out+"/report.json"))
print(String(data:json,encoding:.utf8)!)
