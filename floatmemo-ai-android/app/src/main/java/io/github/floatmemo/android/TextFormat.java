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
    static final int MIN_SIZE = 8, MAX_SIZE = 72;
    private static final Comparator<Range> RANGE_ORDER = Comparator
        .comparing((Range r) -> property(r.kind)).thenComparingInt(r -> r.start);

    private static String property(String kind) {
        return kind.equals("sizeSp") ? "size" : kind;
    }

    private static List<Range> without(List<Range> source, int start, int end, String kind) {
        List<Range> out = new ArrayList<>();
        for (Range range : source) {
            if (!property(range.kind).equals(property(kind)) || range.end <= start || range.start >= end) {
                out.add(range);
                continue;
            }
            if (range.start < start) out.add(new Range(range.start, start, range.kind, range.value));
            if (range.end > end) out.add(new Range(end, range.end, range.kind, range.value));
        }
        return out;
    }

    private static List<Range> merge(List<Range> ranges) {
        ranges.sort(RANGE_ORDER);
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            if (!merged.isEmpty()) {
                int last = merged.size() - 1;
                Range previous = merged.get(last);
                if (previous.kind.equals(range.kind) && previous.value == range.value && previous.end >= range.start) {
                    merged.set(last, new Range(previous.start, Math.max(previous.end, range.end), range.kind, range.value));
                    continue;
                }
            }
            merged.add(range);
        }
        if (merged.size() > MAX_RANGES) throw new IllegalArgumentException("格式片段过多");
        return merged;
    }

    public static List<Range> set(List<Range> source, int start, int end, String kind, double value) {
        if (start >= end) return new ArrayList<>(source);
        List<Range> result = without(source, start, end, kind);
        if (value > 0) result.add(new Range(start, end, kind, value));
        return merge(result);
    }

    static boolean allBold(List<Range> ranges, int start, int end) {
        int covered = start;
        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(r -> r.start));
        for (Range range : sorted) {
            if (!range.kind.equals("bold") || range.end <= covered) continue;
            if (range.start > covered) return false;
            covered = range.end;
            if (covered >= end) return true;
        }
        return false;
    }

    static List<Range> stepSize(Note note, int start, int end, int step) {
        List<Range> sizes = new ArrayList<>();
        for (Range range : note.formats) {
            if (property(range.kind).equals("size") && range.end > start && range.start < end) sizes.add(range);
        }
        sizes.sort(Comparator.comparingInt(r -> r.start));
        List<Range> result = without(note.formats, start, end, "size");
        int cursor = start;
        for (Range range : sizes) {
            int left = Math.max(cursor, range.start);
            int right = Math.min(end, range.end);
            addSizeStep(result, cursor, left, note.fontSize, step);
            int current = range.kind.equals("sizeSp") ? (int) range.value : (int) Math.round(note.fontSize * range.value);
            addSizeStep(result, left, right, current, step);
            cursor = Math.max(cursor, right);
        }
        addSizeStep(result, cursor, end, note.fontSize, step);
        return merge(result);
    }

    private static void addSizeStep(List<Range> out, int start, int end, int current, int step) {
        if (start < end) out.add(new Range(start, end, "sizeSp", Math.max(MIN_SIZE, Math.min(MAX_SIZE, current + step))));
    }

    private static boolean validStyle(Range range) {
        return switch (range.kind) {
            case "bold" -> range.value == 1;
            case "size" -> range.value == 1.25 || range.value == 1.5;
            case "sizeSp" -> range.value >= MIN_SIZE && range.value <= MAX_SIZE && range.value == Math.rint(range.value);
            case "color" -> range.value >= 0xFF000000L && range.value <= 0xFFFFFFFFL && range.value == Math.rint(range.value);
            default -> false;
        };
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
  for(Range r:n.formats)if(r.start<0||r.end>n.content.length()||r.start>=r.end||NoteBodyParser.floorBoundary(n.content,r.start)!=r.start||NoteBodyParser.floorBoundary(n.content,r.end)!=r.end||!validStyle(r))throw new IllegalArgumentException("无效的文字格式范围");
  Set<Integer> starts=new HashSet<>();for(Item i:items(n.content))starts.add(i.start);n.checks.keySet().retainAll(starts);
 }
 public static JSONArray json(List<Range> ranges)throws JSONException{JSONArray a=new JSONArray();for(Range r:ranges)a.put(new JSONObject().put("start",r.start).put("end",r.end).put("kind",r.kind).put("value",r.value));return a;}
 public static List<Range> read(JSONArray a)throws JSONException{List<Range> out=new ArrayList<>();if(a!=null){if(a.length()>MAX_RANGES)throw new IllegalArgumentException("格式过多");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);out.add(new Range(r.getInt("start"),r.getInt("end"),r.getString("kind"),r.getDouble("value")));}}return out;}
}
