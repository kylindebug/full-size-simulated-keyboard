package io.tabletbridge.ime;

import org.junit.Test;
import static org.junit.Assert.*;

public class PanelGeometryTest {
    @Test public void portraitFitPreservesAspectAndLandscapeRestoresSize() {
        PanelGeometry.Box portrait=PanelGeometry.fit(1400,400,800,1200);
        assertEquals(3.5f,portrait.w/portrait.h,.001);
        assertEquals(800,portrait.w,.001);
        PanelGeometry.Box landscape=PanelGeometry.fit(1400,400,1600,900);
        assertEquals(1400,landscape.w,.001); assertEquals(400,landscape.h,.001);
    }
    private final PanelGeometry.Box start=new PanelGeometry.Box(100,100,800,300);
    private PanelGeometry.Box resize(int edge,float x,float y) { return PanelGeometry.resize(start,edge,x,y,400,160,1600,1000); }
    @Test public void rightEdgeChangesWidthOnly() {
        PanelGeometry.Box b=resize(PanelGeometry.RIGHT,200,100);
        assertEquals(1000,b.w,.01); assertEquals(300,b.h,.01); assertEquals(100,b.x,.01);
    }
    @Test public void topEdgeKeepsBottomAnchored() {
        PanelGeometry.Box b=resize(PanelGeometry.TOP,99,50);
        assertEquals(150,b.y,.01); assertEquals(250,b.h,.01); assertEquals(800,b.w,.01);
        assertEquals(400,b.y+b.h,.01);
    }
    @Test public void cornerPreservesAspectAndOppositeAnchor() {
        PanelGeometry.Box b=resize(PanelGeometry.LEFT|PanelGeometry.TOP,200,40);
        assertEquals(800f/300,b.w/b.h,.001); assertEquals(900,b.x+b.w,.01); assertEquals(400,b.y+b.h,.01);
    }
    @Test public void shrinkingRespectsMinimums() {
        PanelGeometry.Box b=resize(PanelGeometry.RIGHT|PanelGeometry.BOTTOM,-2000,-2000);
        assertTrue(b.w>=400); assertTrue(b.h>=160); assertEquals(800f/300,b.w/b.h,.001);
    }
    @Test public void growingCornerCannotExitScreen() {
        PanelGeometry.Box b=resize(PanelGeometry.RIGHT|PanelGeometry.BOTTOM,3000,3000);
        assertTrue(b.x+b.w<=1600.01); assertTrue(b.y+b.h<=1000.01); assertEquals(800f/300,b.w/b.h,.001);
    }
    @Test public void leftEdgeClampsAtScreenBoundary() {
        PanelGeometry.Box b=resize(PanelGeometry.LEFT,-1000,0);
        assertEquals(0,b.x,.01); assertEquals(900,b.w,.01);
    }
    @Test public void smallMultiWindowAllowsSmallerMinimum() {
        PanelGeometry.Box b=PanelGeometry.resize(new PanelGeometry.Box(0,0,300,150),PanelGeometry.RIGHT|PanelGeometry.BOTTOM,500,500,600,210,350,180);
        assertTrue(b.w<=350); assertTrue(b.h<=180); assertTrue(b.w>0 && b.h>0);
    }
}
