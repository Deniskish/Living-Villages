package dev.livingvillages.client;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Pure geometry for the Village Ledger in GUI-scaled pixels. Every area is derived
 * from the actual screen size and measured tab labels, top to bottom:
 * header (title + Refresh), tabs (wrapped rows, or a compact selector when wrapping
 * would leave too little content), content viewport + scrollbar, footer (Back, status,
 * Close). Areas never intersect; {@link #rowAt} only answers inside the viewport.
 * No Minecraft types, so the model is unit-tested directly.
 */
public record LedgerLayout(int width,int height,boolean tooSmall,Rect title,Rect refresh,List<Rect> tabs,
                           boolean compactTabs,Rect previousTab,Rect tabLabel,Rect nextTab,
                           Rect content,Rect scrollbar,Rect back,Rect status,Rect close) {
    public record Rect(int x,int y,int width,int height) {
        public static final Rect NONE=new Rect(0,0,0,0);
        public int right() { return x+width; }
        public int bottom() { return y+height; }
        public boolean empty() { return width<=0||height<=0; }
        public boolean contains(double px,double py) { return !empty()&&px>=x&&px<right()&&py>=y&&py<bottom(); }
        public boolean intersects(Rect o) {
            return !empty()&&!o.empty()&&x<o.right()&&o.x<right()&&y<o.bottom()&&o.y<bottom();
        }
        public boolean inside(int w,int h) { return empty()||x>=0&&y>=0&&right()<=w&&bottom()<=h; }
    }
    public static final int MARGIN=8, GAP=4, BUTTON_HEIGHT=20, ROW_HEIGHT=12, TAB_PADDING=12, TAB_MIN_WIDTH=40,
            REFRESH_WIDTH=80, REFRESH_MIN_WIDTH=48, FOOTER_BUTTON_WIDTH=60, FOOTER_BUTTON_MIN_WIDTH=36,
            ARROW_WIDTH=20, SCROLLBAR_WIDTH=4, MIN_CONTENT_ROWS=3, MIN_STATUS_WIDTH=40;
    /** Below this scaled size only a message and Close are shown. */
    public static final int MIN_WIDTH=160, MIN_HEIGHT=110;

    public static LedgerLayout compute(int width,int height,List<Integer> tabLabelWidths) {
        int w=Math.max(0,width),h=Math.max(0,height);
        int footerY=h-GAP-BUTTON_HEIGHT;
        int footerButton=Math.clamp((w-2*MARGIN-GAP)/3,FOOTER_BUTTON_MIN_WIDTH,FOOTER_BUTTON_WIDTH);
        Rect close=new Rect(w-MARGIN-footerButton,footerY,footerButton,BUTTON_HEIGHT);
        if(w<MIN_WIDTH||h<MIN_HEIGHT) {
            Rect small=new Rect(Math.max(0,(w-footerButton)/2),Math.max(0,h-BUTTON_HEIGHT-2),Math.min(footerButton,w),Math.min(BUTTON_HEIGHT,h));
            return new LedgerLayout(w,h,true,new Rect(0,0,w,Math.max(0,small.y()-1)),Rect.NONE,List.of(),false,
                    Rect.NONE,Rect.NONE,Rect.NONE,Rect.NONE,Rect.NONE,Rect.NONE,Rect.NONE,small);
        }
        Rect back=new Rect(MARGIN,footerY,footerButton,BUTTON_HEIGHT);
        int statusX=back.right()+GAP,statusW=close.x()-GAP-statusX;
        Rect status=statusW>=MIN_STATUS_WIDTH?new Rect(statusX,footerY,statusW,BUTTON_HEIGHT):Rect.NONE;

        int headerY=GAP;
        int refreshW=Math.clamp(w/4,REFRESH_MIN_WIDTH,REFRESH_WIDTH);
        Rect refresh=new Rect(w-MARGIN-refreshW,headerY,refreshW,BUTTON_HEIGHT);
        Rect title=new Rect(MARGIN,headerY,Math.max(0,refresh.x()-GAP-MARGIN),BUTTON_HEIGHT);

        int tabsTop=title.bottom()+GAP,available=w-2*MARGIN,contentBottom=footerY-GAP;
        List<Rect> wrapped=new ArrayList<>();
        int x=MARGIN,y=tabsTop;
        for(int label:tabLabelWidths) {
            int tw=Math.clamp(label+TAB_PADDING,Math.min(TAB_MIN_WIDTH,available),available);
            if(x>MARGIN&&x+tw>MARGIN+available) { x=MARGIN;y+=BUTTON_HEIGHT+2; }
            wrapped.add(new Rect(x,y,tw,BUTTON_HEIGHT));x+=tw+2;
        }
        int tabsBottom=wrapped.isEmpty()?tabsTop:wrapped.getLast().bottom();
        boolean compact=!wrapped.isEmpty()&&contentBottom-(tabsBottom+GAP)<MIN_CONTENT_ROWS*ROW_HEIGHT
                &&wrapped.getLast().y()>tabsTop;
        Rect previous=Rect.NONE,label=Rect.NONE,next=Rect.NONE;
        List<Rect> tabs=wrapped;
        if(compact) {
            // One row: [<] current tab (n/N) [>]. Every tab stays reachable.
            tabs=List.of();
            previous=new Rect(MARGIN,tabsTop,ARROW_WIDTH,BUTTON_HEIGHT);
            next=new Rect(MARGIN+available-ARROW_WIDTH,tabsTop,ARROW_WIDTH,BUTTON_HEIGHT);
            label=new Rect(previous.right()+GAP,tabsTop,Math.max(0,next.x()-GAP-previous.right()-GAP),BUTTON_HEIGHT);
            tabsBottom=label.bottom();
        }
        int contentTop=tabsBottom+GAP;
        int contentHeight=Math.max(0,contentBottom-contentTop);
        contentHeight-=contentHeight%ROW_HEIGHT; // Whole rows only; no partial row under the footer.
        Rect content=new Rect(MARGIN,contentTop,Math.max(0,available-SCROLLBAR_WIDTH-2),contentHeight);
        Rect scrollbar=new Rect(content.right()+2,contentTop,SCROLLBAR_WIDTH,contentHeight);
        return new LedgerLayout(w,h,false,title,refresh,List.copyOf(tabs),compact,previous,label,next,content,scrollbar,back,status,close);
    }
    public int visibleRows() { return content.height()/ROW_HEIGHT; }
    /** Absolute row index under the pointer, or -1 outside the viewport/rows. */
    public int rowAt(double mouseX,double mouseY,int offset,int rowCount) {
        if(tooSmall||!content.contains(mouseX,mouseY))return -1;
        int index=(int)((mouseY-content.y())/ROW_HEIGHT);
        if(index<0||index>=visibleRows())return -1;
        int row=index+offset;
        return row<rowCount?row:-1;
    }
    public int rowY(int visibleIndex) { return content.y()+visibleIndex*ROW_HEIGHT; }
    public static int clampOffset(int offset,int rows,int visible) { return Math.clamp(offset,0,Math.max(0,rows-Math.max(0,visible))); }
    /** Scrollbar thumb inside the track, or NONE when everything fits. */
    public Rect thumb(int offset,int rows) {
        int visible=visibleRows();
        if(rows<=visible||scrollbar.empty())return Rect.NONE;
        int track=scrollbar.height(),size=Math.max(6,track*visible/rows);
        int travel=track-size,max=rows-visible;
        int y=scrollbar.y()+(max==0?0:(int)((long)travel*clampOffset(offset,rows,visible)/max));
        return new Rect(scrollbar.x(),y,scrollbar.width(),size);
    }
    /** Every non-empty area that must not overlap another. */
    public List<Rect> areas() {
        List<Rect> result=new ArrayList<>(List.of(title,refresh,previousTab,tabLabel,nextTab,content,scrollbar,back,status,close));
        result.addAll(tabs);
        result.removeIf(Rect::empty);
        return result;
    }
    /** Shortens text to the pixel width, appending an ellipsis when cut. */
    public static String fit(String text,int maxWidth,ToIntFunction<String> width) {
        if(text==null||maxWidth<=0)return "";
        if(width.applyAsInt(text)<=maxWidth)return text;
        String ellipsis="…";int limit=maxWidth-width.applyAsInt(ellipsis);
        if(limit<=0)return "";
        int lo=0,hi=text.length();
        while(lo<hi) { int mid=(lo+hi+1)>>>1;if(width.applyAsInt(text.substring(0,mid))<=limit)lo=mid;else hi=mid-1; }
        return text.substring(0,lo)+ellipsis;
    }
}
