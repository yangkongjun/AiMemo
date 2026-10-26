package io.github.floatmemo.android;

import java.util.*;

/** Lifecycle-independent request admission and result cache. Clock is supplied for testing. */
public final class SearchSession<T> {
    public static final class Ticket<T> {public final T value;public final boolean fresh;Ticket(T value,boolean fresh){this.value=value;this.fresh=fresh;}}
    private static final class Cached<T> {final T value;final long at,weight;Cached(T v,long t,long w){value=v;at=t;weight=w;}}
    private final LinkedHashMap<String,Cached<T>> cache=new LinkedHashMap<>(16,.75f,true);
    private final int maxCount;private final long ttl,maxWeight;
    private String activeKey,recentKey;private T active,recent;private long recentAt,weight;
    public SearchSession(int count,long ttl,long maxWeight){maxCount=count;this.ttl=ttl;this.maxWeight=maxWeight;}
    public synchronized boolean busy(){return active!=null;}
    private T cached(String key,long now){Cached<T> v=cache.get(key);if(v==null)return null;if(now-v.at>=ttl){cache.remove(key);weight-=v.weight;return null;}return v.value;}
    public synchronized T find(String key,long now){if(activeKey!=null&&activeKey.equals(key))return active;T cached=cached(key,now);if(cached!=null)return cached;return recentKey!=null&&recentKey.equals(key)&&now-recentAt<ttl?recent:null;}
    public synchronized Ticket<T> acquire(String key,T candidate,long now){
        T saved=cached(key,now);if(saved!=null)return new Ticket<>(saved,false);
        if(active!=null){if(activeKey.equals(key))return new Ticket<>(active,false);throw new IllegalStateException("已有一次AI搜索正在进行，请等待完成；不会额外发起请求");}
        activeKey=key;active=candidate;return new Ticket<>(candidate,true);
    }
    public synchronized void complete(String key,T value,boolean successful,long bytes,long now){
        if(activeKey==null||!activeKey.equals(key))return;active=null;activeKey=null;recent=value;recentKey=key;recentAt=now;
        if(!successful||bytes>maxWeight)return;Cached<T> old=cache.remove(key);if(old!=null)weight-=old.weight;cache.put(key,new Cached<>(value,now,bytes));weight+=bytes;
        while(cache.size()>maxCount||weight>maxWeight){String first=cache.keySet().iterator().next();weight-=cache.remove(first).weight;}
    }
}
