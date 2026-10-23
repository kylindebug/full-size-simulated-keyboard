package io.tabletbridge.ime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Real InputConnection receiver: displays events only in memory, never logs typed keys. */
public final class DiagnosticActivity extends Activity {
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setContentView(new Receiver(this)); }
    private static final class Receiver extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayDeque<String> lines=new ArrayDeque<>();
        private final Set<Integer> held=new HashSet<>();
        private final float d;
        Receiver(Context context) {
            super(context); d=getResources().getDisplayMetrics().density;
            setBackgroundColor(0xff10202b); setFocusable(true); setFocusableInTouchMode(true); requestFocus();
            setContentDescription("按键事件测试区域，点击呼出键盘");
        }
        @Override public boolean onCheckIsTextEditor() { return true; }
        @Override public InputConnection onCreateInputConnection(EditorInfo info) {
            info.inputType=InputType.TYPE_NULL; info.imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new BaseInputConnection(this,false) {
                @Override public boolean sendKeyEvent(KeyEvent e) {
                    post(()->receive(e)); return true;
                }
            };
        }
        private void receive(KeyEvent e) {
            if(e.getAction()==KeyEvent.ACTION_DOWN) held.add(e.getKeyCode());
            else if(e.getAction()==KeyEvent.ACTION_UP) held.remove(e.getKeyCode());
            String name=KeyEvent.keyCodeToString(e.getKeyCode()).replace("KEYCODE_","");
            lines.addFirst((e.getAction()==KeyEvent.ACTION_DOWN?"DOWN  ":"UP    ")+name+"   meta=0x"+Integer.toHexString(e.getMetaState())+"   held="+held.size());
            while(lines.size()>12) lines.removeLast(); invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            paint.setColor(0xff69e2c2); paint.setTextSize(22*d);
            c.drawText("按键事件测试",24*d,65*d,paint);
            paint.setColor(0xffe9f0f9); paint.setTextSize(14*d);
            c.drawText("点击空白处呼出键盘 · 拖走键盘查看记录",24*d,95*d,paint);
            c.drawText("同时按住 Ctrl + W；松开后 held 应回到 0",24*d,120*d,paint);
            paint.setTextSize(12*d); float y=155*d;
            for(String line:lines) { c.drawText(line,24*d,y,paint); y+=23*d; }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_UP) {
                requestFocus(); ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(this,InputMethodManager.SHOW_IMPLICIT); performClick();
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
