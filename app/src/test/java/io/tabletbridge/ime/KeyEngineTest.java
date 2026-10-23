package io.tabletbridge.ime;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class KeyEngineTest {
    private KeyEngine engine;
    private final List<KeyEngine.Event> events=new ArrayList<>();
    private long time;
    @Before public void setup() { events.clear(); time=100; engine=new KeyEngine(e->{events.add(e);return true;},()->time++); }
    @Test public void physicalHoldHasNoSyntheticKeyUpOrRepeat() {
        engine.pointerDown(1,51); assertEquals(1,events.size()); assertTrue(engine.isHeld(51));
        engine.pointerUp(1); assertEquals(2,events.size()); assertFalse(events.get(1).down);
        assertEquals(events.get(0).downTime,events.get(1).downTime); assertTrue(events.get(1).eventTime>events.get(0).eventTime);
    }
    @Test public void multiTouchChordPreservesModifiers() {
        engine.pointerDown(0,113); engine.pointerDown(1,59); engine.pointerDown(2,51);
        assertEquals(0x3041,events.get(2).meta); engine.pointerUp(2);
        assertEquals(0x3041,events.get(3).meta); engine.pointerUp(0); assertEquals(0x41,engine.meta());
        engine.pointerUp(1); assertEquals(0,engine.meta());
    }
    @Test public void twoFingersOnOneKeyRequireBothReleases() {
        engine.pointerDown(1,29); engine.pointerDown(2,29); assertEquals(1,events.size());
        engine.pointerUp(1); assertEquals(1,events.size()); engine.pointerUp(2); assertEquals(2,events.size());
    }
    @Test public void duplicatePointerDownDoesNotLeak() {
        engine.pointerDown(1,29); engine.pointerDown(1,30); engine.pointerUp(1);
        assertEquals(2,events.size()); assertFalse(engine.isHeld(29)); assertFalse(engine.isHeld(30));
    }
    @Test public void cancelReleasesOrdinaryKeysBeforeModifiers() {
        engine.pointerDown(0,113); engine.pointerDown(1,51); engine.pointerDown(2,51); engine.releaseAll();
        assertEquals(4,events.size()); assertEquals(51,events.get(2).code); assertEquals(113,events.get(3).code);
        assertEquals(0,engine.meta()); engine.pointerUp(1); assertEquals(4,events.size());
    }
    @Test public void leftAndRightModifierHaveIndependentLifetimes() {
        engine.pointerDown(1,59); engine.pointerDown(2,60); engine.pointerUp(1);
        assertEquals(0x81,engine.meta()); engine.pointerUp(2); assertEquals(0,engine.meta());
    }
    @Test public void stickyModifiersLatchUntilSecondTap() {
        engine.setSticky(true); engine.pointerDown(0,113); engine.pointerUp(0);
        assertTrue(engine.isHeld(113)); engine.pointerDown(1,31); engine.pointerUp(1);
        assertEquals(0x3000,events.get(1).meta); engine.pointerDown(0,113); engine.pointerUp(0);
        assertFalse(engine.isHeld(113)); assertEquals(4,events.size());
    }
    @Test public void modeChangeReleasesLatchedKeys() {
        engine.setSticky(true); engine.pointerDown(0,57); engine.pointerUp(0); engine.setSticky(false);
        assertFalse(engine.isHeld(57)); assertEquals(2,events.size());
    }
    @Test public void lockKeysToggleOncePerPress() {
        engine.pointerDown(0,143); engine.pointerUp(0); assertEquals(0x200000,engine.meta());
        engine.pointerDown(0,143); engine.pointerUp(0); assertEquals(0,engine.meta());
    }
    @Test public void sessionResetDoesNotCarryLocksToNextApp() {
        engine.pointerDown(1,115); engine.pointerDown(2,113); engine.resetSession();
        assertEquals(0,engine.meta()); assertFalse(engine.isHeld(113));
    }
    @Test public void failedDeliveryIsReportedAndStateCanBeReleased() {
        KeyEngine failing=new KeyEngine(e->false,()->1L); assertFalse(failing.pointerDown(1,29));
        assertFalse(failing.pointerUp(1)); assertFalse(failing.isHeld(29));
    }
    @Test public void independentNumpadEnterAndMainEnter() {
        engine.pointerDown(0,66); engine.pointerDown(1,160); engine.pointerUp(0);
        assertTrue(engine.isHeld(160)); engine.releaseAll(); assertEquals(4,events.size());
    }
}
