package io.tabletbridge.ime;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class KeyboardLayoutTest {
    @Test public void numpadEnterUsesSupportedEnterEvent() { assertEquals(66,KeyboardLayout.eventCode(160)); assertEquals(66,KeyboardLayout.eventCode(66)); }
    @Test public void printScreenUsesSnapshotMapping() {
        for(KeyboardLayout.Key k:new KeyboardLayout().keys) if(k.label.equals("PrtSc")) { assertEquals(318,k.code); return; }
        fail("Missing PrtSc");
    }
    @Test public void numpad456ShareGeometryAndLabelRows() {
        KeyboardLayout layout=new KeyboardLayout();
        KeyboardLayout.Key previous=null;
        for(KeyboardLayout.Key k:layout.keys) if(k.code>=148 && k.code<=150) {
            assertEquals(2,k.label.split("\n",-1).length);
            if(previous!=null) { assertEquals(previous.y,k.y,.001); assertEquals(previous.h,k.h,.001); }
            previous=k;
        }
        assertNotNull(previous);
    }
    @Test public void all104KeysAreUniqueAndInsidePanel() {
        KeyboardLayout layout=new KeyboardLayout(); assertEquals(104,layout.keys.size());
        HashSet<Integer> codes=new HashSet<>();
        for(KeyboardLayout.Key k:layout.keys) {
            assertTrue("Duplicate: "+k.label,codes.add(k.code));
            assertTrue(k.x>=0 && k.y>=0 && k.w>0 && k.h>0);
            assertTrue(k.x+k.w<=KeyboardLayout.WIDTH+.001f);
            assertTrue(k.y+k.h<=KeyboardLayout.HEIGHT+.001f);
        }
    }
    @Test public void noKeysOverlap() {
        KeyboardLayout layout=new KeyboardLayout();
        for(int i=0;i<layout.keys.size();i++) for(int j=i+1;j<layout.keys.size();j++) {
            KeyboardLayout.Key a=layout.keys.get(i),b=layout.keys.get(j);
            boolean overlap=Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x)>.001f && Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y)>.001f;
            assertFalse(a.label+" overlaps "+b.label,overlap);
        }
    }
}
