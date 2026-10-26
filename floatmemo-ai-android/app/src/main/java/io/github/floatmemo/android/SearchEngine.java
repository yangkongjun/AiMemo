package io.github.floatmemo.android;
import java.util.*;

public final class SearchEngine {
    public static final class Hit { public final Note note; public final String quote,reason; public final double score; public final int sourceStart;
        public Hit(Note n,String q,String r,double s){this(n,q,r,s,NoteBodyParser.locate(n.content,q));}
        public Hit(Note n,String q,String r,double s,int start){note=n;quote=q;reason=r;score=s;sourceStart=start;} }
    private SearchEngine(){}
    private static Set<String> grams(String value){int[] a=value.toLowerCase(Locale.ROOT).replaceAll("\\s+","").codePoints().toArray();Set<String>s=new HashSet<>();for(int i=0;i<a.length;i++){s.add(new String(a,i,1));if(i+1<a.length)s.add(new String(a,i,2));}return s;}
    public static double overlap(String query,String text){Set<String>a=grams(query),b=grams(text);if(a.isEmpty())return 0;int n=0;for(String s:a)if(b.contains(s))n++;return (double)n/a.size();}
    public static List<Hit> local(List<Note> notes,String query){List<Hit> hits=new ArrayList<>();String q=query.trim().toLowerCase(Locale.ROOT);
        for(Note n:notes){String text=n.title+"\n"+NoteBodyParser.text(n.content)+"\n"+n.folder+"\n"+n.tags;
            if(!q.isEmpty()&&!text.toLowerCase(Locale.ROOT).contains(q))continue;
            double score=q.isEmpty()?0:(n.title.toLowerCase(Locale.ROOT).contains(q)?50:0)+(n.tags.toLowerCase(Locale.ROOT).contains(q)?20:0)+overlap(q,text);
            int at=q.isEmpty()?0:n.content.toLowerCase(Locale.ROOT).indexOf(q);int start=Math.max(0,at-40);if(start>0&&Character.isLowSurrogate(n.content.charAt(start)))start--;
            String snippet="";for(NoteBodyParser.Part part:NoteBodyParser.parts(n.content))if(part.imageId.isEmpty()&&!part.text.trim().isEmpty()){
                int hit=q.isEmpty()?0:SearchPlanner.indexOf(part.text,q,0);if(snippet.isEmpty()||hit>=0){int left=NoteBodyParser.floorBoundary(part.text,Math.max(0,hit-40));snippet=NoteBodyParser.bounded(part.text.substring(left),180);if(hit>=0)break;}}
            hits.add(new Hit(n,snippet,q.isEmpty()?"":"文字匹配 · 标题、原文、分类或标签",score));}
        hits.sort(Comparator.comparingDouble((Hit h)->h.score).reversed());return hits;
    }
    public static List<Note> candidates(List<Note> notes,String query){List<Note> r=new ArrayList<>(notes);r.sort(Comparator.comparingDouble((Note n)->overlap(query,n.title+"\n"+n.content+"\n"+n.tags)).reversed());return r.subList(0,Math.min(30,r.size()));}
    public static List<Hit> related(Note source,List<Note> all){List<Hit> r=new ArrayList<>();for(Note n:all){if(n.id.equals(source.id))continue;
        double score=overlap(source.tags.isEmpty()?NoteBodyParser.bounded(NoteBodyParser.text(source.content),150):source.tags,n.tags+"\n"+NoteBodyParser.bounded(NoteBodyParser.text(n.content),2000));
        if(score<.25)continue;String reason=!source.tags.isEmpty()&&!n.tags.isEmpty()?"标签或内容相近 · "+n.tags:"内容措辞有重合，是否相关由你判断";
        r.add(new Hit(n,NoteBodyParser.bounded(NoteBodyParser.text(n.content),150),reason,score));}
        r.sort(Comparator.comparingDouble((Hit h)->h.score).reversed());return r.subList(0,Math.min(5,r.size()));}
}
