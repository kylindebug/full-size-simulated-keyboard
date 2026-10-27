package io.tabletbridge.ime;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class KeyEngineTest {
    private static final int NUM_ON = 0x200000;
    private KeyEngine engine;
    private final List<KeyEngine.Event> events=new ArrayList<>();
    private long time;
    @Before public void setup() { events.clear(); time=100; engine=new KeyEngine(e->{events.add(e);return true;},()->time++); }
    @Test public void shortTapHasOneDownAndOneUp() {
        engine.pointerDown(1,51); assertEquals(1,events.size()); assertTrue(engine.isHeld(51));
        engine.pointerUp(1); assertEquals(2,events.size()); assertFalse(events.get(1).down);
        assertEquals(events.get(0).downTime,events.get(1).downTime); assertTrue(events.get(1).eventTime>events.get(0).eventTime);
    }
    @Test public void multiTouchChordPreservesModifiers() {
        engine.pointerDown(0,113); engine.pointerDown(1,59); engine.pointerDown(2,51);
        assertEquals(NUM_ON|0x3041,events.get(2).meta); engine.pointerUp(2);
        assertEquals(NUM_ON|0x3041,events.get(3).meta); engine.pointerUp(0); assertEquals(NUM_ON|0x41,engine.meta());
        engine.pointerUp(1); assertEquals(NUM_ON,engine.meta());
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
        assertEquals(NUM_ON,engine.meta()); engine.pointerUp(1); assertEquals(4,events.size());
    }
    @Test public void leftAndRightModifierHaveIndependentLifetimes() {
        engine.pointerDown(1,59); engine.pointerDown(2,60); engine.pointerUp(1);
        assertEquals(NUM_ON|0x81,engine.meta()); engine.pointerUp(2); assertEquals(NUM_ON,engine.meta());
    }
    @Test public void stickyModifiersLatchUntilSecondTap() {
        engine.setSticky(true); engine.pointerDown(0,113); engine.pointerUp(0);
        assertTrue(engine.isHeld(113)); engine.pointerDown(1,31); engine.pointerUp(1);
        assertEquals(NUM_ON|0x3000,events.get(1).meta); engine.pointerDown(0,113); engine.pointerUp(0);
        assertFalse(engine.isHeld(113)); assertEquals(4,events.size());
    }
    @Test public void modeChangeReleasesLatchedKeys() {
        engine.setSticky(true); engine.pointerDown(0,57); engine.pointerUp(0); engine.setSticky(false);
        assertFalse(engine.isHeld(57)); assertEquals(2,events.size());
    }
    @Test public void lockKeysToggleOncePerPress() {
        engine.pointerDown(0,143); engine.pointerUp(0); assertEquals(0,engine.meta());
        engine.pointerDown(0,143); engine.pointerUp(0); assertEquals(NUM_ON,engine.meta());
        assertTrue(events.isEmpty());
    }
    @Test public void sessionResetDoesNotCarryLocksToNextApp() {
        engine.pointerDown(1,115); engine.pointerDown(2,113); engine.resetSession();
        assertEquals(NUM_ON,engine.meta()); assertFalse(engine.isHeld(113));
    }
    @Test public void failedDeliveryIsReportedAndStateCanBeReleased() {
        KeyEngine failing=new KeyEngine(e->false,()->1L); assertFalse(failing.pointerDown(1,29));
        assertFalse(failing.pointerUp(1)); assertFalse(failing.isHeld(29));
    }
    @Test public void independentNumpadEnterAndMainEnter() {
        engine.pointerDown(0,66); engine.pointerDown(1,160); engine.pointerUp(0);
        assertTrue(engine.isHeld(160)); engine.releaseAll(); assertEquals(4,events.size());
    }
    private void repeatSetup() {
        events.clear(); time=100;
        engine=new KeyEngine(e->{events.add(e);return true;},()->time);
        engine.setRepeatTiming(500,50);
    }
    @Test public void backspaceRepeatsAfterDelayUntilReleased() {
        repeatSetup(); engine.pointerDown(1,67);
        assertEquals(500,engine.nextRepeatDelay());
        time=599; engine.repeat(); assertEquals(1,events.size());
        time=600; engine.repeat(); time=650; engine.repeat();
        assertEquals(3,events.size());
        for(int i=0;i<3;i++) {
            KeyEngine.Event e=events.get(i);
            assertTrue(e.down); assertEquals(67,e.code); assertEquals(i,e.repeatCount);
            assertEquals(100,e.downTime);
        }
        assertEquals(650,events.get(2).eventTime);
        engine.pointerUp(1); assertEquals(-1,engine.nextRepeatDelay());
        time=1000; engine.repeat(); assertEquals(4,events.size());
        assertFalse(events.get(3).down); assertEquals(0,events.get(3).repeatCount);
    }
    @Test public void lettersNavigationEditingAndFunctionKeysRepeat() {
        for(int code:new int[]{29,7,62,66,61,112,19,20,21,22,92,93,122,123,124,131,144,155}) {
            repeatSetup(); engine.pointerDown(1,code); time=600; engine.repeat();
            assertEquals("code "+code,2,events.size()); assertEquals(1,events.get(1).repeatCount);
        }
    }
    @Test public void modifiersLocksPrintScreenAndPauseDoNotRepeat() {
        for(int code:new int[]{57,58,59,60,113,114,117,118,115,116,143,120,121,318}) {
            repeatSetup(); engine.pointerDown(1,code); int meta=engine.meta();
            time=5000; engine.repeat();
            assertEquals("code "+code,code==143?0:1,events.size()); assertEquals(-1,engine.nextRepeatDelay());
            assertEquals(meta,engine.meta());
        }
    }
    @Test public void repeatsUseCurrentModifiersWithoutRestartingDelay() {
        repeatSetup(); engine.pointerDown(1,29); time=300; engine.pointerDown(2,59);
        assertEquals(300,engine.nextRepeatDelay()); time=600; engine.repeat();
        assertEquals(NUM_ON|0x41,events.get(2).meta);
        engine.pointerUp(2); time=650; engine.repeat(); assertEquals(NUM_ON,events.get(4).meta);
    }
    @Test public void onlyNewestOrdinaryKeyRepeatsAndOlderKeyDoesNotResume() {
        repeatSetup(); engine.pointerDown(1,29); time=300; engine.pointerDown(2,30);
        time=600; engine.repeat(); assertEquals(2,events.size());
        time=800; engine.repeat(); assertEquals(30,events.get(2).code);
        engine.pointerUp(2); time=900; engine.repeat(); assertEquals(4,events.size());
        assertTrue(engine.isHeld(29)); assertEquals(-1,engine.nextRepeatDelay());
    }
    @Test public void secondFingerDoesNotRestartRepeatOrReleaseSharedKey() {
        repeatSetup(); engine.pointerDown(1,67); time=300; engine.pointerDown(2,67);
        engine.pointerUp(1); assertEquals(300,engine.nextRepeatDelay());
        time=600; engine.repeat(); assertEquals(2,events.size());
        engine.pointerUp(2); assertEquals(-1,engine.nextRepeatDelay());
    }
    @Test public void cleanupAndModeChangesCancelPendingRepeat() {
        for(int action=0;action<3;action++) {
            repeatSetup(); engine.pointerDown(1,67);
            if(action==0) engine.releaseAll();
            else if(action==1) engine.resetSession(); else engine.setSticky(true);
            time=5000; engine.repeat(); assertEquals(2,events.size());
            assertEquals(-1,engine.nextRepeatDelay());
        }
    }
    @Test public void stalledFrameDoesNotGenerateCatchUpBurst() {
        repeatSetup(); engine.pointerDown(1,67); time=5000; engine.repeat(); engine.repeat();
        assertEquals(2,events.size()); assertEquals(50,engine.nextRepeatDelay());
    }
    @Test public void failedRepeatStopsSchedulingAndStillReleasesKey() {
        time=100; events.clear();
        engine=new KeyEngine(e->{events.add(e);return e.repeatCount==0;},()->time);
        engine.pointerDown(1,67); time=600; assertFalse(engine.repeat());
        assertEquals(-1,engine.nextRepeatDelay()); engine.pointerUp(1);
        assertEquals(3,events.size()); assertFalse(events.get(2).down);
    }
    @Test public void repressStartsFreshRepeatCountAndDownTime() {
        repeatSetup(); engine.pointerDown(1,67); time=600; engine.repeat(); engine.pointerUp(1);
        time=700; engine.pointerDown(1,67); time=1200; engine.repeat();
        assertEquals(1,events.get(4).repeatCount); assertEquals(700,events.get(4).downTime);
    }
    @Test public void compatibilityRepeatPassesMoonlightAndHostDuplicateFilters() {
        for(int code:new int[]{67,29,62,22,112,66,131,144}) {
            repeatSetup(); engine.setCompatibilityRepeat(true);
            engine.pointerDown(1,code); time=600; engine.repeat(); time=650; engine.repeat(); engine.pointerUp(1);
            boolean hostHeld=false; int actions=0;
            for(KeyEngine.Event event:events) {
                assertEquals(0,event.repeatCount);
                if(event.down) {
                    assertFalse("DOWN must follow UP",hostHeld);
                    if(event.repeatCount==0 && !hostHeld) actions++;
                    hostHeld=true;
                } else { assertTrue(hostHeld); hostHeld=false; }
            }
            assertEquals(3,actions); assertFalse(hostHeld); assertEquals(-1,engine.nextRepeatDelay());
            assertEquals(events.get(4).downTime,events.get(5).downTime);
        }
    }
    @Test public void compatibilityRepeatKeepsModifiersHeld() {
        repeatSetup(); engine.setCompatibilityRepeat(true);
        engine.pointerDown(0,113); engine.pointerDown(1,67); time=600; engine.repeat();
        assertEquals(4,events.size());
        assertFalse(events.get(2).down); assertEquals(67,events.get(2).code);
        assertEquals(NUM_ON|0x3000,events.get(3).meta); assertTrue(engine.isHeld(113));
        engine.releaseAll(); time=1000; engine.repeat(); assertEquals(6,events.size());
        assertEquals(113,events.get(5).code); assertFalse(events.get(5).down);
    }
    @Test public void compatibilityModeChangeReleasesAndCancels() {
        repeatSetup(); engine.pointerDown(1,67); engine.setCompatibilityRepeat(true);
        time=1000; engine.repeat(); assertEquals(2,events.size());
        assertFalse(engine.isHeld(67)); assertEquals(-1,engine.nextRepeatDelay());
    }
    @Test public void compatibilityRepeatFailureDoesNotSendAnotherDown() {
        time=100; events.clear();
        engine=new KeyEngine(e->{events.add(e);return e.down;},()->time);
        engine.setCompatibilityRepeat(true); engine.pointerDown(1,67);
        time=600; assertFalse(engine.repeat()); assertEquals(2,events.size());
        assertFalse(events.get(1).down); assertEquals(-1,engine.nextRepeatDelay());
    }
}
