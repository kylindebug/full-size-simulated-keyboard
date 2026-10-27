package io.tabletbridge.ime;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardColorsTest {
    @Test public void defaultNumericNumMatchesOrdinaryKeysExactly() {
        assertEquals(KeyboardColors.fill(29,false,true), KeyboardColors.fill(143,true,true));
        assertEquals(KeyboardColors.border(29,false), KeyboardColors.border(143,true));
        assertFalse(KeyboardColors.highlighted(143,true));
    }
    @Test public void grayModeIsDistinctAndNotActiveHighlight() {
        assertEquals(KeyboardColors.NUM_NAVIGATION, KeyboardColors.fill(143,false,false));
        assertNotEquals(KeyboardColors.NORMAL, KeyboardColors.fill(143,false,false));
        assertNotEquals(KeyboardColors.ACTIVE, KeyboardColors.fill(143,false,false));
    }
    @Test public void numRemainsGrayOrNormalWhileFingerIsDown() {
        for(boolean lit:new boolean[]{false,true}) {
            assertEquals(KeyboardColors.NORMAL,KeyboardColors.fill(143,lit,true));
            assertEquals(KeyboardColors.NUM_NAVIGATION,KeyboardColors.fill(143,lit,false));
        }
    }
    @Test public void otherKeysAndLocksRetainOriginalColors() {
        for(int code:new int[]{29,115,116}) {
            assertEquals(KeyboardColors.NORMAL,KeyboardColors.fill(code,false,true));
            assertEquals(KeyboardColors.ACTIVE,KeyboardColors.fill(code,true,true));
            assertTrue(KeyboardColors.highlighted(code,true));
        }
        assertEquals(KeyboardColors.MODIFIER,KeyboardColors.fill(59,false,true));
        assertEquals(KeyboardColors.ACTIVE,KeyboardColors.fill(59,true,true));
    }
    @Test public void repeatedClicksChangeModeWithoutDisablingButton() {
        KeyEngine engine=new KeyEngine(e->true,()->0L);
        for(int i=0;i<4;i++) {
            assertEquals(i%2==0?KeyboardColors.NORMAL:KeyboardColors.NUM_NAVIGATION,
                    KeyboardColors.fill(143,engine.isLit(143),engine.numericMode()));
            engine.pointerDown(0,143);engine.pointerUp(0);
        }
        assertTrue(engine.numericMode());
    }
}
