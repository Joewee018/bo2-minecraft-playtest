package local.bo2beta;
import java.util.*;
/** Explicit debug candidates; inclusion does not certify native BO2 parity. */
public final class BotCharacters {
    public static final Map<String,String> NAMES=new LinkedHashMap<>();
    static {
        NAMES.put("cdc","BO2 CDC");
        NAMES.put("foxy-of-fortnite-fanmade","Foxy");
        NAMES.put("freddy-fazbear-fortnite","Freddy");
        NAMES.put("male-character-ps1-style","PS1 male");
        NAMES.put("peelyfortnite","Peely");
        NAMES.put("spiderman-brand-new-day-from-fortnite","Spider-Man");
        NAMES.put("mint-swimsuit-animated-neverness-to-everness","Mint");
        NAMES.put("chun-li-fortnite-skin","Chun-Li");
        NAMES.put("fortnite-the-foundation","The Foundation");
        // Public builds work with retail assets alone; user-provided skins are optional.
        NAMES.keySet().removeIf(id->!id.equals("cdc") && (BotCharacters.class.getResource("/assets/bo2beta/bots/custom/"+id+".glb")==null || BotCharacters.class.getResource("/assets/bo2beta/viewmodels/custom/"+id+"-hands.glb")==null));
    }
    public static boolean valid(String id){return NAMES.containsKey(id);}
    public static String resource(String id){return id.equals("cdc")?"bots/cdc-native.glb":"bots/custom/"+id+".glb";}
}
