package dev.livingvillages.client;
import dev.livingvillages.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class LedgerScreen extends Screen {
    private final LedgerView view;
    private String tab="Overview";
    private int offset;
    private LedgerView.Row selected;
    public LedgerScreen(LedgerView view) { super(Component.literal("Village Ledger"));this.view=view; }
    @Override protected void init() {
        int x=8;
        for(String name:view.tabs().keySet()) {
            int w=Math.min(82,(width-16)/view.tabs().size());
            addRenderableWidget(Button.builder(Component.literal(name),b->{tab=name;offset=0;selected=null;})
                    .bounds(x,30,w-2,20).build());x+=w;
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->PacketDistributor.sendToServer(new RefreshPayload(view.village())))
                .bounds(width-88,6,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->{selected=null;offset=0;})
                .bounds(8,height-25,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width-68,height-25,60,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xF018211B);
        graphics.drawString(font,view.name()+" — Village Ledger",10,12,0xF2D49B);
        List<String> lines=lines();int visible=Math.max(1,(height-90)/14);
        offset=Math.clamp(offset,0,Math.max(0,lines.size()-visible));
        for(int i=0;i<visible&&i+offset<lines.size();i++)
            graphics.drawString(font,font.plainSubstrByWidth(lines.get(i+offset),width-24),12,60+i*14,0xE0E8DE);
        graphics.drawString(font,"Scroll • click row for details • "+lines.size()+" rows",80,height-20,0xA0B8A0);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    private List<String> lines() {
        if(selected==null)return view.tabs().getOrDefault(tab,List.of()).stream().map(LedgerView.Row::title).toList();
        List<String> result=new ArrayList<>();result.add(selected.title());
        for(String line:selected.detail()) {
            for(var wrapped:font.split(Component.literal(line),Math.max(80,width-24)))
                result.add(flatten(wrapped));
        }
        return result;
    }
    private static String flatten(net.minecraft.util.FormattedCharSequence text) {
        StringBuilder result=new StringBuilder();text.accept((i,style,code)->{result.appendCodePoint(code);return true;});return result.toString();
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(y>=60&&y<height-35&&button==0&&selected==null) {
            int index=(int)((y-60)/14)+offset;var rows=view.tabs().getOrDefault(tab,List.of());
            if(index<rows.size()) { selected=rows.get(index);offset=0;return true; }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        offset=Math.max(0,offset-(int)vertical*3);return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
