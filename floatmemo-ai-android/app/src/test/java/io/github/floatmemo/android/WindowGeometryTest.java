package io.github.floatmemo.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class WindowGeometryTest {
    @Test public void phoneRotationKeepsWindowReachable() {
        WindowGeometry g=WindowGeometry.fit(480,600,292,300,720,360);
        assertEquals(428,g.x);assertEquals(60,g.y);
        assertEquals(292,g.width);assertEquals(300,g.height);
    }
    @Test public void hugeRestoredWindowFitsSmallDisplay() {
        WindowGeometry g=WindowGeometry.fit(900,900,1200,800,320,480);
        assertEquals(0,g.x);assertEquals(0,g.y);
        assertEquals(320,g.width);assertEquals(480,g.height);
    }
    @Test public void draggingBeyondTopLeftStopsAtEdge() {
        WindowGeometry g=WindowGeometry.fit(-90,-30,280,240,360,640);
        assertEquals(0,g.x);assertEquals(0,g.y);
    }
    @Test public void resizeKeepsControlsUsable() {
        WindowGeometry g=WindowGeometry.fit(0,0,10,10,360,640);
        assertEquals(220,g.width);assertEquals(180,g.height);
    }
    @Test public void verySmallSplitScreenStillContainsWindow() {
        WindowGeometry g=WindowGeometry.fit(30,80,292,300,150,120);
        assertEquals(150,g.width);assertEquals(120,g.height);
        assertEquals(0,g.x);assertEquals(0,g.y);
    }
    @Test public void invalidDisplayBoundsDoNotProduceNegativeDimensions() {
        WindowGeometry g=WindowGeometry.fit(Integer.MAX_VALUE,Integer.MIN_VALUE,300,300,0,-1);
        assertEquals(1,g.width);assertEquals(1,g.height);
        assertEquals(0,g.x);assertEquals(0,g.y);
    }
}
