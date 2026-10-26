package io.github.floatmemo.android;

/** Immutable, per-operation budgets. App limits do not imply provider support. */
public final class RequestOptions {
    public final int analyzeTokens, searchTokens, readSeconds, candidates, searchChars;
    public final boolean streaming;
    public static RequestOptions defaults(){return new RequestOptions(8192,4096,120,10,12000);}
    public RequestOptions(int analyze,int search,int seconds,int count,int chars){this(analyze,search,seconds,count,chars,true);}
    public RequestOptions(int analyze,int search,int seconds,int count,int chars,boolean stream){
        streaming=stream;
        analyzeTokens=range(analyze,512,32768,"整理生成上限");
        searchTokens=range(search,512,16384,"搜索生成上限");
        readSeconds=range(seconds,30,300,"读取等待秒数");
        candidates=range(count,5,30,"搜索候选数");
        searchChars=range(chars,2000,30000,"搜索正文字符预算");
    }
    private static int range(int value,int min,int max,String label){
        if(value<min||value>max)throw new IllegalArgumentException(label+"须为"+min+"至"+max);return value;
    }
    public String cacheKey(){return analyzeTokens+":"+searchTokens+":"+readSeconds+":"+candidates+":"+searchChars+":"+streaming;}
}
