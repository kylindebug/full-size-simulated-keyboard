package io.tabletbridge.ime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static android.view.KeyEvent.*;

/** ANSI 104-key geometry, in key units. Layout is shared by rendering and hit testing. */
public final class KeyboardLayout {
    // Moonlight has no NUMPAD_ENTER mapping. Normalize before press ownership
    // tracking so simultaneous Enter keys still emit one balanced DOWN/UP pair.
    public static int eventCode(int code) { return code==KEYCODE_NUMPAD_ENTER?KEYCODE_ENTER:code; }
    public static final float WIDTH = 22.8f, HEIGHT = 6.35f;
    public static final class Key {
        public final String label;
        public final int code;
        public final float x, y, w, h;
        Key(String label, int code, float x, float y, float w, float h) {
            this.label = label; this.code = code; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    public final List<Key> keys;
    private final ArrayList<Key> building = new ArrayList<>();
    private void key(String label, int code, float x, float y, float w, float h) {
        building.add(new Key(label, code, x, y, w, h));
    }
    private void key(String label, int code, float x, float y) { key(label, code, x, y, 1, 1); }
    private void row(float y, String[] labels, int[] codes, float[] widths) {
        float x = 0;
        for (int i = 0; i < labels.length; i++) { key(labels[i], codes[i], x, y, widths[i], 1); x += widths[i]; }
    }
    public KeyboardLayout() {
        key("Esc", KEYCODE_ESCAPE, 0, 0);
        for (int i = 0; i < 12; i++) key("F" + (i + 1), KEYCODE_F1 + i, 2 + i + (i / 4) * .5f, 0);
        // KEYCODE_SCREENSHOT (318, introduced after compileSdk 35).
        // Current Moonlight maps this to Windows VK_SNAPSHOT 0x2C.
        key("PrtSc", 318, 15.4f, 0);
        key("ScrLk", KEYCODE_SCROLL_LOCK, 16.4f, 0);
        key("Pause", KEYCODE_BREAK, 17.4f, 0);
        row(1.35f, new String[]{"~\n`","!\n1","@\n2","#\n3","$\n4","%\n5","^\n6","&\n7","*\n8","(\n9",")\n0","_\n-","+\n=","Backspace"},
            new int[]{KEYCODE_GRAVE,KEYCODE_1,KEYCODE_2,KEYCODE_3,KEYCODE_4,KEYCODE_5,KEYCODE_6,KEYCODE_7,KEYCODE_8,KEYCODE_9,KEYCODE_0,KEYCODE_MINUS,KEYCODE_EQUALS,KEYCODE_DEL},
            new float[]{1,1,1,1,1,1,1,1,1,1,1,1,1,2});
        row(2.35f, new String[]{"Tab","Q","W","E","R","T","Y","U","I","O","P","{\n[","}\n]","|\n\\"},
            new int[]{KEYCODE_TAB,KEYCODE_Q,KEYCODE_W,KEYCODE_E,KEYCODE_R,KEYCODE_T,KEYCODE_Y,KEYCODE_U,KEYCODE_I,KEYCODE_O,KEYCODE_P,KEYCODE_LEFT_BRACKET,KEYCODE_RIGHT_BRACKET,KEYCODE_BACKSLASH},
            new float[]{1.5f,1,1,1,1,1,1,1,1,1,1,1,1,1.5f});
        row(3.35f, new String[]{"Caps","A","S","D","F","G","H","J","K","L",":\n;","\"\n'","Enter"},
            new int[]{KEYCODE_CAPS_LOCK,KEYCODE_A,KEYCODE_S,KEYCODE_D,KEYCODE_F,KEYCODE_G,KEYCODE_H,KEYCODE_J,KEYCODE_K,KEYCODE_L,KEYCODE_SEMICOLON,KEYCODE_APOSTROPHE,KEYCODE_ENTER},
            new float[]{1.75f,1,1,1,1,1,1,1,1,1,1,1,2.25f});
        row(4.35f, new String[]{"Shift","Z","X","C","V","B","N","M","<\n,",">\n.","?\n/","Shift"},
            new int[]{KEYCODE_SHIFT_LEFT,KEYCODE_Z,KEYCODE_X,KEYCODE_C,KEYCODE_V,KEYCODE_B,KEYCODE_N,KEYCODE_M,KEYCODE_COMMA,KEYCODE_PERIOD,KEYCODE_SLASH,KEYCODE_SHIFT_RIGHT},
            new float[]{2.25f,1,1,1,1,1,1,1,1,1,1,2.75f});
        row(5.35f, new String[]{"Ctrl","Win","Alt","Space","Alt","Win","Menu","Ctrl"},
            new int[]{KEYCODE_CTRL_LEFT,KEYCODE_META_LEFT,KEYCODE_ALT_LEFT,KEYCODE_SPACE,KEYCODE_ALT_RIGHT,KEYCODE_META_RIGHT,KEYCODE_MENU,KEYCODE_CTRL_RIGHT},
            new float[]{1.25f,1.25f,1.25f,6.25f,1.25f,1.25f,1.25f,1.25f});
        String[] nav = {"Ins","Home","PgUp","Del","End","PgDn"};
        int[] nc = {KEYCODE_INSERT,KEYCODE_MOVE_HOME,KEYCODE_PAGE_UP,KEYCODE_FORWARD_DEL,KEYCODE_MOVE_END,KEYCODE_PAGE_DOWN};
        for (int i=0;i<6;i++) key(nav[i], nc[i], 15.4f+i%3, 1.35f+i/3);
        key("↑",KEYCODE_DPAD_UP,16.4f,4.35f);
        key("←",KEYCODE_DPAD_LEFT,15.4f,5.35f);
        key("↓",KEYCODE_DPAD_DOWN,16.4f,5.35f);
        key("→",KEYCODE_DPAD_RIGHT,17.4f,5.35f);
        String[] np = {"Num","/","*","−","7\nHome","8\n↑","9\nPgUp","+","4\n←","5\n","6\n→","1\nEnd","2\n↓","3\nPgDn","Enter","0\nIns",".\nDel"};
        int[] pc = {KEYCODE_NUM_LOCK,KEYCODE_NUMPAD_DIVIDE,KEYCODE_NUMPAD_MULTIPLY,KEYCODE_NUMPAD_SUBTRACT,KEYCODE_NUMPAD_7,KEYCODE_NUMPAD_8,KEYCODE_NUMPAD_9,KEYCODE_NUMPAD_ADD,KEYCODE_NUMPAD_4,KEYCODE_NUMPAD_5,KEYCODE_NUMPAD_6,KEYCODE_NUMPAD_1,KEYCODE_NUMPAD_2,KEYCODE_NUMPAD_3,KEYCODE_NUMPAD_ENTER,KEYCODE_NUMPAD_0,KEYCODE_NUMPAD_DOT};
        float[][] pos = {{0,0,1,1},{1,0,1,1},{2,0,1,1},{3,0,1,1},{0,1,1,1},{1,1,1,1},{2,1,1,1},{3,1,1,2},{0,2,1,1},{1,2,1,1},{2,2,1,1},{0,3,1,1},{1,3,1,1},{2,3,1,1},{3,3,1,2},{0,4,2,1},{2,4,1,1}};
        for(int i=0;i<np.length;i++) key(np[i],pc[i],18.8f+pos[i][0],1.35f+pos[i][1],pos[i][2],pos[i][3]);
        keys = Collections.unmodifiableList(building);
    }
}
