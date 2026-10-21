package dev.livingvillages.client;
import dev.livingvillages.client.LedgerLayout.Rect;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

class LedgerLayoutTest {
    /** Approximation of the vanilla font: 6 px per character including spacing. */
    static final ToIntFunction<String> FONT=s->s.length()*6;
    static final List<String> TABS=List.of("Overview","Citizens","Resources","Buildings","Plans","Progression","Chronicle","Diagnostics");
    static final List<Integer> WIDTHS=TABS.stream().map(FONT::applyAsInt).toList();
    /** Window.calculateScale: requested scale (0 = Auto) is capped so the GUI keeps at least 320×240. */
    static int scale(int requested,int width,int height) {
        int i=1;
        while(i!=requested&&i<width&&i<height&&width/(i+1)>=320&&height/(i+1)>=240)i++;
        return i;
    }
    static int scaled(int physical,int scale) { return (int)Math.ceil(physical/(double)scale); }
    static void assertSound(LedgerLayout l,String label) { assertSound(l,label,TABS.size()); }
    static void assertSound(LedgerLayout l,String label,int tabCount) {
        List<Rect> areas=l.areas();
        for(Rect r:areas)assertTrue(r.inside(l.width(),l.height()),label+": "+r+" outside "+l.width()+"x"+l.height());
        for(int i=0;i<areas.size();i++)for(int j=i+1;j<areas.size();j++)
            assertFalse(areas.get(i).intersects(areas.get(j)),label+": "+areas.get(i)+" overlaps "+areas.get(j));
        if(l.tooSmall())return;
        assertTrue(l.compactTabs()||l.tabs().size()==tabCount,label+": every tab has a button or the compact selector");
        if(l.compactTabs())assertFalse(l.previousTab().empty()||l.nextTab().empty()||l.tabLabel().empty(),label);
        Rect c=l.content();
        assertTrue(c.y()>=l.title().bottom()&&c.y()>=l.refresh().bottom(),label+": content below header");
        for(Rect t:l.tabs())assertTrue(t.bottom()<=c.y(),label+": tabs above content");
        assertTrue(c.bottom()<=l.back().y()&&c.bottom()<=l.close().y(),label+": content above footer");
        assertEquals(0,c.height()%LedgerLayout.ROW_HEIGHT,label+": whole rows only");
        // Clicks and scrolling never reach rows from a button.
        for(Rect button:List.of(l.refresh(),l.back(),l.close()))
            assertEquals(-1,l.rowAt(button.x()+1,button.y()+1,0,10_000),label+": button is not a row");
        for(Rect tab:l.tabs())assertEquals(-1,l.rowAt(tab.x()+1,tab.y()+1,0,10_000),label);
        if(l.visibleRows()>0) {
            assertEquals(0,l.rowAt(c.x()+1,c.y()+1,0,5));
            assertEquals(7,l.rowAt(c.x()+1,c.y()+1,7,50),"Offset applies");
            assertEquals(-1,l.rowAt(c.x()+1,c.y()+1,5,5),"No row past the end");
            assertEquals(-1,l.rowAt(c.x()+1,c.bottom(),0,10_000),"Bottom edge is outside");
        }
    }
    @Test void guiScalesOneToFourAndAutoNeverOverlap() {
        int[][] windows={{854,480},{1024,768},{1280,720},{1366,768},{1600,900},{1920,1080},{2560,1440},{3840,2160},{640,480},{800,600}};
        for(int[] window:windows)for(int requested=0;requested<=4;requested++) {
            int s=scale(requested,window[0],window[1]);
            var l=LedgerLayout.compute(scaled(window[0],s),scaled(window[1],s),WIDTHS);
            String label=window[0]+"x"+window[1]+" scale "+(requested==0?"Auto":requested)+"→"+s;
            assertSound(l,label);
            assertFalse(l.tooSmall(),label+" is a supported size");
            assertTrue(l.visibleRows()>=LedgerLayout.MIN_CONTENT_ROWS,label+" leaves a usable viewport: "+l.content());
        }
    }
    @Test void resizedWindowsKeepEveryInvariant() {
        for(int w=60;w<=1400;w+=13)for(int h=40;h<=800;h+=11) {
            assertSound(LedgerLayout.compute(w,h,WIDTHS),w+"x"+h);
            assertSound(LedgerLayout.compute(w,h,WIDTHS.subList(0,7)),w+"x"+h+" without Diagnostics",7);
        }
    }
    @Test void narrowWindowsWrapTabsAndShortWindowsUseCompactSelector() {
        var wide=LedgerLayout.compute(960,540,WIDTHS);
        assertFalse(wide.compactTabs());
        assertEquals(1,wide.tabs().stream().map(Rect::y).distinct().count(),"Wide: one tab row");
        var narrow=LedgerLayout.compute(320,240,WIDTHS);
        assertFalse(narrow.compactTabs());
        assertTrue(narrow.tabs().stream().map(Rect::y).distinct().count()>1,"Narrow: tabs wrap onto more rows");
        assertTrue(narrow.content().y()>narrow.tabs().getLast().bottom(),"Content starts below the last wrapped row");
        var shortWide=LedgerLayout.compute(240,130,WIDTHS);
        assertTrue(shortWide.compactTabs(),"Wrapping would leave fewer than "+LedgerLayout.MIN_CONTENT_ROWS+" rows");
        assertSound(shortWide,"compact");
        var tiny=LedgerLayout.compute(120,80,WIDTHS);
        assertTrue(tiny.tooSmall());assertSound(tiny,"tiny");
        assertFalse(tiny.close().empty(),"The screen can always be closed");
        assertEquals(-1,tiny.rowAt(10,10,0,100));
    }
    @Test void longTextIsFittedAndScrollingIsBounded() {
        String name="Very long village name ".repeat(40);
        var l=LedgerLayout.compute(427,240,WIDTHS);
        String title=LedgerLayout.fit(name,l.title().width(),FONT);
        assertTrue(FONT.applyAsInt(title)<=l.title().width());assertTrue(title.endsWith("…"));
        assertEquals("short",LedgerLayout.fit("short",100,FONT));
        assertEquals("",LedgerLayout.fit("anything",0,FONT));
        assertEquals(0,LedgerLayout.clampOffset(-5,10,20));
        assertEquals(90,LedgerLayout.clampOffset(500,100,10));
        assertEquals(2,LedgerLayout.clampOffset(3,2,0),"Nothing visible: offset only bounded by rows");
        assertTrue(l.thumb(0,1).empty(),"No scrollbar when everything fits");
        for(int offset=0;offset<=1000;offset+=37) {
            var thumb=l.thumb(offset,1000);
            assertTrue(thumb.y()>=l.scrollbar().y()&&thumb.bottom()<=l.scrollbar().bottom(),"Thumb inside track at "+offset);
        }
        var label=l.tabs().get(TABS.indexOf("Progression"));
        assertTrue(FONT.applyAsInt("Progression")+6<=label.width()||label.width()==l.width()-2*LedgerLayout.MARGIN);
    }
    @Test void lateRefreshNeverReopensAClosedLedger() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertEquals(LedgerRouting.Action.OPEN,LedgerRouting.route(false,null,a));
        assertEquals(LedgerRouting.Action.OPEN,LedgerRouting.route(false,b,a),"Book on another bell opens that village");
        assertEquals(LedgerRouting.Action.IGNORE,LedgerRouting.route(true,null,a),"Closed screen stays closed");
        assertEquals(LedgerRouting.Action.IGNORE,LedgerRouting.route(true,b,a),"Different village is not mixed in");
        assertEquals(LedgerRouting.Action.UPDATE,LedgerRouting.route(true,a,a));
    }
}
