package io.tabletbridge.ime;

/** Pure color decisions: Num's local mode must not look like a held/locked key. */
public final class KeyboardColors {
    private KeyboardColors() {}
    public static final int NORMAL = 0xd5f2f3ff;
    public static final int NUM_NAVIGATION = 0xffc9cdd3;
    public static final int ACTIVE = 0xffeab6d4;
    public static final int MODIFIER = 0xe0d5def7;
    public static boolean highlighted(int code, boolean lit) {
        return code != 143 && lit;
    }
    public static int fill(int code, boolean lit, boolean numericMode) {
        if (code == 143) return numericMode ? NORMAL : NUM_NAVIGATION;
        return lit ? ACTIVE : KeyEngine.modifier(code) != 0 ? MODIFIER : NORMAL;
    }
    public static int border(int code, boolean lit) {
        return highlighted(code, lit) ? 0xffbd7fae : 0xd9ffffff;
    }
}
