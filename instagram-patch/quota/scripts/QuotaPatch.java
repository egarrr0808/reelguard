import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.builder.*;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.writer.pool.DexPool;

/** Only adds explicit guards; all original instructions remain in their original order. */
public class QuotaPatch {
    static final String HELPER="Llocal/reelguard/quota/HomeQuota;";
    static ImmutableMethodReference ref(String name,List<String> params,String ret) {
        return new ImmutableMethodReference(HELPER,name,params,ret);
    }
    static BuilderInstruction35c invoke(String name,String ret,int... regs) {
        List<String> params=name.equals("attach")?Arrays.asList("Landroid/content/Context;"):
            Arrays.asList("Ljava/lang/Object;",name.equals("filter")?"Ljava/util/List;":"Ljava/lang/Object;");
        return new BuilderInstruction35c(Opcode.INVOKE_STATIC,regs.length,regs[0],regs.length>1?regs[1]:0,0,0,0,ref(name,params,ret));
    }
    public static void main(String[] args) throws Exception {
        File out=new File(args[1]);out.mkdirs();int writes=0,attach=0,raw=0,rowCaps=0;
        try(ZipFile zip=new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();if(!entry.getName().matches("classes[0-9]*\\.dex"))continue;
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(entry)));
                List<ClassDef> classes=new ArrayList<>();boolean changed=false;
                for(ClassDef cls:dex.getClasses()) {
                    List<Method> methods=new ArrayList<>();boolean classChanged=false;
                    for(Method m:cls.getMethods()) {
                        MethodImplementation impl=m.getImplementation();
                        if(impl==null) {methods.add(m);continue;}
                        MutableMethodImplementation edit=null;
                        List<Instruction> ins=new ArrayList<>();for(Instruction i:impl.getInstructions())ins.add(i);
                        // Work backwards so original instruction indices remain valid.
                        for(int n=ins.size()-1;n>=0;n--) {
                            Instruction i=ins.get(n);
                            if(!(i instanceof ReferenceInstruction))continue;
                            Reference r=((ReferenceInstruction)i).getReference();
                            if(r instanceof FieldReference) {
                                FieldReference f=(FieldReference)r;
                                if(i.getOpcode()==Opcode.IPUT_OBJECT && f.getDefiningClass().equals("LX/07Gg;") && f.getName().equals("A01") && f.getType().equals("Ljava/util/List;")) {
                                    
                                    if(edit==null)edit=new MutableMethodImplementation(impl);
                                    TwoRegisterInstruction regs=(TwoRegisterInstruction)i;
                                    Set<Label> incoming = new HashSet<>(edit.getInstructions().get(n).getLocation().getLabels());
                                    edit.addInstruction(n,invoke("filter","Ljava/util/List;",regs.getRegisterB(),regs.getRegisterA()));
                                    edit.addInstruction(n+1,new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,regs.getRegisterA()));
                                    edit.getInstructions().get(n+2).getLocation().getLabels().removeAll(incoming);
                                    edit.getInstructions().get(n).getLocation().getLabels().addAll(incoming);
                                    writes++;
                                    System.out.println("FILTER "+entry.getName()+" "+m+" index="+n);
                                }
                                if(cls.getType().equals("LX/06qW;") && m.getName().equals("<init>") && i.getOpcode()==Opcode.IPUT_OBJECT && f.getDefiningClass().equals("LX/06qW;") && f.getName().equals("A0H")) {
                                    if(edit==null)edit=new MutableMethodImplementation(impl);
                                    edit.addInstruction(n+1,invoke("attach","V",((TwoRegisterInstruction)i).getRegisterA()));attach++;
                                    System.out.println("ATTACH "+m);
                                }
                            }
                        }
                        if((cls.getType().equals("LX/06qW;") && m.getName().equals("getCount")) ||
                           (cls.getType().equals("LX/07BI;") && m.getName().equals("getItemCount"))) {
                            if(edit!=null)throw new IllegalStateException("Unexpected overlapping edits");
                            edit=new MutableMethodImplementation(impl);
                            int adapter=impl.getRegisterCount()-1;
                            for(int n=ins.size()-1;n>=0;n--)if(ins.get(n).getOpcode()==Opcode.RETURN) {
                                int value=((OneRegisterInstruction)ins.get(n)).getRegisterA();
                                Set<Label> labels=new HashSet<>(edit.getInstructions().get(n).getLocation().getLabels());
                                edit.addInstruction(n,new BuilderInstruction35c(Opcode.INVOKE_STATIC,2,adapter,value,0,0,0,
                                    ref("capRows",Arrays.asList("Ljava/lang/Object;","I"),"I")));
                                edit.addInstruction(n+1,new BuilderInstruction11x(Opcode.MOVE_RESULT,value));
                                edit.getInstructions().get(n+2).getLocation().getLabels().removeAll(labels);
                                edit.getInstructions().get(n).getLocation().getLabels().addAll(labels);
                                rowCaps++;
                            }
                        }
                        if(cls.getType().equals("LX/07Gg;") && m.getName().equals("A02")) {
                            if(!m.getParameterTypes().toString().equals("[LX/07Gg;, LX/01c6;, Ljava/lang/Object;, I, Z]") || impl.getRegisterCount()!=9)throw new IllegalStateException("Raw guard precondition");
                            if(edit==null)edit=new MutableMethodImplementation(impl);
                            Label original=edit.newLabelForIndex(0);
                            edit.addInstruction(0,invoke("allow","Z",4,6));
                            edit.addInstruction(1,new BuilderInstruction11x(Opcode.MOVE_RESULT,0));
                            edit.addInstruction(2,new BuilderInstruction21t(Opcode.IF_NEZ,0,original));
                            edit.addInstruction(3,new BuilderInstruction10x(Opcode.RETURN_VOID));raw++;
                            System.out.println("RAW_GUARD "+m);
                        }
                        if(edit!=null) {
                            methods.add(new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),edit));
                            classChanged=true;
                        } else methods.add(m);
                    }
                    if(classChanged) {
                        classes.add(new ImmutableClassDef(cls.getType(),cls.getAccessFlags(),cls.getSuperclass(),cls.getInterfaces(),cls.getSourceFile(),cls.getAnnotations(),cls.getFields(),methods));changed=true;
                    } else classes.add(cls);
                }
                if(changed) {
                    final List<ClassDef> outputClasses=classes;
                    DexPool.writeTo(new File(out,entry.getName()).getPath(),new DexFile(){public Set<? extends ClassDef> getClasses(){return new LinkedHashSet<ClassDef>(outputClasses);}public Opcodes getOpcodes(){return dex.getOpcodes();}});
                }
            }
        }
        if(writes!=18 || attach!=1 || raw!=1 || rowCaps!=3) throw new IllegalStateException("Unexpected hook counts "+writes+","+attach+","+raw);
        System.out.println("PASS hooks: "+writes+" visible-list writes, 1 Home context, 1 raw insertion guard, 3 native row caps");
    }
}
