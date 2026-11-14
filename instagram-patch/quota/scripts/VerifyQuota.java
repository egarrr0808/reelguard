import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.dexbacked.*;

/** Independently strips and validates hooks, then compares every original instruction semantically. */
public class VerifyQuota {
    static String helper(Instruction i) {
        if(i instanceof ReferenceInstruction) {
            Reference r=((ReferenceInstruction)i).getReference();
            if(r instanceof MethodReference && ((MethodReference)r).getDefiningClass().equals("Llocal/reelguard/quota/HomeQuota;"))return ((MethodReference)r).getName();
        }return "";
    }
    static int filters,attaches,guards,methods,rowCaps;
    static void require(boolean ok,String msg) {if(!ok)throw new IllegalStateException(msg);}
    static List<String> normalized(Method m,boolean patched) {
        MethodImplementation code=m.getImplementation();if(code==null)return null;
        List<Instruction> all=new ArrayList<>();List<Integer> addr=new ArrayList<>();int pc=0;
        for(Instruction i:code.getInstructions()){all.add(i);addr.add(pc);pc+=i.getCodeUnits();}
        addr.add(pc);Set<Integer> skip=new HashSet<>();Set<Integer> forbiddenTargets=new HashSet<>();
        if(patched)for(int n=0;n<all.size();n++) {
            Instruction i=all.get(n);String h=helper(i);if(h.isEmpty())continue;
            require(i.getOpcode()==Opcode.INVOKE_STATIC,"helper opcode");
            FiveRegisterInstruction r=(FiveRegisterInstruction)i;
            if(h.equals("filter")) {
                require(n+2<all.size(),"filter bounds");
                Instruction move=all.get(n+1),put=all.get(n+2);
                require(move.getOpcode()==Opcode.MOVE_RESULT_OBJECT && put.getOpcode()==Opcode.IPUT_OBJECT,"filter sequence");
                TwoRegisterInstruction p=(TwoRegisterInstruction)put;
                FieldReference f=(FieldReference)((ReferenceInstruction)put).getReference();
                require(f.toString().equals("LX/07Gg;->A01:Ljava/util/List;") && r.getRegisterCount()==2 && r.getRegisterC()==p.getRegisterB() && r.getRegisterD()==p.getRegisterA() && ((OneRegisterInstruction)move).getRegisterA()==p.getRegisterA(),"filter operands");
                skip.add(n);skip.add(n+1);forbiddenTargets.add(addr.get(n+1));forbiddenTargets.add(addr.get(n+2));filters++;
            }else if(h.equals("capRows")) {
                require((m.getDefiningClass().equals("LX/06qW;") && m.getName().equals("getCount")) ||
                    (m.getDefiningClass().equals("LX/07BI;") && m.getName().equals("getItemCount")),"row cap location");
                require(all.get(n+1).getOpcode()==Opcode.MOVE_RESULT && all.get(n+2).getOpcode()==Opcode.RETURN &&
                    r.getRegisterCount()==2 && r.getRegisterC()==code.getRegisterCount()-1 &&
                    r.getRegisterD()==((OneRegisterInstruction)all.get(n+2)).getRegisterA() &&
                    r.getRegisterD()==((OneRegisterInstruction)all.get(n+1)).getRegisterA(),"row cap operands");
                skip.add(n);skip.add(n+1);rowCaps++;
            }else if(h.equals("attach")) {
                require(m.getDefiningClass().equals("LX/06qW;") && m.getName().equals("<init>") && n>0,"attach location");
                Instruction prev=all.get(n-1);
                require(prev.getOpcode()==Opcode.IPUT_OBJECT && ((FieldReference)((ReferenceInstruction)prev).getReference()).toString().equals("LX/06qW;->A0H:Landroid/content/Context;") && r.getRegisterCount()==1 && r.getRegisterC()==((TwoRegisterInstruction)prev).getRegisterA(),"attach context");
                skip.add(n);attaches++;
            }else if(h.equals("allow")) {
                require(n==0 && m.toString().equals("LX/07Gg;->A02(LX/07Gg;LX/01c6;Ljava/lang/Object;IZ)V") && r.getRegisterCount()==2 && r.getRegisterC()==4 && r.getRegisterD()==6,"guard location");
                require(all.get(1).getOpcode()==Opcode.MOVE_RESULT && ((OneRegisterInstruction)all.get(1)).getRegisterA()==0 && all.get(2).getOpcode()==Opcode.IF_NEZ && ((OneRegisterInstruction)all.get(2)).getRegisterA()==0 && addr.get(2)+((OffsetInstruction)all.get(2)).getCodeOffset()==addr.get(4) && all.get(3).getOpcode()==Opcode.RETURN_VOID,"guard branch");
                for(int k=0;k<4;k++)skip.add(k);guards++;
            }else throw new IllegalStateException("Unexpected helper");
        }
        for(int n=0;n<all.size();n++)if(all.get(n) instanceof OffsetInstruction)
            require(!forbiddenTargets.contains(addr.get(n)+((OffsetInstruction)all.get(n)).getCodeOffset()),"branch bypasses filter");
        Map<Integer,Integer> logical=new HashMap<>();int ordinal=0;
        for(int n=0;n<all.size();n++){logical.put(addr.get(n),ordinal);if(!skip.contains(n))ordinal++;}logical.put(pc,ordinal);
        Map<Integer,Integer> switchBase=new HashMap<>();
        for(int n=0;n<all.size();n++)if(all.get(n).getOpcode()==Opcode.PACKED_SWITCH || all.get(n).getOpcode()==Opcode.SPARSE_SWITCH)switchBase.put(addr.get(n)+((OffsetInstruction)all.get(n)).getCodeOffset(),addr.get(n));
        List<String> result=new ArrayList<>();result.add("registers="+code.getRegisterCount());
        for(int n=0;n<all.size();n++) {
            if(skip.contains(n))continue;Instruction i=all.get(n);
            StringBuilder s=new StringBuilder(i.getOpcode()==Opcode.CONST_STRING_JUMBO?"CONST_STRING":i.getOpcode().toString());
            if(i instanceof OneRegisterInstruction)s.append(" A=").append(((OneRegisterInstruction)i).getRegisterA());
            if(i instanceof TwoRegisterInstruction)s.append(" B=").append(((TwoRegisterInstruction)i).getRegisterB());
            if(i instanceof ThreeRegisterInstruction)s.append(" C=").append(((ThreeRegisterInstruction)i).getRegisterC());
            if(i instanceof FiveRegisterInstruction){FiveRegisterInstruction r=(FiveRegisterInstruction)i;s.append(" regs=").append(Arrays.asList(r.getRegisterCount(),r.getRegisterC(),r.getRegisterD(),r.getRegisterE(),r.getRegisterF(),r.getRegisterG()));}
            if(i instanceof RegisterRangeInstruction){RegisterRangeInstruction r=(RegisterRangeInstruction)i;s.append(" range=").append(r.getStartRegister()).append(',').append(r.getRegisterCount());}
            if(i instanceof WideLiteralInstruction)s.append(" literal=").append(((WideLiteralInstruction)i).getWideLiteral());
            if(i instanceof ReferenceInstruction)s.append(" ref=").append(((ReferenceInstruction)i).getReference());
            if(i instanceof DualReferenceInstruction)s.append(" ref2=").append(((DualReferenceInstruction)i).getReference2());
            if(i instanceof OffsetInstruction)s.append(" target=").append(logical.get(addr.get(n)+((OffsetInstruction)i).getCodeOffset()));
            if(i instanceof SwitchPayload)for(SwitchElement e:((SwitchPayload)i).getSwitchElements())s.append(" switch=").append(e.getKey()).append(':').append(logical.get(switchBase.get(addr.get(n))+e.getOffset()));
            if(i instanceof ArrayPayload)s.append(" array=").append(((ArrayPayload)i).getElementWidth()).append(((ArrayPayload)i).getArrayElements());
            result.add(s.toString());
        }
        // DexPool merges adjacent try ranges. Compare effective handlers at each logical instruction.
        Map<Integer,LinkedHashSet<String>> handlers=new TreeMap<>();
        for(TryBlock<? extends ExceptionHandler> t:code.getTryBlocks()) {
            int start=logical.get(t.getStartCodeAddress()),end=logical.get(t.getStartCodeAddress()+t.getCodeUnitCount());
            for(int k=start;k<end;k++)for(ExceptionHandler h:t.getExceptionHandlers()) {
                LinkedHashSet<String> hs=handlers.computeIfAbsent(k,key->new LinkedHashSet<>());
                String type=h.getExceptionType()+":";
                boolean shadowed=false;
                for(String old:hs)if(old.startsWith(type) || old.startsWith("null:"))shadowed=true;
                if(!shadowed)hs.add(type+logical.get(h.getHandlerCodeAddress()));
            }
        }
        for(Map.Entry<Integer,LinkedHashSet<String>> e:handlers.entrySet())result.add("handlers "+e.getKey()+" "+e.getValue());
        return result;
    }
    public static void main(String[] args)throws Exception {
        try(ZipFile before=new ZipFile(args[0]);ZipFile after=new ZipFile(args[1])) {
            Enumeration<? extends ZipEntry> entries=before.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;
                byte[] original=before.getInputStream(e).readAllBytes(),patched=after.getInputStream(after.getEntry(e.getName())).readAllBytes();
                if(Arrays.equals(original,patched))continue;
                DexBackedDexFile a=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new ByteArrayInputStream(original));
                DexBackedDexFile b=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new ByteArrayInputStream(patched));
                Map<String,ClassDef> classes=new HashMap<>();for(ClassDef c:b.getClasses())classes.put(c.getType(),c);
                require(a.getClasses().size()==classes.size(),"class count");
                for(ClassDef c:a.getClasses()) {
                    ClassDef d=classes.get(c.getType());require(d!=null,"missing class");
                    require(c.getAccessFlags()==d.getAccessFlags() && Objects.equals(c.getSuperclass(),d.getSuperclass()) && c.getInterfaces().equals(d.getInterfaces()) && c.getAnnotations().equals(d.getAnnotations()),"class metadata "+c);
                    Map<String,Method> ms=new HashMap<>();for(Method m:d.getMethods())ms.put(m.toString(),m);
                    int count=0;for(Method m:c.getMethods()) {
                        Method next=ms.get(m.toString());require(next!=null,"missing method");
                        require(m.getAccessFlags()==next.getAccessFlags() && m.getAnnotations().equals(next.getAnnotations()) && m.getParameters().equals(next.getParameters()),"method metadata");
                        List<String> x=normalized(m,false),y=normalized(next,true);
                        if(!Objects.equals(x,y)) {
                            for(int k=0;k<Math.min(x.size(),y.size());k++)if(!x.get(k).equals(y.get(k)))throw new IllegalStateException("Instruction mismatch "+m+" at "+k+"\n"+x.get(k)+"\n"+y.get(k));
                            throw new IllegalStateException("Instruction length mismatch "+m);
                        }count++;methods++;
                    }require(count==ms.size(),"extra methods");
                    List<String> fs=new ArrayList<>(),gs=new ArrayList<>();
                    for(Field f:c.getFields())fs.add(f+" "+f.getAccessFlags()+" "+f.getInitialValue()+" "+f.getAnnotations());
                    for(Field f:d.getFields())gs.add(f+" "+f.getAccessFlags()+" "+f.getInitialValue()+" "+f.getAnnotations());
                    require(fs.equals(gs),"field metadata "+c);
                }
            }
        }
        require(filters==18 && attaches==1 && guards==1 && rowCaps==3,"missing or extra hooks");
        System.out.println("PASS "+methods+" original methods preserved; 18 filters, attach, raw guard, 3 row caps verified");
    }
}
