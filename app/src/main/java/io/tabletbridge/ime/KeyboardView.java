package io.tabletbridge.ime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/** Canvas and hit testing share the exact same key/control geometry. */
public final class KeyboardView extends View {
    public interface Actions {
        void drag(float dx,float dy);
        void beginResize();
        void resize(int edges,float dx,float dy);
        void collapse();
        void hide();
        void savePosition();
    }
    private final KeyEngine engine;
    private final Actions actions;
    private final Runnable repeatKey=new Runnable() {
        @Override public void run() {
            if(!engine.repeat()) { clearTouches(); return; }
            scheduleRepeat();
        }
    };
    private void scheduleRepeat() {
        removeCallbacks(repeatKey);
        long delay=engine.nextRepeatDelay();
        if(delay>=0) postDelayed(repeatKey,delay);
    }
    private final KeyboardLayout layout=new KeyboardLayout();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF box=new RectF(),wallpaperBox=new RectF();
    private final Path clip=new Path();
    private final Map<Integer,KeyboardLayout.Key> fingers=new HashMap<>();
    private final float density;
    private final Bitmap wallpaper;
    private float unitX,unitY,opacity=.96f;
    private int moving=-1,resizeEdges;
    private float lastX,lastY,originX,originY;
    private boolean collapsed,editingSize;
    private boolean sliderVisible;
    private boolean modeTipVisible;
    private final Runnable dismissModeTip=()-> { modeTipVisible=false; invalidate(); };
    private void hideModeTip() { removeCallbacks(dismissModeTip); modeTipVisible=false; }
    private void showModeTip() { hideModeTip(); modeTipVisible=true; postDelayed(dismissModeTip,6500); }
    private int sliding=-1;
    private void sliderBounds() { box.set(padding()+18.8f*unitX,padding()+1.3f*unitY,padding()+22.8f*unitX,padding()+2.1f*unitY); }
    private void slide(float x) {
        sliderBounds(); opacity=Math.max(.15f,Math.min(1f,.15f+.85f*(x-box.left-dp(12))/(box.width()-dp(24))));
        getContext().getSharedPreferences("panel",0).edit().putFloat("opacity",opacity).apply(); invalidate();
    }
    private boolean dragSpace(float x,float y) {
        float ux=(x-padding())/unitX,uy=(y-padding())/unitY;
        return ux>=0&&ux<18.4f&&uy>1&&uy<1.35f || ux>=15.4f&&ux<18.4f&&uy>=3.35f&&uy<4.35f;
    }
    public KeyboardView(Context context,KeyEngine engine,Actions actions) {
        super(context); this.engine=engine; this.actions=actions;
        density=getResources().getDisplayMetrics().density;
        opacity=getContext().getSharedPreferences("panel",0).getFloat("opacity",.96f);
        wallpaper=BitmapFactory.decodeResource(getResources(),R.drawable.ganyu_sakura);
        setFocusable(false); setContentDescription("甘雨樱花全尺寸键盘");
    }
    private float dp(float n) { return n*density; }
    private float padding() { return dp(12); }
    public int desiredHeight(int width) {
        return collapsed?(int)dp(44):(int)(2*padding()+KeyboardLayout.HEIGHT*Math.min((width-2*padding())/KeyboardLayout.WIDTH*1.07f,dp(58)));
    }
    public void setCollapsed(boolean value) { finishEditing(); collapsed=value; invalidate(); }
    public boolean collapsed() { return collapsed; }
    public void clearTouches() { removeCallbacks(repeatKey); fingers.clear(); moving=-1; sliding=-1; resizeEdges=0; setAlpha(1f); engine.releaseAll(); invalidate(); }
    public void finishEditing() { clearTouches(); hideModeTip(); sliderVisible=false; editingSize=false; updateGestureExclusion(); }
    private void updateGestureExclusion() {
        if(Build.VERSION.SDK_INT<29) return;
        ArrayList<Rect> regions=new ArrayList<>();
        if(editingSize) {
            int size=(int)dp(48),w=getWidth(),h=getHeight();
            // Three 48dp segments per side stay within Android's gesture exclusion budget.
            int[] ys={0,Math.max(0,(h-size)/2),Math.max(0,h-size)};
            for(int y:ys) {
                regions.add(new Rect(0,y,Math.min(size,w),Math.min(y+size,h)));
                regions.add(new Rect(Math.max(0,w-size),y,w,Math.min(y+size,h)));
            }
        }
        setSystemGestureExclusionRects(regions);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        unitX=Math.max(1,(w-2*padding())/KeyboardLayout.WIDTH);
        unitY=Math.max(1,(h-2*padding())/KeyboardLayout.HEIGHT);
        clip.reset(); box.set(0,0,w,h); clip.addRoundRect(box,dp(13),dp(13),Path.Direction.CW);
        float ratio=Math.max(w/(float)wallpaper.getWidth(),h/(float)wallpaper.getHeight());
        float bw=wallpaper.getWidth()*ratio,bh=wallpaper.getHeight()*ratio;
        wallpaperBox.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);
        updateGestureExclusion();
    }
    private void bounds(KeyboardLayout.Key k) {
        float gx=Math.max(dp(1.8f),unitX*.04f),gy=Math.max(dp(1.8f),unitY*.04f);
        box.set(padding()+k.x*unitX+gx,padding()+k.y*unitY+gy,
                padding()+(k.x+k.w)*unitX-gx,padding()+(k.y+k.h)*unitY-gy);
    }
    private void controlBounds(int i) {
        float x=18.8f+(i<4?i:(i-4)*2f),y=i<4?0:.62f,w=i<4?1:2;
        box.set(padding()+x*unitX+dp(2),padding()+y*unitY+dp(2),padding()+(x+w)*unitX-dp(2),padding()+(y+.58f)*unitY-dp(2));
    }
    private void text(Canvas c,String value,float x,float y,float size,int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    private void surface(Canvas c,int fill,int border,float radius) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(fill); c.drawRoundRect(box,radius,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(.8f)); paint.setColor(border);
        c.drawRoundRect(box,radius,radius,paint); paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save(); c.clipPath(clip);
        paint.setColor(0xffbcc7e4); paint.setAlpha((int)(255*opacity));
        c.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setAlpha((int)(255*opacity)); c.drawBitmap(wallpaper,null,wallpaperBox,paint); paint.setAlpha(255);
        if(collapsed) {
            paint.setColor(0xdcecf0ff); c.drawRect(0,0,getWidth(),getHeight(),paint);
            text(c,"✿  拖动",getWidth()*.30f,getHeight()/2f,dp(12),0xff4b5079);
            text(c,"展开",getWidth()*.75f,getHeight()/2f,dp(12),0xff4b5079);
            text(c,"×",getWidth()*.94f,getHeight()/2f,dp(19),0xff4b5079);
            c.restore(); return;
        }
        for(KeyboardLayout.Key key:layout.keys) {
            bounds(key);
            boolean lit=engine.isLit(KeyboardLayout.eventCode(key.code));
            int fill=KeyboardColors.fill(key.code,lit,engine.numericMode());
            surface(c,fill,KeyboardColors.border(key.code,lit),dp(6));
            String[] lines=key.label.split("\n",-1);
            float font=Math.min(dp(15),Math.min(unitX*.29f,unitY*.31f));
            if(lines.length==1 && key.label.length()>4) font=Math.min(font,box.width()/key.label.length()*1.6f);
            int fg=0xff303956;
            if(lines.length==1) text(c,lines[0],box.centerX(),box.centerY(),font,fg);
            else {
                text(c,lines[0],box.centerX(),box.centerY()-unitY*.18f,font,fg);
                text(c,lines[1],box.centerX(),box.centerY()+unitY*.19f,font*.80f,0xff596182);
            }
            if(key.code==KeyEvent.KEYCODE_F || key.code==KeyEvent.KEYCODE_J || key.code==KeyEvent.KEYCODE_NUMPAD_5) {
                paint.setColor(0xff9499bb);
                c.drawRoundRect(box.centerX()-unitX*.08f,box.bottom-dp(4),box.centerX()+unitX*.08f,box.bottom-dp(2.5f),dp(1),dp(1),paint);
            }
        }
        String[] tools={editingSize?"完成":"大小","透明度","","×",engine.sticky()?"锁定 ●":"物理","释放"};
        for(int i=0;i<tools.length;i++) {
            controlBounds(i);
            boolean active=(i==0&&editingSize)||(i==4&&engine.sticky());
            surface(c,active?0xffe8b7d7:0xeddee5fa,0xeaffffff,dp(5));
            if(i==2 || i==3) {
                // Both icons share the close glyph's scale, stroke and alignment.
                float iconSize=Math.min(dp(17),Math.min(unitX*.20f,unitY*.25f));
                float cx=box.centerX(),cy=box.centerY(),half=iconSize*.32f;
                paint.setColor(0xff404567); paint.setStrokeWidth(iconSize*.10f); paint.setStrokeCap(Paint.Cap.ROUND);
                if(i==2) {
                    c.drawLine(cx-half,cy-half*.4f,cx,cy+half*.4f,paint);
                    c.drawLine(cx,cy+half*.4f,cx+half,cy-half*.4f,paint);
                } else {
                    c.drawLine(cx-half,cy-half,cx+half,cy+half,paint);
                    c.drawLine(cx-half,cy+half,cx+half,cy-half,paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
            } else text(c,tools[i],box.centerX(),box.centerY(),Math.min(dp(i==3?17:11),Math.min(unitX*.20f,unitY*.25f)),0xff404567);
        }
        if(sliderVisible) {
            sliderBounds(); surface(c,0xffeef0fc,0xffb477ac,dp(8));
            float left=box.left+dp(12),right=box.right-dp(12),cy=box.centerY()+dp(6);
            text(c,"背景不透明度 "+Math.round(opacity*100)+"%",box.centerX(),box.top+dp(10),dp(10),0xff404567);
            paint.setColor(0xffb2b8d2); paint.setStrokeWidth(dp(3)); c.drawLine(left,cy,right,cy,paint);
            paint.setColor(0xffad78a7); c.drawCircle(left+(right-left)*(opacity-.15f)/.85f,cy,dp(6),paint);
        }
        if(modeTipVisible) drawModeTip(c);
        c.restore();
        if(editingSize) drawHandles(c);
    }
    private void drawModeTip(Canvas c) {
        String[] lines=engine.sticky()
                ?new String[]{"锁定模式：点按保持修饰键", "Ctrl / Shift / Alt / Win 点一次按住，", "再点一次释放；普通键仍随手指按下、弹起。", "点「释放」或切换模式可释放保持的按键。"}
                :new String[]{"物理模式：按住才生效，松手即释放", "组合键需同时按住，例如 Ctrl + C。", "点此按钮切换到锁定模式，", "可依次点按修饰键和普通键组成快捷键。"};
        float width=Math.min(getWidth()-2*padding(),dp(380));
        float font=Math.min(dp(12),(width-dp(24))/23f),lineHeight=font*1.65f;
        float right=getWidth()-padding(),top=padding()+1.25f*unitY;
        box.set(right-width,top,right,top+lineHeight*4+dp(16));
        surface(c,0xfff4f1fc,0xffb477ac,dp(8));
        for(int i=0;i<lines.length;i++) text(c,lines[i],box.centerX(),top+dp(8)+lineHeight*(i+.5f),font,0xff303956);
    }
    private void drawHandles(Canvas c) {
        box.set(dp(2),dp(2),getWidth()-dp(2),getHeight()-dp(2));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(0xffb477ac);
        c.drawRoundRect(box,dp(12),dp(12),paint); paint.setStyle(Paint.Style.FILL);
        float[] xs={dp(5),getWidth()/2f,getWidth()-dp(5)},ys={dp(5),getHeight()/2f,getHeight()-dp(5)};
        for(int x=0;x<3;x++) for(int y=0;y<3;y++) if(x!=1||y!=1) {
            paint.setColor(0xfffaf0fa); c.drawCircle(xs[x],ys[y],dp(5),paint);
            paint.setColor(0xffac77a5); c.drawCircle(xs[x],ys[y],dp(2.5f),paint);
        }
    }
    private KeyboardLayout.Key hit(float x,float y) {
        for(KeyboardLayout.Key key:layout.keys) { bounds(key); if(box.contains(x,y)) return key; } return null;
    }
    private int hitControl(float x,float y) {
        for(int i=0;i<6;i++) { controlBounds(i); if(box.contains(x,y)) return i; } return -1;
    }
    private int hitEdge(float x,float y) {
        float zone=dp(20);
        return (x<zone?PanelGeometry.LEFT:x>getWidth()-zone?PanelGeometry.RIGHT:0)
                |(y<zone?PanelGeometry.TOP:y>getHeight()-zone?PanelGeometry.BOTTOM:0);
    }
    private float screenX(MotionEvent e,int index) { return e.getX(index)+e.getRawX()-e.getX(0); }
    private float screenY(MotionEvent e,int index) { return e.getY(index)+e.getRawY()-e.getY(0); }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int index=e.getActionIndex(),id=e.getPointerId(index);
        float x=e.getX(index),y=e.getY(index);
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if(moving>=0||sliding>=0) break;
                hideModeTip();
                if(sliderVisible) {
                    sliderBounds();
                    if(box.contains(x,y)) { clearTouches(); sliding=id; slide(x); break; }
                    sliderVisible=false; invalidate();
                }
                if(!collapsed&&!editingSize&&dragSpace(x,y)) {
                    clearTouches(); moving=id; setAlpha(.55f); lastX=screenX(e,index); lastY=screenY(e,index); break;
                }
                int control=collapsed?-1:hitControl(x,y);
                int edge=editingSize&&control<0?hitEdge(x,y):0;
                if(edge!=0) {
                    clearTouches(); resizeEdges=edge; moving=id; actions.beginResize();
                    originX=screenX(e,index); originY=screenY(e,index);
                } else if(collapsed || control>=0) {
                    clearTouches();
                    if(collapsed&&x/getWidth()<.62f) {
                        moving=id; setAlpha(.55f); lastX=screenX(e,index); lastY=screenY(e,index);
                    } else if(collapsed) { if(x/getWidth()>.88f) actions.hide(); else actions.collapse(); }
                    else switch(control) {
                        case 0:
                            editingSize=!editingSize;
                            updateGestureExclusion();
                            if(editingSize) Toast.makeText(getContext(),"拖动边缘改变比例，拖动四角等比缩放；点「完成」恢复打字",Toast.LENGTH_SHORT).show();
                            invalidate(); break;
                        case 1: sliderVisible=true; sliding=id; invalidate(); break;
                        case 2: actions.collapse(); break;
                        case 3: actions.hide(); break;
                        case 4: engine.setSticky(!engine.sticky()); showModeTip(); invalidate(); break;
                        case 5: invalidate(); break;
                    }
                } else if(!editingSize) {
                    KeyboardLayout.Key key=hit(x,y);
                    if(key!=null) { fingers.put(id,key); engine.pointerDown(id,KeyboardLayout.eventCode(key.code)); invalidate(); }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(sliding>=0) { int si=e.findPointerIndex(sliding); if(si>=0) slide(e.getX(si)); break; }
                if(moving>=0) {
                    int mi=e.findPointerIndex(moving);
                    if(mi>=0) {
                        float rx=screenX(e,mi),ry=screenY(e,mi);
                        if(resizeEdges!=0) actions.resize(resizeEdges,rx-originX,ry-originY);
                        else { actions.drag(rx-lastX,ry-lastY); lastX=rx; lastY=ry; }
                    }
                } else for(int i=0;i<e.getPointerCount();i++) {
                    int pid=e.getPointerId(i); KeyboardLayout.Key key=fingers.get(pid);
                    if(key!=null) {
                        bounds(key); box.inset(-dp(4),-dp(4));
                        if(!box.contains(e.getX(i),e.getY(i))) { fingers.remove(pid); engine.pointerUp(pid); invalidate(); }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if(id==sliding) sliding=-1;
                if(id==moving) { moving=-1; resizeEdges=0; setAlpha(1f); actions.savePosition(); }
                fingers.remove(id); engine.pointerUp(id); invalidate(); performClick(); break;
            case MotionEvent.ACTION_CANCEL:
                if(moving>=0) actions.savePosition(); clearTouches(); break;
        }
        scheduleRepeat();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { hideModeTip(); clearTouches(); super.onDetachedFromWindow(); }
}
