package io.github.floatmemo.android;
import java.util.*;
import java.util.regex.*;
import org.json.*;
/** Presentation uses raw UTF-16 offsets, without rewriting source. */
public final class TextFormat {
 public static final int MAX_RANGES=1000;
 public static final class Range {public final int start,end;public final String kind;public final double value;public Range(int a,int b,String k,double v){start=a;end=b;kind=k;value=v;}}
 public static final class Item {public final int start,end,textStart;public final boolean checked;Item(int a,int b,int t,boolean c){start=a;end=b;textStart=t;checked=c;}}
 private static final Pattern CHECK=Pattern.compile("(?m)^[ \\t]*- \\[([ xX])\\] ");
 private TextFormat(){}
 public static List<Item> items(String text){List<Item> out=new ArrayList<>();int at=0;boolean code=false;for(String line:text.split("\n",-1)){if(line.trim().startsWith("```"))code=!code;else if(!code){Matcher m=CHECK.matcher(line);if(m.find())out.add(new Item(at,at+line.length(),at+m.end(),!m.group(1).equals(" ")));}at+=line.length()+1;}return out;}
 public static boolean checked(Note n,Item i){return n.checks.containsKey(i.start)?n.checks.get(i.start):i.checked;}
 public static List<Range> set(List<Range> source,int a,int b,String kind,double value){
  List<Range> out=new ArrayList<>();if(a>=b)return new ArrayList<>(source);
  for(Range r:source){if(!r.kind.equals(kind)||r.end<=a||r.start>=b){out.add(r);continue;}if(r.start<a)out.add(new Range(r.start,a,r.kind,r.value));if(r.end>b)out.add(new Range(b,r.end,r.kind,r.value));}
  if(value>0)out.add(new Range(a,b,kind,value));out.sort(Comparator.comparing((Range r)->r.kind).thenComparingDouble(r->r.value).thenComparingInt(r->r.start));List<Range> merged=new ArrayList<>();
  for(Range r:out){if(!merged.isEmpty()){Range p=merged.get(merged.size()-1);if(p.kind.equals(r.kind)&&p.value==r.value&&p.end>=r.start){merged.set(merged.size()-1,new Range(p.start,Math.max(p.end,r.end),p.kind,p.value));continue;}}merged.add(r);}
  if(merged.size()>MAX_RANGES)throw new IllegalArgumentException("格式片段过多");return merged;
 }
 public static void rebase(Note n,String before){
  String after=n.content;if(before.equals(after))return;int a=0;while(a<before.length()&&a<after.length()&&before.charAt(a)==after.charAt(a))a++;a=NoteBodyParser.floorBoundary(before,a);a=NoteBodyParser.floorBoundary(after,a);
  int oldEnd=before.length(),newEnd=after.length();while(oldEnd>a&&newEnd>a&&before.charAt(oldEnd-1)==after.charAt(newEnd-1)){oldEnd--;newEnd--;}oldEnd=ceil(before,oldEnd);newEnd=ceil(after,newEnd);int delta=newEnd-oldEnd;
  List<Range> ranges=new ArrayList<>();for(Range r:n.formats){int left=NoteBodyParser.floorBoundary(after,map(r.start,a,oldEnd,newEnd,delta,true)),right=NoteBodyParser.floorBoundary(after,map(r.end,a,oldEnd,newEnd,delta,false));if(left<right)ranges.add(new Range(left,right,r.kind,r.value));}
  Map<Integer,Boolean> checks=new LinkedHashMap<>();for(Item item:items(before))if(n.checks.containsKey(item.start)){int at=-1;if(oldEnd<=item.start)at=item.start+delta;else if(a>=item.end)at=item.start;if(at>=0&&at+item.end-item.start<=after.length()&&after.regionMatches(at,before,item.start,item.end-item.start)){int end=after.indexOf('\n',at);if((end<0?after.length():end)==at+item.end-item.start)checks.put(at,n.checks.get(item.start));}}
  n.formats=ranges;n.checks=checks;validate(n);
 }
 private static int ceil(String s,int at){int floor=NoteBodyParser.floorBoundary(s,at);return floor<at?Math.min(s.length(),at+1):at;}
 private static int map(int p,int a,int oldEnd,int newEnd,int delta,boolean start){if(oldEnd==a)return (start?p>=a:p>a)?p+delta:p;if(p<=a)return p;if(p>=oldEnd)return p+delta;return start?a:newEnd;}
 public static void validate(Note n){
  if(n.formats.size()>MAX_RANGES||n.checks.size()>MAX_RANGES)throw new IllegalArgumentException("格式或清单项目超过上限");
  for(Range r:n.formats)if(r.start<0||r.end>n.content.length()||r.start>=r.end||NoteBodyParser.floorBoundary(n.content,r.start)!=r.start||NoteBodyParser.floorBoundary(n.content,r.end)!=r.end||!(r.kind.equals("bold")&&r.value==1||r.kind.equals("size")&&(r.value==1.25||r.value==1.5)))throw new IllegalArgumentException("无效的文字格式范围");
  Set<Integer> starts=new HashSet<>();for(Item i:items(n.content))starts.add(i.start);n.checks.keySet().retainAll(starts);
 }
 public static JSONArray json(List<Range> ranges)throws JSONException{JSONArray a=new JSONArray();for(Range r:ranges)a.put(new JSONObject().put("start",r.start).put("end",r.end).put("kind",r.kind).put("value",r.value));return a;}
 public static List<Range> read(JSONArray a)throws JSONException{List<Range> out=new ArrayList<>();if(a!=null){if(a.length()>MAX_RANGES)throw new IllegalArgumentException("格式过多");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);out.add(new Range(r.getInt("start"),r.getInt("end"),r.getString("kind"),r.getDouble("value")));}}return out;}
}
