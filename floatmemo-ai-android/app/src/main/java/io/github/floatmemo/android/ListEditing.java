package io.github.floatmemo.android;
import java.util.regex.*;
public final class ListEditing {
 public static final class Edit {public final int start,end;public final String text;Edit(int s,int e,String t){start=s;end=e;text=t;}}
 private static final Pattern PREFIX=Pattern.compile("^([ \\t]*)(?:(- \\[[ xX]\\] )|(\\d{1,6})[.] |(- ))(.*)$");
 private ListEditing(){}
 public static String prefixLines(String selected,String prefix){String[] lines=selected.split("\n",-1);StringBuilder result=new StringBuilder();for(int i=0;i<lines.length;i++){if(i>0)result.append('\n');result.append(prefix.equals("1. ")?(i+1)+". ":prefix).append(lines[i]);}return result.toString();}
 public static Edit newline(String text,int at){if(at<0||at>=text.length()||text.charAt(at)!='\n')return null;int start=text.lastIndexOf('\n',at-1)+1;Matcher m=PREFIX.matcher(text.substring(start,at));if(!m.matches())return null;if(m.group(5).trim().isEmpty())return new Edit(start,at+1,"");String next=m.group(2)!=null?"- [ ] ":m.group(3)!=null?(Integer.parseInt(m.group(3))+1)+". ":"- ";return new Edit(at+1,at+1,m.group(1)+next);}
}
