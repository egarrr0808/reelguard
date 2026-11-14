import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.security.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.dexbacked.instruction.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

/** Emits exact byte replacements for this reviewed build; does not write APKs. */
public class NativePatchPlan {
 static byte[] bytes; static String dexName;
 static String hex(byte[] b){return HexFormat.of().formatHex(b);}
 static void emit(Method m,Instruction ins,byte[] replacement,String reason) throws Exception {
  int offset=((DexBackedInstruction)ins).instructionStart;
  emitAt(m,offset,replacement,reason);
 }
 static void emitAt(Method m,int offset,byte[] replacement,String reason) throws Exception {
  byte[] before=Arrays.copyOfRange(bytes,offset,offset+replacement.length);
  if(Arrays.equals(before,replacement))throw new IllegalStateException("Already patched: "+m);
  MethodImplementation code=m.getImplementation();
  List<Instruction> list=new ArrayList<>();code.getInstructions().forEach(list::add);
  int start=((DexBackedInstruction)list.get(0)).instructionStart;
  int length=list.stream().mapToInt(i->i.getCodeUnits()*2).sum();
  String sha=hex(MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(bytes,start,start+length)));
  System.out.println(String.join("\t",dexName,m.toString(),Integer.toString(offset),hex(before),hex(replacement),sha,reason));
 }
 static List<Instruction> instructions(Method m){List<Instruction> a=new ArrayList<>();m.getImplementation().getInstructions().forEach(a::add);return a;}
 static FieldReference field(Instruction i){return i instanceof ReferenceInstruction && ((ReferenceInstruction)i).getReference() instanceof FieldReference ? (FieldReference)((ReferenceInstruction)i).getReference():null;}
 static int fieldIndex(DexBackedDexFile d,String name){
  for(int i=0;i<d.getFieldSection().size();i++){FieldReference f=d.getFieldSection().get(i);if(f.getDefiningClass().equals("LX/03am;")&&f.getName().equals(name))return i;}
  throw new IllegalStateException("Field missing "+name);
 }
 static byte[] fieldReplacement(Instruction i,int index){
  int p=((DexBackedInstruction)i).instructionStart;
  byte[] b=Arrays.copyOfRange(bytes,p,p+4);b[2]=(byte)index;b[3]=(byte)(index>>8);return b;
 }
 public static void main(String[] args)throws Exception{
  int hits=0;
  try(ZipFile z=new ZipFile(args[0])){
   for(String n:List.of("classes.dex","classes2.dex","classes5.dex")){
    dexName=n;bytes=z.getInputStream(z.getEntry(n)).readAllBytes();
    DexBackedDexFile d=new DexBackedDexFile(Opcodes.getDefault(),bytes);
    for(ClassDef c:d.getClasses())for(Method m:c.getMethods()){
     if(m.getImplementation()==null)continue;
     List<Instruction> ins=instructions(m);
     if(c.getType().equals("LX/06Sp;")){
      if(m.getName().equals("A01")||m.getName().equals("BEh")){
       List<Instruction> constants=new ArrayList<>();
       for(Instruction i:ins)if(i.getOpcode()==Opcode.CONST_4&&((NarrowLiteralInstruction)i).getNarrowLiteral()==1&&((OneRegisterInstruction)i).getRegisterA()==0)constants.add(i);
       if(constants.size()!=1)throw new IllegalStateException("Unexpected input-enable shape "+m);
       emit(m,constants.get(0),new byte[]{0x12,0},"Force native Reels touch paging disabled");hits++;
      }
      if(m.getName().equals("H99")||m.getName().equals("H9E")){
       if(!m.getImplementation().getTryBlocks().isEmpty())throw new IllegalStateException("Unexpected paging try block");
       byte[] b=new byte[ins.get(0).getCodeUnits()*2];b[0]=0x0e;
       emit(m,ins.get(0),b,"Disable explicit Reels next/previous commands without changing initial item positioning");hits++;
      }
     }
     if(c.getType().equals("LX/03am;")&&m.getName().equals("A01"))for(Instruction i:ins){
      FieldReference f=field(i);if(f!=null&&f.getDefiningClass().equals("LX/03am;")&&f.getName().equals("A08")){
       int reg=((OneRegisterInstruction)i).getRegisterA();if(reg>15)throw new IllegalStateException();
       emit(m,i,new byte[]{0x12,(byte)reg,0,0},"Exclude clips destination from configured native tab parsing");hits++;
      }
     }
     if(c.getType().equals("LX/04sM;")){
      if(m.getName().equals("A00"))for(Instruction i:ins){FieldReference f=field(i);if(f!=null&&f.getDefiningClass().equals("LX/03am;")&&f.getName().equals("A08")){emit(m,i,fieldReplacement(i,fieldIndex(d,"A0C")),"Use Home instead of Reels as default destination");hits++;}}
      if(m.getName().equals("A01")){
       Map<Integer,String> replacement=Map.of(2,"A0A",3,"A0E",4,"A09",5,"A0F");
       for(Instruction i:ins){
        if(i.getOpcode()==Opcode.SGET_OBJECT&&replacement.containsKey(((OneRegisterInstruction)i).getRegisterA())){
         emit(m,i,fieldReplacement(i,fieldIndex(d,replacement.get(((OneRegisterInstruction)i).getRegisterA()))),"Compact fallback native tabs without clips");hits++;
        }
        if(i.getOpcode()==Opcode.FILLED_NEW_ARRAY_RANGE){
         int p=((DexBackedInstruction)i).instructionStart;byte[] b=Arrays.copyOfRange(bytes,p,p+6);
         if(Byte.toUnsignedInt(b[1])!=7)throw new IllegalStateException("Unexpected tab count");
         b[1]=6;emit(m,i,b,"Fallback native tab list contains six remaining destinations");hits++;
        }
       }
      }
     }
     if(c.getType().equals("LX/04sT;")&&m.getName().equals("A0B")){
      if(ins.get(0).getOpcode()!=Opcode.CONST_4||ins.get(1).getOpcode()!=Opcode.INVOKE_STATIC||ins.get(0).getCodeUnits()+ins.get(1).getCodeUnits()!=4)throw new IllegalStateException("Unexpected navigation precondition");
      int base=((DexBackedInstruction)ins.get(0)).instructionStart;
      Instruction ret=ins.stream().filter(i->i.getOpcode()==Opcode.RETURN_VOID).findFirst().orElseThrow();
      int relative=(((DexBackedInstruction)ret).instructionStart-base)/2-2;
      int reg=m.getImplementation().getRegisterCount()-m.getParameters().size()+1;
      if(reg>15||relative<0||relative>32767)throw new IllegalStateException("Navigation range");
      int field=fieldIndex(d,"A08");
      emitAt(m,base,new byte[]{0x62,0,(byte)field,(byte)(field>>8),0x32,(byte)reg,(byte)relative,(byte)(relative>>8)},"Reject dedicated Reels tab fragment pushes before navigation; replaces target non-null assertion");hits++;
     }
    }
   }
  }
  if(hits!=12)throw new IllegalStateException("Expected 12 reviewed patches, found "+hits);
 }
}
