package io.github.floatmemo.android;
import java.util.*;

public final class SearchEngine {
    public static final class Hit { public final Note note; public final String quote,reason; public final double score;
        public Hit(Note n,String q,String r,double s){note=n;quote=q;reason=r;score=s;} }
    private SearchEngine(){}
    private static Set<String> grams(String value){int[] a=value.toLowerCase(Locale.ROOT).replaceAll("\\s+","").codePoints().toArray();Set<String>s=new HashSet<>();for(int i=0;i<a.length;i++){s.add(new String(a,i,1));if(i+1<a.length)s.add(new String(a,i,2));}return s;}
    public static double overlap(String query,String text){Set<String>a=grams(query),b=grams(text);if(a.isEmpty())return 0;int n=0;for(String s:a)if(b.contains(s))n++;return (double)n/a.size();}
    public static List<Hit> local(List<Note> notes,String query){List<Hit> hits=new ArrayList<>();String q=query.trim().toLowerCase(Locale.ROOT);
        for(Note n:notes){String text=n.title+"\n"+n.content+"\n"+n.folder+"\n"+n.tags;
            if(!q.isEmpty()&&!text.toLowerCase(Locale.ROOT).contains(q))continue;
            double score=q.isEmpty()?0:(n.title.toLowerCase(Locale.ROOT).contains(q)?50:0)+(n.tags.toLowerCase(Locale.ROOT).contains(q)?20:0)+overlap(q,text);
            int at=q.isEmpty()?0:n.content.toLowerCase(Locale.ROOT).indexOf(q);int start=Math.max(0,at-40);if(start>0&&Character.isLowSurrogate(n.content.charAt(start)))start--;
            String snippet=n.content.substring(start,Math.min(n.content.length(),start+180));hits.add(new Hit(n,snippet,q.isEmpty()?"":"文字匹配 · 标题、原文、分类或标签",score));}
        hits.sort(Comparator.comparingDouble((Hit h)->h.score).reversed());return hits;
    }
    public static List<Note> candidates(List<Note> notes,String query){List<Note> r=new ArrayList<>(notes);r.sort(Comparator.comparingDouble((Note n)->overlap(query,n.title+"\n"+n.content+"\n"+n.tags)).reversed());return r.subList(0,Math.min(30,r.size()));}
    public static List<Hit> related(Note source,List<Note> all){List<Hit> r=new ArrayList<>();for(Note n:all){if(n.id.equals(source.id))continue;
        double score=overlap(source.tags.isEmpty()?source.content.substring(0,Math.min(150,source.content.length())):source.tags,n.tags+"\n"+n.content.substring(0,Math.min(2000,n.content.length())));
        if(score<.25)continue;String reason=!source.tags.isEmpty()&&!n.tags.isEmpty()?"标签或内容相近 · "+n.tags:"内容措辞有重合，是否相关由你判断";
        r.add(new Hit(n,n.content.substring(0,Math.min(150,n.content.length())),reason,score));}
        r.sort(Comparator.comparingDouble((Hit h)->h.score).reversed());return r.subList(0,Math.min(5,r.size()));}
}
