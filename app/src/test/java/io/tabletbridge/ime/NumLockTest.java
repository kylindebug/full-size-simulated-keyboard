package io.tabletbridge.ime;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

public class NumLockTest {
    private final List<KeyEngine.Event> events = new ArrayList<>();
    private long time;
    private KeyEngine engine;
    @Before public void setup() {
        events.clear(); time = 100;
        engine = new KeyEngine(e -> { events.add(e); return true; }, () -> time);
    }
    private void toggle() { engine.pointerDown(99, 143); engine.pointerUp(99); }
    private void tap(int code) { engine.pointerDown(1, code); engine.pointerUp(1); }
    private void pair(int index, int code) {
        KeyEngine.Event down = events.get(index), up = events.get(index + 1);
        assertEquals(code, down.code); assertEquals(code, up.code);
        assertTrue(down.down); assertFalse(up.down);
        assertEquals(down.downTime, up.downTime);
    }
    @Test public void defaultAndResetAreNumericWithOrdinaryNumColor() {
        assertTrue(engine.numericMode()); assertFalse(engine.isDimmed(143)); assertFalse(engine.isLit(143));
        toggle(); assertFalse(engine.numericMode()); assertTrue(engine.isDimmed(143));
        engine.resetSession(); assertTrue(engine.numericMode()); assertFalse(engine.isDimmed(143));
        assertTrue(events.isEmpty());
    }
    @Test public void numIsLocalClickableAndNeverHighlightedEvenWhileHeld() {
        engine.pointerDown(1, 143); assertTrue(engine.isDimmed(143)); assertFalse(engine.isLit(143));
        time = 5000; engine.repeat(); assertEquals(-1, engine.nextRepeatDelay());
        engine.pointerUp(1); toggle(); assertTrue(engine.numericMode()); assertFalse(engine.isDimmed(143));
        assertTrue(events.isEmpty());
    }
    @Test public void twoFingersOnNumToggleOnlyOnceUntilBothLift() {
        engine.pointerDown(1, 143); engine.pointerDown(2, 143); engine.pointerUp(1);
        assertFalse(engine.numericMode()); engine.pointerUp(2); toggle(); assertTrue(engine.numericMode());
        assertTrue(events.isEmpty());
    }
    @Test public void ordinaryDigitsAndDecimalUseExplicitNonKeypadCodes() {
        for (int i = 0; i < 10; i++) { tap(144 + i); pair(i * 2, 7 + i); }
        tap(158); pair(20, 56);
        for (KeyEngine.Event e : events) assertEquals(0x200000, e.meta);
    }
    @Test public void navigationUsesExplicitCodesRegardlessOfHostNumLock() {
        toggle();
        int[] codes = {124, 123, 20, 93, 21, -1, 22, 122, 19, 92};
        for (int i = 0; i < 10; i++) {
            int before = events.size(); tap(144 + i);
            if (codes[i] < 0) assertEquals(before, events.size()); else pair(before, codes[i]);
        }
        int before = events.size(); tap(158); pair(before, 112);
        for (KeyEngine.Event e : events) assertEquals(0, e.meta);
        toggle(); before = events.size(); tap(151); pair(before, 14);
    }
    @Test public void operatorsAndEnterAreUnaffectedInBothModes() {
        for (int mode = 0; mode < 2; mode++) {
            for (int code : new int[]{154, 155, 156, 157, 66}) {
                int before = events.size(); tap(code); pair(before, code);
            }
            toggle();
        }
    }
    @Test public void numericChordsKeepNativeKeypadIdentityForEveryModifier() {
        for (int modifier : new int[]{57, 58, 59, 60, 113, 114, 117, 118}) {
            engine.pointerDown(0, modifier);
            for (int code : new int[]{144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 158}) {
                int before = events.size(); tap(code); pair(before, code);
                assertEquals(0x200000 | KeyEngine.modifier(modifier), events.get(before).meta);
            }
            engine.pointerUp(0);
        }
    }
    @Test public void altNumericSequenceIsNotReplacedWithTopRowDigits() {
        engine.pointerDown(0, 57); tap(144); tap(150); tap(149); engine.pointerUp(0);
        assertEquals(8, events.size()); pair(1, 144); pair(3, 150); pair(5, 149);
        assertEquals(57, events.get(7).code); assertFalse(events.get(7).down);
    }
    @Test public void navigationWithShiftAndCtrlRetainsSelectionModifiers() {
        toggle(); engine.pointerDown(0, 59); engine.pointerDown(2, 113);
        tap(151); pair(2, 122); assertEquals(0x3041, events.get(2).meta);
        tap(158); pair(4, 112); engine.releaseAll();
    }
    @Test public void modeSwitchReleasesOldCodeAndStopsRepeatUntilFingerLifts() {
        for (boolean compatibility : new boolean[]{false, true}) {
            setup(); engine.setCompatibilityRepeat(compatibility);
            engine.pointerDown(1, 151); time = 600; engine.repeat();
            int before = events.size(); toggle();
            assertEquals(before + 1, events.size()); assertEquals(14, events.get(before).code);
            assertFalse(events.get(before).down); assertFalse(engine.isHeld(151));
            assertEquals(-1, engine.nextRepeatDelay());
            time = 1000; engine.repeat(); engine.pointerUp(1); assertEquals(before + 1, events.size());
            engine.pointerDown(1, 151); assertEquals(122, events.get(before + 1).code);
            toggle(); pair(before + 1, 122); engine.pointerUp(1);
        }
    }
    @Test public void switchReleasesBothFingersButKeepsModifiersAndUnrelatedKeys() {
        engine.pointerDown(0, 59); engine.pointerDown(1, 151); engine.pointerDown(2, 151);
        engine.pointerDown(3, 29); toggle();
        assertTrue(engine.isHeld(59)); assertTrue(engine.isHeld(29)); assertFalse(engine.isHeld(151));
        assertEquals(4, events.size()); assertEquals(151, events.get(3).code); assertFalse(events.get(3).down);
        assertEquals(events.get(1).downTime, events.get(3).downTime);
        engine.pointerUp(1); engine.pointerUp(2); assertEquals(4, events.size());
        time = 600; engine.repeat(); assertEquals(29, events.get(4).code);
        engine.releaseAll(); assertEquals(29, events.get(5).code); assertEquals(59, events.get(6).code);
    }
    @Test public void outputAliasesShareOneBalancedLifetimeInEitherReleaseOrder() {
        for (boolean keypadFirst : new boolean[]{false, true}) {
            setup(); engine.pointerDown(1, 8); engine.pointerDown(2, 145);
            assertEquals(1, events.size()); assertTrue(engine.isLit(8)); assertTrue(engine.isLit(145));
            engine.pointerUp(keypadFirst ? 2 : 1); assertEquals(1, events.size());
            time = 600; engine.repeat(); assertEquals(8, events.get(1).code);
            engine.pointerUp(keypadFirst ? 1 : 2); assertEquals(3, events.size());
            assertFalse(events.get(2).down); assertEquals(-1, engine.nextRepeatDelay());
        }
    }
    @Test public void switchDoesNotReleaseAnAliasedMainKeyboardKey() {
        engine.pointerDown(1, 8); engine.pointerDown(2, 145); toggle();
        assertEquals(1, events.size()); assertTrue(engine.isHeld(8)); assertFalse(engine.isHeld(145));
        engine.pointerUp(2); engine.pointerUp(1); pair(0, 8);
    }
    @Test public void navigationAndDedicatedArrowShareOneLifetime() {
        toggle(); engine.pointerDown(1, 19); engine.pointerDown(2, 152); toggle();
        assertEquals(1, events.size()); assertTrue(engine.isHeld(19));
        engine.pointerUp(1); engine.pointerUp(2); pair(0, 19);
    }
    @Test public void modifierTransitionsCancelNumericKeypadBeforeChangingMeta() {
        for (int modifier : new int[]{57, 59, 113, 117}) {
            setup(); engine.pointerDown(1, 145); engine.pointerDown(0, modifier);
            pair(0, 8); assertEquals(0x200000, events.get(1).meta);
            assertEquals(modifier, events.get(2).code); assertFalse(engine.isHeld(145));
            engine.pointerUp(1); engine.pointerDown(1, 145); engine.pointerUp(0);
            pair(3, 145); assertEquals(0x200000 | KeyEngine.modifier(modifier), events.get(4).meta);
            assertEquals(modifier, events.get(5).code); assertFalse(events.get(5).down);
            time = 1000; engine.repeat(); engine.pointerUp(1); assertEquals(6, events.size());
        }
    }
    @Test public void stickyAltAndNumSwitchKeepAltLatchedAndReleaseKeypad() {
        engine.setSticky(true); tap(57); engine.pointerDown(2, 145); toggle();
        assertTrue(engine.isHeld(57)); assertFalse(engine.isHeld(145)); pair(1, 145);
        engine.pointerUp(2); tap(151); pair(3, 122);
        tap(57); assertFalse(engine.isHeld(57));
    }
    @Test public void disabledFiveHasNoEventsOrRepeat() {
        toggle(); engine.pointerDown(0, 19); engine.pointerDown(1, 149);
        time = 5000; engine.repeat(); assertEquals(1, events.size());
        assertEquals(-1, engine.nextRepeatDelay()); engine.releaseAll(); pair(0, 19);
    }
    @Test public void capsAndScrollDoNotAffectNumericModeAndResetRestoresDefault() {
        tap(115); tap(116); tap(145); pair(4, 8); assertTrue(engine.numericMode());
        toggle(); engine.releaseAll(); assertTrue(engine.isDimmed(143));
        engine.resetSession(); assertEquals(0x200000, engine.meta()); assertFalse(engine.isDimmed(143));
    }
    @Test public void failedSwitchReleaseReportsFailureAndDoesNotLeaveLocalHeldKeys() {
        engine = new KeyEngine(e -> { events.add(e); return e.down; }, () -> time);
        engine.pointerDown(1, 151); assertFalse(engine.pointerDown(2, 143));
        assertFalse(engine.isHeld(151)); assertFalse(engine.numericMode());
        assertEquals(-1, engine.nextRepeatDelay()); engine.releaseAll(); pair(0, 14);
    }
    @Test public void randomMultitouchModeAndModifierChangesLeaveBalancedOutputs() {
        for (boolean compatibility : new boolean[]{false, true}) {
            setup(); Map<Integer, Long> down = new HashMap<>();
            engine = new KeyEngine(e -> {
                assertNotEquals(143, e.code);
                if (e.down && e.repeatCount == 0) assertNull("duplicate DOWN " + e.code, down.put(e.code, e.downTime));
                else if (e.down) assertEquals(Long.valueOf(e.downTime), down.get(e.code));
                else assertEquals("UP needs matching DOWN " + e.code, Long.valueOf(e.downTime), down.remove(e.code));
                return true;
            }, () -> time);
            engine.setCompatibilityRepeat(compatibility);
            int[] codes = {143, 144, 145, 149, 151, 152, 158, 8, 14, 19, 122, 29, 57, 59, 113};
            Random random = new Random(20261027);
            for (int i = 0; i < 3000; i++) {
                time += 50; int id = random.nextInt(8);
                switch (random.nextInt(8)) {
                    case 0, 1, 2, 3 -> engine.pointerDown(id, codes[random.nextInt(codes.length)]);
                    case 4, 5 -> engine.pointerUp(id);
                    case 6 -> engine.repeat();
                    case 7 -> engine.releaseAll();
                }
            }
            engine.releaseAll(); assertTrue(down.isEmpty()); assertEquals(-1, engine.nextRepeatDelay());
        }
    }
}
