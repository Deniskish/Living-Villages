package dev.livingvillages.client;
import dev.livingvillages.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class LedgerScreen extends Screen {
    private LedgerView view;
    private String tab="Overview";
    private int offset;
    private LedgerView.Row selected;
    private List<String> cachedLines;
    private int contentTop=60;
    public LedgerScreen(LedgerView view) { super(Component.literal("Village Ledger"));this.view=view; }
    @Override protected void init() {
        cachedLines=null;
        int x=8,y=30;
        for(String name:view.tabs().keySet()) {
            int w=Math.max(64,font.width(name)+12);
            if(x+w>width-8) { x=8;y+=22; }
            addRenderableWidget(Button.builder(Component.literal(name),b->{tab=name;offset=0;selected=null;cachedLines=null;})
                    .bounds(x,y,w-2,20).build());x+=w;
        }
        contentTop=y+30;
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->PacketDistributor.sendToServer(new RefreshPayload(view.village())))
                .bounds(width-88,6,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->{selected=null;offset=0;cachedLines=null;})
                .bounds(8,height-25,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width-68,height-25,60,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xF018211B);
        graphics.drawString(font,font.plainSubstrByWidth(view.name()+" — Village Ledger",Math.max(0,width-110)),10,12,0xF2D49B);
        List<String> lines=lines();int visible=Math.max(0,(height-contentTop-35)/14);
        offset=Math.clamp(offset,0,Math.max(0,lines.size()-visible));
        for(int i=0;i<visible&&i+offset<lines.size();i++)
            graphics.drawString(font,font.plainSubstrByWidth(lines.get(i+offset),Math.max(0,width-24)),12,contentTop+i*14,0xE0E8DE);
        graphics.drawString(font,font.plainSubstrByWidth(lines.size()+" rows • scroll / select",Math.max(0,width-156)),80,height-20,0xA0B8A0);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    private List<String> lines() {
        if(cachedLines!=null)return cachedLines;
        if(selected==null)return cachedLines=view.tabs().getOrDefault(tab,List.of()).stream().map(LedgerView.Row::title).toList();
        List<String> result=new ArrayList<>();result.add(selected.title());
        for(String line:selected.detail()) {
            for(var wrapped:font.split(Component.literal(line),Math.max(80,width-24)))
                result.add(flatten(wrapped));
        }
        return cachedLines=result;
    }
    private static String flatten(net.minecraft.util.FormattedCharSequence text) {
        StringBuilder result=new StringBuilder();text.accept((i,style,code)->{result.appendCodePoint(code);return true;});return result.toString();
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(y>=contentTop&&y<height-35&&button==0&&selected==null) {
            int index=(int)((y-contentTop)/14)+offset;var rows=view.tabs().getOrDefault(tab,List.of());
            if(index>=0&&index<rows.size()) { selected=rows.get(index);offset=0;cachedLines=null;return true; }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        offset=Math.max(0,offset-(int)vertical*3);return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    public void refresh(LedgerView replacement) {
        if(!view.village().equals(replacement.village()))return;
        view=replacement;selected=null;cachedLines=null;
        if(!view.tabs().containsKey(tab))tab="Overview";
        rebuildWidgets();
    }
}
