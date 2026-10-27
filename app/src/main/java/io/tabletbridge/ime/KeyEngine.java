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
        long started;
        int owners;
        Press(long time) { started = time; owners = 1; }
    }
    private final Map<Integer, Press> held = new LinkedHashMap<>();
    private final Map<Integer, Integer> pointers = new HashMap<>();
    // Keep visual/source keys separate from emitted keys. A key's mapping is frozen
    // at DOWN so a mode change cannot send an unmatched UP or change a held repeat.
    private final Map<Integer, Integer> sourceKeys = new HashMap<>();
    private boolean numericMode = true;
    private final Sink sink;
    private final LongSupplier clock;
    private boolean sticky;
    private boolean compatibilityRepeat;
    private int lockMeta;
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
        Press press = held.get(code);
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
    public boolean isLit(int code) {
        if (code == 143) return numericMode;
        if (modifier(code) == 0 && code != 115 && code != 116) return sourceKeys.containsValue(code);
        return isHeld(code) || (code == 115 && (lockMeta & 0x100000) != 0)
                || (code == 116 && (lockMeta & 0x400000) != 0);
    }
    public boolean numericMode() { return numericMode; }
    /** Local Num mode, not a claim about the remote host's lock state.
     * Digits use the number row and navigation uses dedicated keys, so neither
     * depends on the host's NumLock setting. Other modifiers retain normal meaning.
     */
    private int mappedCode(int code) {
        if (numericMode) {
            if (code >= 144 && code <= 153) return 7 + code - 144; // 0..9
            if (code == 158) return 56; // decimal -> period
            return code;
        }
        return switch (code) {
            case 144 -> 124; // 0 -> Insert
            case 145 -> 123; // 1 -> End
            case 146 -> 20;  // 2 -> Down
            case 147 -> 93;  // 3 -> Page Down
            case 148 -> 21;  // 4 -> Left
            case 149 -> -1;  // 5 has no navigation action
            case 150 -> 22;  // 6 -> Right
            case 151 -> 122; // 7 -> Home
            case 152 -> 19;  // 8 -> Up
            case 153 -> 92;  // 9 -> Page Up
            case 158 -> 112; // decimal -> Delete
            default -> code;
        };
    }
    public boolean sticky() { return sticky; }
    public void setSticky(boolean value) { releaseAll(); sticky = value; }
    public boolean pointerDown(int id, int code) {
        if (pointers.containsKey(id)) return true;
        if (code == 143) {
            if (!sourceKeys.containsValue(143)) numericMode = !numericMode;
            sourceKeys.put(id, code);
            pointers.put(id, -1);
            return true; // Never toggle the remote host's unknown lock state.
        }
        sourceKeys.put(id, code);
        code = mappedCode(code);
        if (code < 0) { pointers.put(id, -1); return true; }
        if (sticky && modifier(code) != 0) {
            pointers.put(id, -1);
            if (isHeld(code)) return release(code);
            return press(code);
        }
        pointers.put(id, code);
        return press(code);
    }
    public boolean pointerUp(int id) {
        sourceKeys.remove(id);
        Integer code = pointers.remove(id);
        return code == null || code < 0 || release(code);
    }
    private boolean press(int code) {
        Press existing = held.get(code);
        if (existing != null) { existing.owners++; return true; }
        long now = clock.getAsLong();
        held.put(code, new Press(now));
        if (code == 115) lockMeta ^= 0x100000;
        if (code == 116) lockMeta ^= 0x400000;
        boolean ok = sink.send(new Event(code, true, meta(), now, now, 0));
        if (isRepeatable(code)) {
            // Like hardware typematic, only the most recently pressed ordinary key repeats.
            repeating = ok ? code : null;
            repeatCount = 0;
            nextRepeat = now + repeatDelay;
        }
        return ok;
    }
    private boolean release(int code) {
        Press press = held.get(code);
        if (press == null) return true;
        if (--press.owners > 0) return true;
        held.remove(code);
        if (repeating != null && repeating == code) repeating = null;
        return sink.send(new Event(code, false, meta(), press.started, clock.getAsLong(), 0));
    }
    public void releaseAll() {
        repeating = null;
        // Release ordinary keys before modifiers, including two fingers on the same key.
        ArrayList<Integer> codes = new ArrayList<>(held.keySet());
        codes.sort((a, b) -> Integer.compare(modifier(a) == 0 ? 0 : 1, modifier(b) == 0 ? 0 : 1));
        for (int code : codes) { held.get(code).owners = 1; release(code); }
        pointers.clear();
        sourceKeys.clear();
    }
    public void resetSession() { releaseAll(); lockMeta = 0; numericMode = true; }
}
