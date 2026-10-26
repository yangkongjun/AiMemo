package io.github.floatmemo.android;

import java.util.*;
import org.json.*;

/** Pure local selection. No remote call is needed to choose or locate excerpts. */
public final class SearchPlanner {
    public static final class Window {
        public final int start,end;public final String text;
        Window(String raw,int start,int end){this.start=start;this.end=end;text=raw.substring(start,end);}
    }
    public static final class Entry {
        public final Note note;public final List<Window> windows;public final boolean fallback;
        Entry(Note n,List<Window> windows,boolean fallback){note=n.copy();this.windows=Collections.unmodifiableList(new ArrayList<>(windows));this.fallback=fallback;}
    }
    public static final class Plan {
        public final List<Entry> entries;public final String query,scope;public final int bodyChars,scopeCount;public final long corpusVersion;
        Plan(List<Entry> e,String q,String scope,int chars,int count,long version){entries=Collections.unmodifiableList(e);query=q;this.scope=scope;bodyChars=chars;scopeCount=count;corpusVersion=version;}
        public JSONObject payload()throws JSONException{
            JSONArray a=new JSONArray();for(Entry e:entries){JSONArray windows=new JSONArray();for(Window w:e.windows)windows.put(new JSONObject().put("start",w.start).put("end",w.end).put("text",w.text));
                a.put(new JSONObject().put("id",e.note.id).put("revision",e.note.revision).put("title",NoteBodyParser.bounded(e.note.title,120))
                    .put("tags",NoteBodyParser.bounded(e.note.tags,240)).put("topic",NoteBodyParser.bounded(e.note.folder,60)).put("fallback",e.fallback).put("windows",windows));}
            return new JSONObject().put("query",query).put("notes",a);
        }
        public List<SearchEngine.Hit> results(String answer)throws Exception{
            String response=answer.trim();if(response.startsWith("```")){int a=response.indexOf('\n'),b=response.lastIndexOf("```");if(a>=0&&b>a)response=response.substring(a+1,b);}
            JSONArray a=new JSONObject(response).getJSONArray("matches");if(a.length()>10)throw new Exception("搜索返回条目过多");
            Map<String,Entry> map=new HashMap<>();for(Entry e:entries)map.put(e.note.id,e);
            List<SearchEngine.Hit> out=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);String id=item.getString("id"),quote=item.getString("quote"),reason=item.getString("reason");Entry e=map.get(id);int start=-1;
                if(e!=null&&!quote.trim().isEmpty())for(Window w:e.windows){int at=w.text.indexOf(quote);if(at>=0){start=w.start+at;break;}}
                if(start<0||reason.length()>800)throw new Exception("搜索来源不在发送的同一片段内");
                if(seen.add(id))out.add(new SearchEngine.Hit(e.note,quote,reason,10-i,start));
            }return out;
        }
    }
    private static final class Indexed {
        final Note note;final String body,title,tags,topic;
        Indexed(Note n){note=n.copy();body=normalized(NoteBodyParser.text(n.content));title=normalized(n.title);tags=normalized(n.tags);topic=normalized(n.folder);}
        double score(Set<String> q,String query){return overlap(q,body)+2*overlap(q,title)+1.5*overlap(q,tags)+overlap(q,topic)
            +(indexOf(note.title,query,0)>=0?5:0)+(indexOf(note.tags,query,0)>=0?3:0);}
    }
    /** Reused until repository text/title/topic/tag content version changes. */
    public static final class Index {
        private final List<Indexed> notes=new ArrayList<>();public final long version;
        public Index(List<Note> all,long version){for(Note n:all)notes.add(new Indexed(n));this.version=version;}
        public Plan plan(String query,String scope,RequestOptions options)throws Exception{
            String q=normalize(query);if(q.isEmpty())throw new IllegalArgumentException("先输入想找的内容");if(q.length()>256)throw new IllegalArgumentException("搜索线索最多256字符，请缩短后再试");
            List<Indexed> selected=new ArrayList<>();for(Indexed n:notes)if(inScope(n.note,scope)&&!NoteBodyParser.text(n.note.content).trim().isEmpty()&&n.note.id.length()<=200)selected.add(n);
            int count=selected.size();Set<String> grams=grams(q);selected.sort(Comparator.comparingDouble((Indexed i)->i.score(grams,q)).reversed());
            if(selected.size()>options.candidates)selected=new ArrayList<>(selected.subList(0,options.candidates));
            List<Entry> preliminary=new ArrayList<>();for(Indexed n:selected)preliminary.add(windows(n.note,q));
            // First pass gives every high-ranked candidate a base excerpt before adding context.
            List<List<Window>> allocations=new ArrayList<>();int remaining=options.searchChars;
            int base=preliminary.isEmpty()?0:Math.min(600,remaining/preliminary.size());
            for(Entry e:preliminary){List<Window> a=new ArrayList<>();Window first=e.windows.get(0);Window w=cut(e.note.content,first,Math.min(base,first.text.length()),q);
                if(!w.text.isEmpty()){a.add(w);remaining-=w.text.length();}allocations.add(a);}
            for(int i=0;i<preliminary.size()&&remaining>0;i++){
                Entry e=preliminary.get(i);List<Window> a=allocations.get(i);int size=length(a);
                // Expand the base window around the same hit, then add other independent hits.
                Window full=e.windows.get(0);if(!a.isEmpty()&&full.text.length()>a.get(0).text.length()){
                    Window w=cut(e.note.content,full,Math.min(full.text.length(),a.get(0).text.length()+remaining),q);int extra=w.text.length()-a.get(0).text.length();a.set(0,w);remaining-=extra;size+=extra;}
                for(int j=1;j<e.windows.size()&&remaining>0&&size<1200;j++){Window w=cut(e.note.content,e.windows.get(j),Math.min(remaining,1200-size),q);if(!w.text.isEmpty()){a.add(w);remaining-=w.text.length();size+=w.text.length();}}
            }
            List<Entry> entries=new ArrayList<>();for(int i=0;i<preliminary.size();i++)if(!allocations.get(i).isEmpty())entries.add(new Entry(preliminary.get(i).note,allocations.get(i),preliminary.get(i).fallback));
            Plan plan=new Plan(entries,q,scope,lengthEntries(entries),count,version);
            int wireLimit=options.searchChars+options.candidates*1000+4096;
            // JSON escaping and metadata also consume input. Preserve the body cap and cap serialized input.
            while(!entries.isEmpty()&&plan.payload().toString().length()>wireLimit){
                int i=entries.size()-1;Entry e=entries.get(i);List<Window> w=new ArrayList<>(e.windows);
                Window last=w.remove(w.size()-1);if(last.text.length()>100)w.add(cut(e.note.content,last,last.text.length()-100,q));
                if(w.isEmpty())entries.remove(i);else entries.set(i,new Entry(e.note,w,e.fallback));plan=new Plan(entries,q,scope,lengthEntries(entries),count,version);
            }return plan;
        }
    }
    public static String normalize(String q){return java.text.Normalizer.normalize(q.trim().replaceAll("\\s+"," "),java.text.Normalizer.Form.NFC);}
    public static boolean inScope(Note n,String scope){return scope.equals("all")||(scope.equals("unfiled")&&n.folder.isEmpty())||(scope.startsWith("folder:")&&n.folder.equals(scope.substring(7)));}
    private static int length(List<Window> w){int size=0;for(Window x:w)size+=x.text.length();return size;}
    private static int lengthEntries(List<Entry> e){int size=0;for(Entry x:e)size+=length(x.windows);return size;}
    private static Set<String> grams(String value){int[] a=value.toLowerCase(Locale.ROOT).replaceAll("\\s+","").codePoints().toArray();Set<String> s=new HashSet<>();for(int i=0;i<a.length;i++){s.add(new String(a,i,1));if(i+1<a.length)s.add(new String(a,i,2));}return s;}
    private static String normalized(String text){return text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    private static double overlap(Set<String> a,String b){if(a.isEmpty())return 0;int n=0;for(String s:a)if(b.contains(s))n++;return (double)n/a.size();}
    static int indexOf(String text,String q,int start){if(q.isEmpty())return -1;for(int i=Math.max(0,start);i+q.length()<=text.length();i++)if(text.regionMatches(true,i,q,0,q.length()))return i;return -1;}
    private static List<String> terms(String query){
        LinkedHashSet<String> terms=new LinkedHashSet<>();terms.add(query);for(String t:query.split("[\\s，,。！？?!、]+"))if(!t.isEmpty())terms.add(t);
        int[] cp=query.codePoints().toArray();for(int i=0;i+1<cp.length&&terms.size()<40;i++)terms.add(new String(cp,i,2));return new ArrayList<>(terms);
    }
    static Entry windows(Note n,String query){
        List<Window> found=new ArrayList<>();List<NoteBodyParser.Part> parts=NoteBodyParser.parts(n.content);List<String> terms=terms(query);
        // Full phrases have priority; later partial matches only fill remaining windows.
        for(String term:terms){for(NoteBodyParser.Part p:parts){if(!p.imageId.isEmpty())continue;int from=0,hits=0,at;
                while((at=indexOf(p.text,term,from))>=0&&hits++<8){int pos=p.start+at;int start=Math.max(p.start,pos-300),end=Math.min(p.end,pos+term.length()+300);
                    start=NoteBodyParser.floorBoundary(n.content,start);end=NoteBodyParser.floorBoundary(n.content,end);
                    // Prefer nearby sentence/paragraph boundaries without losing the actual hit.
                    for(int j=start;j<Math.min(pos,start+70);j++)if("\n。！？!?".indexOf(n.content.charAt(j))>=0){start=j+1;break;}
                    for(int j=end-1;j>Math.max(pos+term.length(),end-70);j--)if("\n。！？!?".indexOf(n.content.charAt(j))>=0){end=j+1;break;}
                    Window w=new Window(n.content,start,end);boolean merged=false;
                    for(int i=0;i<found.size();i++){Window old=found.get(i);if(w.start<=old.end&&w.end>=old.start){int a=Math.min(old.start,w.start),b=Math.max(old.end,w.end);if(b-a<=1200)found.set(i,new Window(n.content,a,b));merged=true;break;}}
                    if(!merged&&found.size()<3)found.add(w);from=at+Math.max(1,term.length());if(found.size()==3)break;
                }if(found.size()==3)break;}if(found.size()==3)break;}
        boolean fallback=found.isEmpty();if(fallback)for(NoteBodyParser.Part p:parts)if(p.imageId.isEmpty()&&!p.text.trim().isEmpty()){found.add(new Window(n.content,p.start,NoteBodyParser.floorBoundary(n.content,Math.min(p.end,p.start+600))));break;}
        // Merge overlapping windows again after extension; never join across image markers.
        found.sort(Comparator.comparingInt(w->w.start));List<Window> merged=new ArrayList<>();for(Window w:found){if(!merged.isEmpty()&&merged.get(merged.size()-1).end>=w.start){Window prev=merged.remove(merged.size()-1);merged.add(new Window(n.content,prev.start,Math.max(prev.end,w.end)));}else merged.add(w);}
        // Under a tight global budget, keep the full remembered phrase ahead of earlier partial hits.
        merged.sort(Comparator.comparingInt((Window w)->indexOf(w.text,query,0)>=0?0:1).thenComparingInt(w->w.start));
        List<Window> capped=new ArrayList<>();int remaining=1200;for(Window w:merged){if(remaining<=0)break;Window cut=cut(n.content,w,Math.min(remaining,w.text.length()),query);capped.add(cut);remaining-=cut.text.length();}
        return new Entry(n,capped,fallback);
    }
    private static Window cut(String raw,Window w,int max,String query){
        if(max>=w.text.length())return w;if(max<=0)return new Window(raw,w.start,w.start);
        int hit=indexOf(w.text,query,0);if(hit<0)for(String term:terms(query)){hit=indexOf(w.text,term,0);if(hit>=0)break;}
        int start=w.start+(hit<0?0:Math.max(0,hit-max/2));start=Math.min(start,Math.max(w.start,w.end-max));start=NoteBodyParser.floorBoundary(raw,start);
        return new Window(raw,start,NoteBodyParser.floorBoundary(raw,Math.min(w.end,start+max)));
    }
}
