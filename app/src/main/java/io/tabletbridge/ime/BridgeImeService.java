package io.tabletbridge.ime;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.Toast;

public final class BridgeImeService extends InputMethodService implements KeyboardView.Actions {
    private FrameLayout root;
    private KeyboardView keyboard;
    private KeyEngine engine;
    private InputConnection session;
    private SharedPreferences prefs;
    private float scale=.96f, heightFraction=0, positionX=.5f, positionY=.85f;
    private PanelGeometry.Box resizeStart;
    private float preferredWidth,preferredHeight;
    private boolean failurePosted;
    private int generation;
    @Override public void onCreate() {
        super.onCreate();
        prefs=getSharedPreferences("panel",MODE_PRIVATE);
        scale=prefs.getFloat("scale",.96f); positionX=prefs.getFloat("x",.5f); positionY=prefs.getFloat("y",.85f);
        heightFraction=prefs.getFloat("heightFraction",0);
        preferredWidth=prefs.getFloat("preferredWidth",0); preferredHeight=prefs.getFloat("preferredHeight",0);
        engine=new KeyEngine(this::send,SystemClock::uptimeMillis);
        engine.setRepeatTiming(ViewConfiguration.getKeyRepeatTimeout(),ViewConfiguration.getKeyRepeatDelay());
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public boolean onEvaluateInputViewShown() { super.onEvaluateInputViewShown(); return true; }
    @Override public void onConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        super.onConfigureWindow(window,false,candidatesOnly);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.BOTTOM);
    }
    @Override public View onCreateInputView() {
        root=new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        // InputMethodService reparents the input view with WRAP_CONTENT. A minimum
        // height lets its container fill the available IME window before panel sizing.
        root.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        keyboard=new KeyboardView(this,engine,this);
        root.addView(keyboard,new FrameLayout.LayoutParams(1,1));
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)-> {
            if(r-l!=or-ol || b-t!=ob-ot) { keyboard.clearTouches(); place(); }
        });
        root.post(this::place);
        return root;
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private void place() {
        if(root==null || root.getWidth()==0 || root.getHeight()==0) return;
        int maxW=Math.max(1,root.getWidth()-dp(12));
        int width=keyboard.collapsed()?Math.min(dp(280),maxW):(int)(maxW*scale);
        int height=keyboard.collapsed()||heightFraction==0?keyboard.desiredHeight(width):Math.round(heightFraction*root.getHeight());
        if(!keyboard.collapsed()) {
            float density=getResources().getDisplayMetrics().density;
            if(preferredWidth<=0 || preferredHeight<=0) {
                preferredWidth=width/density; preferredHeight=height/density;
                prefs.edit().putFloat("preferredWidth",preferredWidth).putFloat("preferredHeight",preferredHeight).apply();
            }
            PanelGeometry.Box fitted=PanelGeometry.fit(preferredWidth*density,preferredHeight*density,maxW,root.getHeight());
            width=Math.round(fitted.w); height=Math.round(fitted.h);
        }
        width=Math.max(1,Math.min(width,maxW)); height=Math.max(1,Math.min(height,root.getHeight()));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height);
        keyboard.setLayoutParams(lp);
        keyboard.setX(Math.max(0,(root.getWidth()-width)*positionX));
        keyboard.setY(Math.max(0,(root.getHeight()-height)*positionY));
        root.requestLayout();
    }
    @Override public void onComputeInsets(Insets out) {
        // Full-height transparent IME; only the actual panel intercepts touches.
        int height=getWindow().getWindow().getDecorView().getHeight();
        out.contentTopInsets=height; out.visibleTopInsets=height;
        out.touchableInsets=Insets.TOUCHABLE_INSETS_REGION;
        out.touchableRegion.setEmpty();
        if(keyboard!=null && keyboard.isShown()) {
            int[] loc=new int[2]; keyboard.getLocationInWindow(loc);
            out.touchableRegion.set(loc[0],loc[1],loc[0]+keyboard.getWidth(),loc[1]+keyboard.getHeight());
        }
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting) {
        endSession(); super.onStartInput(info,restarting);
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        endSession(); session=getCurrentInputConnection();
        engine.setCompatibilityRepeat(prefs.getBoolean("compatibilityRepeat",false));
        if(keyboard!=null) root.post(this::place);
    }
    private boolean send(KeyEngine.Event e) {
        boolean ok=false;
        if(session!=null) {
            KeyEvent event=new KeyEvent(e.downTime,e.eventTime,e.down?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,
                    e.code,e.repeatCount,e.meta,KeyCharacterMap.VIRTUAL_KEYBOARD,0,
                    KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE,InputDevice.SOURCE_KEYBOARD);
            try { ok=session.sendKeyEvent(event); } catch(RuntimeException ignored) { ok=false; }
        }
        if(!ok && keyboard!=null && !failurePosted) {
            failurePosted=true;
            final int failedGeneration=generation;
            keyboard.post(()-> {
                if(failedGeneration==generation) {
                    keyboard.clearTouches(); session=null;
                    Toast.makeText(this,"连接不可用，请在 Moonlight 内重新呼出键盘",Toast.LENGTH_SHORT).show();
                }
                failurePosted=false;
            });
        }
        return ok;
    }
    private void endSession() {
        generation++;
        if(keyboard!=null) keyboard.finishEditing();
        if(engine!=null) engine.resetSession();
        session=null;
    }
    @Override public void onFinishInputView(boolean finishingInput) { endSession(); super.onFinishInputView(finishingInput); }
    @Override public void onFinishInput() { endSession(); super.onFinishInput(); }
    @Override public void onWindowHidden() { endSession(); super.onWindowHidden(); }
    @Override public void onUnbindInput() { endSession(); super.onUnbindInput(); }
    @Override public void onConfigurationChanged(Configuration c) { endSession(); super.onConfigurationChanged(c); }
    @Override public void onDestroy() { endSession(); super.onDestroy(); }
    @Override public void drag(float dx,float dy) {
        keyboard.setX(Math.max(0,Math.min(root.getWidth()-keyboard.getWidth(),keyboard.getX()+dx)));
        keyboard.setY(Math.max(0,Math.min(root.getHeight()-keyboard.getHeight(),keyboard.getY()+dy)));
        root.requestLayout();
    }
    @Override public void savePosition() {
        positionX=keyboard.getX()/Math.max(1,root.getWidth()-keyboard.getWidth());
        positionY=keyboard.getY()/Math.max(1,root.getHeight()-keyboard.getHeight());
        SharedPreferences.Editor edit=prefs.edit().putFloat("x",positionX).putFloat("y",positionY);
        if(!keyboard.collapsed()) {
            scale=keyboard.getWidth()/(float)Math.max(1,root.getWidth()-dp(12));
            heightFraction=keyboard.getHeight()/(float)Math.max(1,root.getHeight());
            edit.putFloat("scale",scale).putFloat("heightFraction",heightFraction);
        }
        edit.apply();
    }
    @Override public void beginResize() {
        resizeStart=new PanelGeometry.Box(keyboard.getX(),keyboard.getY(),keyboard.getWidth(),keyboard.getHeight());
    }
    @Override public void resize(int edges,float dx,float dy) {
        if(resizeStart==null) return;
        PanelGeometry.Box b=PanelGeometry.resize(resizeStart,edges,dx,dy,dp(600),dp(210),root.getWidth(),root.getHeight());
        float density=getResources().getDisplayMetrics().density;
        preferredWidth=b.w/density; preferredHeight=b.h/density;
        prefs.edit().putFloat("preferredWidth",preferredWidth).putFloat("preferredHeight",preferredHeight).apply();
        keyboard.setLayoutParams(new FrameLayout.LayoutParams(Math.round(b.w),Math.round(b.h)));
        keyboard.setX(b.x); keyboard.setY(b.y); root.requestLayout();
    }
    @Override public void collapse() { keyboard.setCollapsed(!keyboard.collapsed()); place(); }
    @Override public void hide() { endSession(); requestHideSelf(0); }
}
