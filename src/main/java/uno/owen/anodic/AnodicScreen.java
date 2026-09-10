package uno.owen.anodic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

public final class AnodicScreen extends Screen {
    private static final int ACCENT=0xFF83E0D3;
    private final Screen parent;
    private int left,top,panelWidth;
    public AnodicScreen(Screen parent){super(Component.literal("Anodic"));this.parent=parent;}
    @Override protected void init(){
        panelWidth=Math.min(320,width-24);left=(width-panelWidth)/2;top=Math.max(8,(height-206)/2);
        int gap=5,bw=(panelWidth-32-gap*2)/3;
        var modes=AnodicConfig.Preset.values();
        for(int i=0;i<modes.length;i++){
            var mode=modes[i];
            addRenderableWidget(new FlatButton(left+16+i*(bw+gap),top+61,bw,30,mode.label,()->{
                Anodic.config.preset=mode;if(mode!=AnodicConfig.Preset.OFF)Anodic.config.previous=mode;
                TemporalAA.settingsChanged();Anodic.save();
            },()->Anodic.config.preset==mode));
        }
        addRenderableWidget(new FlatButton(left+panelWidth-88,top+168,72,24,"Done",this::onClose,()->false));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){g.fill(0,0,width,height,0x88060B11);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(left,top,left+panelWidth,top+206,0xF2131C25);
        g.fill(left,top,left+panelWidth,top+2,ACCENT);
        g.text(font,"Anodic",left+16,top+17,0xFFF0F6F8,false);
        g.text(font,"Antialiasing",left+16,top+33,0xFF99AAB6,false);
        String title,description;
        switch(Anodic.config.preset){
            case OFF -> {title="Original rendering";description="Antialiasing is off. Your settings are kept.";}
            case PERFORMANCE -> {title="Fast, lightweight smoothing";description="Smooth edges with less GPU work. Some shimmer remains during movement.";}
            default -> {title="Steadier edges in motion";description="Temporal smoothing for the world. Keeps the hand and HUD sharp. Moving objects may show trails.";}
        }
        g.text(font,title,left+16,top+106,ACCENT,false);
        g.textWithWordWrap(font,Component.literal(description),left+16,top+122,panelWidth-32,0xFFCDD7DE);
        g.text(font,"Controls > Key Binds",left+16,top+176,0xFF99AAB6,false);
        if(!Anodic.saveError.isEmpty())g.text(font,"Settings could not be saved",left+16,top+151,0xFFFFB99A,false);
        else if(Anodic.config.preset==AnodicConfig.Preset.QUALITY&&!TemporalAA.ready())g.text(font,"Using Performance: temporal unavailable",left+16,top+151,0xFFFFB99A,false);
        super.extractRenderState(g,mx,my,dt);
    }
    private final class FlatButton extends Button {
        private final BooleanSupplier selected;
        FlatButton(int x,int y,int w,int h,String text,Runnable action,BooleanSupplier selected){
            super(x,y,w,h,Component.literal(text),b->action.run(),DEFAULT_NARRATION);this.selected=selected;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
            boolean chosen=selected.getAsBoolean(),hover=isHoveredOrFocused();
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),chosen?0xFF244F50:hover?0xFF344451:0xFF24323E);
            if(chosen||isFocused())g.fill(getX(),getY()+getHeight()-2,getX()+getWidth(),getY()+getHeight(),ACCENT);
            g.text(font,getMessage(),getX()+(getWidth()-font.width(getMessage()))/2,getY()+(getHeight()-8)/2,chosen?ACCENT:0xFFE0E9EE,false);
        }
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
