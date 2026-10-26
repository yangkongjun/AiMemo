package io.github.floatmemo.android;
public final class ResultGate {
    private ResultGate(){}
    public static boolean accept(Note current,Note requested){return current!=null&&!current.aiLocked&&current.aiGeneration==requested.aiGeneration&&NoteBodyParser.fingerprint(current).equals(NoteBodyParser.fingerprint(requested));}
    public static boolean accept(long current,long requested,boolean locked){return current==requested&&!locked;}
}
