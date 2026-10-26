package io.github.floatmemo.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestOptionsTest {
    @Test public void longNoteStaysOneRequestChunkUnlessActualChunkBoundaryIsCrossed(){assertEquals(1,AiDocument.chunks("笔".repeat(3000)).size());assertEquals(1,AiDocument.chunks("笔".repeat(10000)).size());assertEquals(2,AiDocument.chunks("笔".repeat(10001)).size());}
    @Test public void oldSettingsHaveLargerIndependentDefaults(){RequestOptions o=RequestOptions.defaults();assertEquals(8192,o.analyzeTokens);assertEquals(4096,o.searchTokens);assertEquals(120,o.readSeconds);assertEquals(10,o.candidates);assertEquals(12000,o.searchChars);}
    @Test public void illegalBudgetsFailRatherThanSilentlyChangingValue(){int[][] bad={{511,4096,120,10,12000},{32769,4096,120,10,12000},{8192,511,120,10,12000},{8192,16385,120,10,12000},{8192,4096,29,10,12000},{8192,4096,301,10,12000},{8192,4096,120,4,12000},{8192,4096,120,31,12000},{8192,4096,120,10,1999},{8192,4096,120,10,30001}};
        for(int[] a:bad)try{new RequestOptions(a[0],a[1],a[2],a[3],a[4]);fail();}catch(IllegalArgumentException expected){}}
}
