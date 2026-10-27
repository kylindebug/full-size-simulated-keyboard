package io.tabletbridge.ime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Platform-independent press state. Android key/meta values are part of its public API. */
public final class KeyEngine {
    public interface Sink { boolean send(Event event); }
    public static final class Event {
        public final int code, meta, repeatCount;
        public final boolean down;
        public final long downTime, eventTime;
        Event(int code, boolean down, int meta, long downTime, long eventTime, int repeatCount) {
            this.code = code; this.down = down; this.meta = meta;
            this.downTime = downTime; this.eventTime = eventTime;
            this.repeatCount = repeatCount;
        }
    }
    private static final class Press {
        final int eventCode;
        final Output output;
        int owners = 1;
        Press(int eventCode, Output output) { this.eventCode = eventCode; this.output = output; }
    }
    private static final class Output {
        long started;
        int owners = 1;
        Output(long time) { started = time; }
    }
    private final Map<Integer, Press> held = new LinkedHashMap<>();
    // Different physical keys may resolve to the same event (e.g. top-row 1 and keypad 1).
    private final Map<Integer, Output> outputs = new LinkedHashMap<>();
    private final Map<Integer, Integer> pointers = new HashMap<>();
    private final Sink sink;
    private final LongSupplier clock;
    private boolean sticky;
    private boolean compatibilityRepeat;
    private static final int NUM_LOCK = 143, NUM_LOCK_ON = 0x200000;
    private int lockMeta = NUM_LOCK_ON;
    private Integer repeating;
    private long nextRepeat;
    private int repeatCount;
    private long repeatDelay = 500, repeatInterval = 50;
    public KeyEngine(Sink sink, LongSupplier clock) { this.sink = sink; this.clock = clock; }

    public void setRepeatTiming(long delay, long interval) {
        if (delay < 0 || interval <= 0) throw new IllegalArgumentException("Invalid repeat timing");
        repeatDelay = delay; repeatInterval = interval;
    }
    public void setCompatibilityRepeat(boolean value) {
        releaseAll(); compatibilityRepeat = value;
    }
    public static boolean isRepeatable(int code) {
        // Modifiers and lock/system keys do not auto-repeat on a physical keyboard.
        return modifier(code) == 0 && code != 115 && code != 116 && code != 143
                && code != 120 && code != 121 && code != 318;
    }
    /** Delay until the next repeat, or -1 when no repeat is pending. */
    public long nextRepeatDelay() {
        return repeating == null ? -1 : Math.max(0, nextRepeat - clock.getAsLong());
    }
    /** Called on the input thread. Never catch up with a burst after a stalled frame. */
    public boolean repeat() {
        if (repeating == null) return true;
        long now = clock.getAsLong();
        if (now < nextRepeat) return true;
        int code = repeating;
        Output press = outputs.get(code);
        nextRepeat = now + repeatInterval;
        boolean ok;
        if (compatibilityRepeat) {
            // Moonlight filters repeatCount > 0, and Sunshine filters duplicate DOWNs.
            // Opt-in text mode therefore sends complete transitions, never for modifiers.
            ok = sink.send(new Event(code, false, meta(), press.started, now, 0));
            if (ok) {
                press.started = now;
                ok = sink.send(new Event(code, true, meta(), now, now, 0));
            }
        } else {
            ok = sink.send(new Event(code, true, meta(), press.started, now, ++repeatCount));
        }
        if (!ok) repeating = null;
        return ok;
    }

