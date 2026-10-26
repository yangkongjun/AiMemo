package io.github.floatmemo.android;
public final class ResultGate {
    private ResultGate(){}
    public static boolean accept(long current,long requested,boolean locked){return current==requested&&!locked;}
}
