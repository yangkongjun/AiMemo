package io.github.floatmemo.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class NoteTest {
    @Test public void emptyNoteHasReadableTitle(){assertEquals("新便签",new Note().displayTitle());}
    @Test public void explicitTitleWinsOverBody(){
        Note n=new Note();n.title="  组会安排  ";n.content="另一个标题\n内容";
        assertEquals("组会安排",n.displayTitle());
    }
    @Test public void titleFallsBackToFirstNonblankBodyLine(){
        Note n=new Note();n.content="\n\n待办清单\n下一行";assertEquals("待办清单",n.displayTitle());
    }
    @Test public void truncationNeverSplitsEmojiSurrogatePair(){
        Note n=new Note();n.content="😀".repeat(32);String title=n.displayTitle();
        assertEquals(29,title.codePointCount(0,title.length()));assertTrue(title.endsWith("…"));
        assertEquals("😀".repeat(28)+"…",title);
    }
    @Test public void copyCanBeEditedWithoutMutatingRepositoryValue(){
        Note n=new Note();n.title="原始";n.folder="论文";n.width=260;
        Note copy=n.copy();copy.title="已修改";copy.folder="";copy.width=999;
        assertEquals("原始",n.title);assertEquals("论文",n.folder);assertEquals(260,n.width);assertEquals(n.id,copy.id);
    }
}