    public static int modifier(int code) {
        return switch (code) {
            case 59 -> 0x41; case 60 -> 0x81; // Shift L/R + aggregate
            case 57 -> 0x12; case 58 -> 0x22; // Alt L/R
            case 113 -> 0x3000; case 114 -> 0x5000; // Ctrl L/R
            case 117 -> 0x30000; case 118 -> 0x50000; // Meta L/R
            default -> 0;
        };
    }
    public int meta() {
        int value = lockMeta;
        for (int code : held.keySet()) value |= modifier(code);
        return value;
    }
    public boolean isHeld(int code) { return held.containsKey(code); }
    public boolean numericMode() { return (lockMeta & NUM_LOCK_ON) != 0; }
    public boolean isDimmed(int code) { return code == NUM_LOCK && !numericMode(); }
    public boolean isLit(int code) {
        if (code == NUM_LOCK) return false;
        return isHeld(code) || (code == 115 && (lockMeta & 0x100000) != 0)
                || (code == 116 && (lockMeta & 0x400000) != 0);
    }
    public boolean sticky() { return sticky; }
    public void setSticky(boolean value) { releaseAll(); sticky = value; }
    public boolean pointerDown(int id, int code) {
        if (pointers.containsKey(id)) return true;
        if (sticky && modifier(code) != 0) {
            pointers.put(id, -1);
            if (isHeld(code)) return release(code);
            return press(code);
        }
        pointers.put(id, code);
        return press(code);
    }
    public boolean pointerUp(int id) {
        Integer code = pointers.remove(id);
        return code == null || code < 0 || release(code);
    }
    private boolean press(int code) {
        Press existing = held.get(code);
        if (existing != null) { existing.owners++; return true; }
        if (code == NUM_LOCK) {
            boolean ok = releaseKeypad();
            held.put(code, new Press(-1, null));
            // This is a local semantic mode, never a blind toggle of the remote host.
            lockMeta ^= NUM_LOCK_ON;
            return ok;
        }
        // Never let a held top-row fallback turn into Shift punctuation or an Alt
        // shortcut mid-press. Lift and press the keypad again to start a native chord.
        if (numericMode() && modifier(code) != 0 && !releaseKeypad()) return false;
        int eventCode = resolve(code);
        if (eventCode < 0) {
            held.put(code, new Press(eventCode, null));
            repeating = null;
            return true; // Keypad 5 has no navigation action.
        }
        Output shared = outputs.get(eventCode);
        if (shared != null) {
            shared.owners++;
            held.put(code, new Press(eventCode, shared));
            return true;
        }
        long now = clock.getAsLong();
        Output output = new Output(now);
        outputs.put(eventCode, output);
        held.put(code, new Press(eventCode, output));
        if (code == 115) lockMeta ^= 0x100000;
        if (code == 116) lockMeta ^= 0x400000;
        boolean ok = sink.send(new Event(eventCode, true, meta(), now, now, 0));
        if (isRepeatable(code)) {
            // Like hardware typematic, only the most recently pressed ordinary key repeats.
            repeating = ok ? eventCode : null;
            repeatCount = 0;
            nextRepeat = now + repeatDelay;
        }
        return ok;
    }
    private static boolean isModeKeypad(int code) {
        return (code >= 144 && code <= 153) || code == 158;
    }
    private int resolve(int code) {
        if (!isModeKeypad(code)) return code;
        if (numericMode()) {
            // Preserve native keypad identity for Shift, Alt codes and other shortcuts.
            // Their interpretation belongs to the host; ordinary input is deterministic.
            for (int heldCode : held.keySet()) if (modifier(heldCode) != 0) return code;
            return code == 158 ? 56 : 7 + code - 144; // period or top-row 0..9
        }
        return switch (code) {
            case 144 -> 124; // 0: Insert
            case 145 -> 123; // 1: End
            case 146 -> 20;  // 2: Down
            case 147 -> 93;  // 3: PageDown
            case 148 -> 21;  // 4: Left
            case 149 -> -1;  // 5: no action
            case 150 -> 22;  // 6: Right
            case 151 -> 122; // 7: Home
            case 152 -> 19;  // 8: Up
            case 153 -> 92;  // 9: PageUp
            case 158 -> 112; // decimal: Delete
            default -> throw new AssertionError(code);
        };
    }
    private boolean releaseKeypad() {
        boolean ok = true;
        for (int code : new ArrayList<>(held.keySet())) if (isModeKeypad(code)) {
            held.get(code).owners = 1;
            if (!release(code)) ok = false;
        }
        // A finger held across the switch must lift before producing a new action.
        pointers.replaceAll((id, code) -> isModeKeypad(code) ? -1 : code);
        return ok;
    }
    private boolean release(int code) {
        Press press = held.get(code);
        if (press == null) return true;
        if (--press.owners > 0) return true;
        boolean ok = true;
        if (numericMode() && modifier(code) != 0) ok = releaseKeypad();
        held.remove(code);
        if (press.output == null || --press.output.owners > 0) return ok;
        outputs.remove(press.eventCode);
        if (repeating != null && repeating == press.eventCode) repeating = null;
        return sink.send(new Event(press.eventCode, false, meta(), press.output.started, clock.getAsLong(), 0)) && ok;
    }
    public void releaseAll() {
        repeating = null;
        // Release ordinary keys before modifiers, including two fingers on the same key.
        ArrayList<Integer> codes = new ArrayList<>(held.keySet());
        codes.sort((a, b) -> Integer.compare(modifier(a) == 0 ? 0 : 1, modifier(b) == 0 ? 0 : 1));
        for (int code : codes) { held.get(code).owners = 1; release(code); }
        pointers.clear();
    }
    public void resetSession() { releaseAll(); lockMeta = NUM_LOCK_ON; }
}
