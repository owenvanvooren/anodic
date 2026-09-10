package uno.owen.anodic;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MetallumContractTest {
    @Test void releasedMetallumHasExactlyOnePresentationPipelineBind() throws Exception {
        var calls=new AtomicInteger();var init=new AtomicInteger();var close=new AtomicInteger();
        try (var in=getClass().getResourceAsStream("/com/metallum/mtl/MTLBuiltinPipelines.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                    if (name.equals("init") && desc.equals("(Lcom/metallum/mtl/MTLDevice;)V")) init.incrementAndGet();
                    if (name.equals("close") && desc.equals("()V")) close.incrementAndGet();
                    if (!name.equals("encodePresentTextureToDrawable")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                            if (owner.equals("com/metallum/mtl/MTLRenderCommandEncoder") && name.equals("setRenderPipelineState") && descriptor.equals("(Ljava/lang/foreign/MemorySegment;)V")) calls.incrementAndGet();
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,init.get());assertEquals(1,close.get());assertEquals(1,calls.get());
    }
}
