package dev.performanceclient.profile;
/** Flat JSON values only, no external parser dependency. Rejects unsupported nested data. */
public final class ProfileJson {
    private final String s; private int i;
    private ProfileJson(String s){this.s=s;}
    public static ProfileData parse(String data){return new ProfileJson(data).parseRoot();}
    private void spaces(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
    private boolean eat(char c){spaces();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
    private void expect(char c){if(!eat(c))throw new IllegalArgumentException("Expected '"+c+"' at "+i);}
    private String quoted(){
        expect('"');StringBuilder out=new StringBuilder();
        while(i<s.length()){
            char c=s.charAt(i++);
            if(c=='"')return out.toString();
            if(c=='\\'){
                if(i>=s.length())break;
                char e=s.charAt(i++);
                switch(e){case '"' -> out.append('"');case '\\' -> out.append('\\');
                    case 'n' -> out.append('\n');case 'r' -> out.append('\r');case 't' -> out.append('\t');
                    default -> throw new IllegalArgumentException("Unsupported JSON escape: "+e);}
            }else if(c<32)throw new IllegalArgumentException("Control character");
            else out.append(c);
        }
        throw new IllegalArgumentException("Unterminated string");
    }
    private String scalar(){
        spaces();if(i<s.length()&&s.charAt(i)=='"')return quoted();
        int start=i;while(i<s.length()&&" ,}\t\r\n".indexOf(s.charAt(i))<0)i++;
        String v=s.substring(start,i);
        if(!v.matches("true|false|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?"))throw new IllegalArgumentException("Only scalar values supported: "+v);
        return v;
    }
    private ProfileData parseRoot(){
        expect('{');ProfileData p=new ProfileData();
        if(!eat('}'))do{String k=quoted();expect(':');p.put(k,scalar());
            if(eat('}'))break;expect(',');
        }while(true);
        spaces();if(i!=s.length())throw new IllegalArgumentException("Trailing data");
        return p;
    }
}
