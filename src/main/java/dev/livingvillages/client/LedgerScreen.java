package dev.livingvillages.client;
import dev.livingvillages.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Server snapshot viewer. All geometry comes from {@link LedgerLayout}; nothing is placed by hand. */
public final class LedgerScreen extends Screen {
    private static final int BACKGROUND=0xF018211B, PANEL=0x60000000, TITLE=0xF2D49B, TEXT=0xE0E8DE, MUTED=0xA0B8A0,
            OK=0xA8E6A1, MISSING=0xF2A0A0, WARNING=0xF2D49B, ACTIVE=0xA0C8F0, HOVER=0x30FFFFFF, TRACK=0x40FFFFFF, THUMB=0xC0D0E0C8;
    private LedgerView view;
    private String tab="Overview";
    private int offset,listOffset;
    private LedgerView.Row selected;
    private List<String> cachedLines;
    private LedgerLayout layout;
    private final Map<String,Button> tabButtons=new LinkedHashMap<>();
    private Button back;
    public LedgerScreen(LedgerView view) { super(Component.literal("Village Ledger"));this.view=view; }
    public UUID village() { return view.village(); }
    @Override protected void init() {
        cachedLines=null;tabButtons.clear();back=null;
        List<String> names=List.copyOf(view.tabs().keySet());
        if(!names.contains(tab))tab=names.isEmpty()?"Overview":names.getFirst();
        layout=LedgerLayout.compute(width,height,names.stream().map(font::width).toList());
        if(layout.tooSmall()) { addRenderableWidget(button("Close",layout.close(),b->onClose()));return; }
        addRenderableWidget(button("Refresh",layout.refresh(),b->PacketDistributor.sendToServer(new RefreshPayload(view.village()))));
        if(layout.compactTabs()) {
            addRenderableWidget(button("<",layout.previousTab(),b->cycle(names,-1)));
            addRenderableWidget(button(">",layout.nextTab(),b->cycle(names,1)));
        } else for(int i=0;i<names.size();i++) {
            String name=names.get(i);var rect=layout.tabs().get(i);
            tabButtons.put(name,addRenderableWidget(button(LedgerLayout.fit(name,rect.width()-6,font::width),rect,b->open(name))));
        }
        back=addRenderableWidget(button("Back",layout.back(),b->back()));
        addRenderableWidget(button("Close",layout.close(),b->onClose()));
        updateButtons();
    }
    private static Button button(String label,LedgerLayout.Rect r,Button.OnPress action) {
        return Button.builder(Component.literal(label),action).bounds(r.x(),r.y(),r.width(),r.height()).build();
    }
    private void updateButtons() {
        tabButtons.forEach((name,button)->button.active=!name.equals(tab));
        if(back!=null)back.active=selected!=null;
    }
    private void open(String name) { tab=name;offset=0;listOffset=0;selected=null;cachedLines=null;updateButtons(); }
    private void cycle(List<String> names,int direction) {
        if(names.isEmpty())return;
        open(names.get(Math.floorMod(names.indexOf(tab)+direction,names.size())));
    }
    private void back() { selected=null;offset=listOffset;cachedLines=null;updateButtons(); }
    // Screen.render draws the (blurred) menu background before widgets, so the panel belongs
    // here; content is drawn after super.render and therefore always on top of the background.
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,BACKGROUND);
        if(layout!=null&&!layout.tooSmall()) {
            var c=layout.content();
            graphics.fill(c.x()-2,c.y()-2,layout.scrollbar().right()+1,c.bottom()+2,PANEL);
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick);
        if(layout.tooSmall()) {
            var area=layout.title();int y=area.y()+4;
            for(String line:List.of("Village Ledger","Window too small:","enlarge it or lower GUI Scale")) {
                if(y+font.lineHeight>area.bottom())break;
                graphics.drawString(font,LedgerLayout.fit(line,area.width()-8,font::width),area.x()+4,y,TEXT);y+=font.lineHeight+2;
            }
            return;
        }
        var title=layout.title();
        graphics.drawString(font,LedgerLayout.fit(view.name()+" — Village Ledger",title.width(),font::width),
                title.x(),title.y()+(title.height()-font.lineHeight)/2+1,TITLE);
        if(layout.compactTabs()) {
            var names=List.copyOf(view.tabs().keySet());var label=layout.tabLabel();
            String text=LedgerLayout.fit(tab+" ("+(names.indexOf(tab)+1)+"/"+names.size()+")",label.width(),font::width);
            graphics.drawString(font,text,label.x()+(label.width()-font.width(text))/2,label.y()+(label.height()-font.lineHeight)/2+1,TITLE);
        } else {
            var current=tabButtons.get(tab);
            if(current!=null)graphics.fill(current.getX()+2,current.getY()+current.getHeight()-2,current.getX()+current.getWidth()-2,current.getY()+current.getHeight(),TITLE);
        }
        List<String> lines=lines();
        int visible=layout.visibleRows();
        offset=LedgerLayout.clampOffset(offset,lines.size(),visible);
        var c=layout.content();
        int hovered=selected==null?layout.rowAt(mouseX,mouseY,offset,lines.size()):-1;
        graphics.enableScissor(c.x(),c.y(),c.right(),c.bottom());
        for(int i=0;i<visible&&i+offset<lines.size();i++) {
            int y=layout.rowY(i);String line=lines.get(i+offset);
            if(i+offset==hovered)graphics.fill(c.x(),y,c.right(),y+LedgerLayout.ROW_HEIGHT,HOVER);
            graphics.drawString(font,LedgerLayout.fit(line,c.width()-4,font::width),c.x()+2,y+2,
                    selected!=null&&i+offset==0?TITLE:color(line));
        }
        graphics.disableScissor();
        var track=layout.scrollbar();var thumb=layout.thumb(offset,lines.size());
        if(!thumb.empty()) {
            graphics.fill(track.x(),track.y(),track.right(),track.bottom(),TRACK);
            graphics.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),THUMB);
        }
        var status=layout.status();
        if(!status.empty()) {
            int last=Math.min(lines.size(),offset+visible);
            String text=lines.isEmpty()?"No entries":(offset+1)+"–"+last+" of "+lines.size()+(selected==null?" • click a row for details":" • Back returns to list");
            text=LedgerLayout.fit(text,status.width(),font::width);
            graphics.drawString(font,text,status.x()+(status.width()-font.width(text))/2,status.y()+(status.height()-font.lineHeight)/2+1,MUTED);
        }
    }
    /** Text markers carry the meaning; colour only reinforces it. */
    private static int color(String line) {
        String t=line.stripLeading();
        if(t.startsWith("[✓]"))return OK;
        if(t.startsWith("[✗]"))return MISSING;
        if(t.startsWith("[!]"))return WARNING;
        if(t.startsWith("[~]"))return ACTIVE;
        if(t.startsWith("== "))return TITLE;
        return TEXT;
    }
    private List<LedgerView.Row> rows() { return view.tabs().getOrDefault(tab,List.of()); }
    private List<String> lines() {
        if(cachedLines!=null)return cachedLines;
        if(selected==null)return cachedLines=rows().stream().map(LedgerView.Row::title).toList();
        int wrap=Math.max(40,layout.content().width()-4);
        List<String> result=new ArrayList<>();
        for(String line:java.util.stream.Stream.concat(java.util.stream.Stream.of(selected.title()),selected.detail().stream()).toList()) {
            var parts=font.split(Component.literal(line),wrap);
            if(parts.isEmpty())result.add("");
            for(var wrapped:parts)result.add(flatten(wrapped));
        }
        return cachedLines=result;
    }
    private static String flatten(net.minecraft.util.FormattedCharSequence text) {
        StringBuilder result=new StringBuilder();text.accept((i,style,code)->{result.appendCodePoint(code);return true;});return result.toString();
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button))return true;
        if(button!=0||selected!=null||layout.tooSmall())return false;
        int index=layout.rowAt(x,y,offset,rows().size());
        if(index<0)return false;
        selected=rows().get(index);listOffset=offset;offset=0;cachedLines=null;updateButtons();
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(layout.tooSmall()||!layout.content().contains(x,y))return super.mouseScrolled(x,y,horizontal,vertical);
        offset=LedgerLayout.clampOffset(offset-(int)Math.signum(vertical)*3,lines().size(),layout.visibleRows());
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    /** Keeps the tab; re-selects the same row (stable key) when it still exists. */
    public void refresh(LedgerView replacement) {
        if(!view.village().equals(replacement.village()))return;
        view=replacement;cachedLines=null;
        if(!view.tabs().containsKey(tab)) { tab="Overview";selected=null;offset=0;listOffset=0; }
        else if(selected!=null) {
            String key=selected.key(),title=selected.title();
            selected=rows().stream().filter(r->key!=null&&!key.isEmpty()?key.equals(r.key()):title.equals(r.title())).findFirst().orElse(null);
            if(selected==null)offset=listOffset;
        }
        rebuildWidgets();
    }
}
