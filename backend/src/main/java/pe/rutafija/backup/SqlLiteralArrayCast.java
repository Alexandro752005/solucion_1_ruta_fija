package pe.rutafija.backup;
import java.util.regex.*;
/** Exact reversible spelling change only for literal unbounded-varchar arrays cast to text[]. */
public final class SqlLiteralArrayCast {
 private static final Pattern ARRAY=Pattern.compile("ARRAY\\[([^\\[\\]]*)\\]");
 private static final Pattern ITEM=Pattern.compile("\\s*\\(?('(?:[^']|'')*')::character varying\\)?::text\\s*");
 private SqlLiteralArrayCast(){}
 public static String sourceSpelling(String definition,boolean index){
  var arrays=ARRAY.matcher(definition);var out=new StringBuilder();
  while(arrays.find()){
   String body=arrays.group(1);int offset=0;var elements=new java.util.ArrayList<String>();boolean complete=true;
   while(offset<body.length()){
    var item=ITEM.matcher(body);item.region(offset,body.length());if(!item.lookingAt()){complete=false;break;}
    elements.add(item.group(1)+"::character varying");offset=item.end();
    if(offset==body.length())break;if(body.charAt(offset)!=','){complete=false;break;}offset++;
   }
   if(!complete||elements.isEmpty())continue;
   String replacement="ARRAY["+String.join(", ",elements)+"]";
   replacement=(index?"("+replacement+")":replacement)+"::text[]";
   arrays.appendReplacement(out,Matcher.quoteReplacement(replacement));
  }
  arrays.appendTail(out);return out.toString();
 }
}
