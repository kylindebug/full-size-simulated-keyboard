package io.tabletbridge.ime;

/** Edges change aspect; corners preserve aspect and the opposite anchor. */
public final class PanelGeometry {
    public static Box fit(float w,float h,float maxW,float maxH) {
        float factor=Math.min(1f,Math.min(maxW/w,maxH/h));
        return new Box(0,0,w*factor,h*factor);
    }
    public static final int LEFT=1, RIGHT=2, TOP=4, BOTTOM=8;
    public static final class Box {
        public final float x,y,w,h;
        public Box(float x,float y,float w,float h) { this.x=x; this.y=y; this.w=w; this.h=h; }
    }
    private static float clamp(float n,float lo,float hi) { return Math.max(lo,Math.min(hi,n)); }
    public static Box resize(Box s,int edge,float dx,float dy,float minW,float minH,float screenW,float screenH) {
        boolean left=(edge&LEFT)!=0,right=(edge&RIGHT)!=0,top=(edge&TOP)!=0,bottom=(edge&BOTTOM)!=0;
        float maxW=left?s.x+s.w:screenW-s.x,maxH=top?s.y+s.h:screenH-s.y;
        minW=Math.min(minW,maxW); minH=Math.min(minH,maxH);
        float w=s.w+(left?-dx:right?dx:0),h=s.h+(top?-dy:bottom?dy:0);
        if((left||right)&&(top||bottom)) {
            float sw=w/s.w,sh=h/s.h,scale=Math.abs(sw-1)>Math.abs(sh-1)?sw:sh;
            float upper=Math.min(maxW/s.w,maxH/s.h),lower=Math.min(upper,Math.max(minW/s.w,minH/s.h));
            scale=clamp(scale,lower,upper); w=s.w*scale; h=s.h*scale;
        } else {
            if(left||right) w=clamp(w,minW,maxW);
            if(top||bottom) h=clamp(h,minH,maxH);
        }
        return new Box(left?s.x+s.w-w:s.x,top?s.y+s.h-h:s.y,w,h);
    }
}
