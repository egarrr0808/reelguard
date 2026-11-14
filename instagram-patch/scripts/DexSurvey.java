import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

public class DexSurvey {
    static final String PAGER="Lcom/instagram/common/ui/widget/reboundviewpager/ReboundViewPager;";
    public static void main(String[] args) throws Exception {
        try (ZipFile zip=new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(entry)));
                for (ClassDef cls:dex.getClasses()) {
                    for (Field field:cls.getStaticFields()) {
                        if (field.getName().equals("__redex_internal_original_name") && field.getInitialValue() instanceof org.jf.dexlib2.iface.value.StringEncodedValue) {
                            String n=((org.jf.dexlib2.iface.value.StringEncodedValue)field.getInitialValue()).getValue();
                            if(n.toLowerCase().contains("clip") || n.toLowerCase().contains("maintab") || n.toLowerCase().contains("tabbar") || n.toLowerCase().contains("feed")) emit(entry,cls,null,"ORIGINAL_NAME",n);
                        }
                    }
                    if (PAGER.equals(cls.getSuperclass())) emit(entry,cls,null,"SUBCLASS",cls.getSuperclass());
                    for (Method method:cls.getMethods()) {
                        MethodImplementation code=method.getImplementation();if(code==null)continue;
                        Set<String> hits=new LinkedHashSet<>();
                        for(Instruction ins:code.getInstructions()) {
                            if(ins instanceof WideLiteralInstruction) {
                                long value=((WideLiteralInstruction)ins).getWideLiteral();
                                if(value==0x7f0b0ca3L || value==0x7f0b0c4eL) hits.add("RESOURCE\t"+Long.toHexString(value));
                            }
                            if(ins instanceof ReferenceInstruction) {
                                Reference ref=((ReferenceInstruction)ins).getReference();
                                if(ref instanceof FieldReference) {
                                    FieldReference f=(FieldReference)ref;
                                    if(f.getDefiningClass().equals("LX/03am;") && f.getName().equals("A08")) hits.add("CLIPS_TAB_REF\t"+f);
                                }
                                if(ref instanceof MethodReference && PAGER.equals(((MethodReference)ref).getDefiningClass())) hits.add("PAGER_CALL\t"+ref);
                                if(ref instanceof StringReference) {
                                    String value=((StringReference)ref).getString();
                                    if(value.equals("feed_timeline") && method.getName().equals("getModuleName") || value.equals("ClipsViewerFragment") || value.equals("clips_viewer_view_pager") || value.contains("autoscroll") && cls.getType().contains("clips")) hits.add("STRING\t"+value.substring(0,Math.min(160,value.length())).replace('\n',' '));
                                }
                            }
                        }
                        for(String hit:hits) emit(entry,cls,method,"HIT",hit);
                    }
                }
            }
        }
    }
    static void emit(ZipEntry e,ClassDef c,Method m,String kind,String value) {
        System.out.println(e.getName()+"\t"+c.getType()+"\t"+(m==null?"":m.getName()+m.getParameterTypes()+m.getReturnType())+"\t"+kind+"\t"+value);
    }
}
