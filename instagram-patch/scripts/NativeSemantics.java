import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.dexbacked.instruction.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

/** Static postconditions read from the built APK, not from the patch manifest. */
public class NativeSemantics {
 static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static List<Instruction> list(Method m){List<Instruction>a=new ArrayList<>();m.getImplementation().getInstructions().forEach(a::add);return a;}
 static Map<String,Method> read(ZipFile zip,String entry,String type)throws Exception{
  DexBackedDexFile d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(zip.getEntry(entry))));
  for(ClassDef c:d.getClasses())if(c.getType().equals(type)){Map<String,Method> out=new HashMap<>();for(Method m:c.getMethods())out.put(m.getName(),m);return out;}
  throw new IllegalStateException("Class missing "+type);
 }
 static FieldReference field(Instruction i){if(i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof FieldReference)return (FieldReference)((ReferenceInstruction)i).getReference();return null;}
 static void check(String path)throws Exception{
  try(ZipFile zip=new ZipFile(path)){
   Map<String,Method> pager=read(zip,"classes5.dex","LX/06Sp;");
   for(String name:List.of("A01","BEh")){
    List<Instruction> code=list(pager.get(name));int zero=0;
    for(Instruction i:code)if(i.getOpcode()==Opcode.CONST_4&&((OneRegisterInstruction)i).getRegisterA()==0){int v=((NarrowLiteralInstruction)i).getNarrowLiteral();require(v==0,"Reels input can be enabled in "+name);zero++;}
    require(zero>0,"Missing input-disable constant");
   }
   for(String name:List.of("H99","H9E"))require(list(pager.get(name)).get(0).getOpcode()==Opcode.RETURN_VOID,"Next/previous is executable");
   List<Instruction> parser=list(read(zip,"classes.dex","LX/03am;").get("A01"));
   require(parser.stream().noneMatch(i->{FieldReference f=field(i);return f!=null&&f.getName().equals("A08");}),"Tab parser still returns CLIPS");
   Map<String,Method> tabs=read(zip,"classes2.dex","LX/04sM;");
   List<String> defaults=new ArrayList<>();
   for(Instruction i:list(tabs.get("A00"))){FieldReference f=field(i);if(f!=null&&f.getDefiningClass().equals("LX/03am;"))defaults.add(f.getName());}
   require(!defaults.contains("A08")&&defaults.contains("A0C"),"Default destination still CLIPS");
   Map<Integer,String> registers=new HashMap<>();boolean arrayFound=false;
   for(Instruction i:list(tabs.get("A01"))){
    FieldReference f=field(i);if(f!=null&&i.getOpcode()==Opcode.SGET_OBJECT)registers.put(((OneRegisterInstruction)i).getRegisterA(),f.getName());
    if(i.getOpcode()==Opcode.FILLED_NEW_ARRAY_RANGE){
     RegisterRangeInstruction range=(RegisterRangeInstruction)i;List<String> values=new ArrayList<>();
     for(int n=0;n<range.getRegisterCount();n++)values.add(registers.get(range.getStartRegister()+n));
     require(values.equals(List.of("A0C","A0G","A0A","A0E","A09","A0F")),"Wrong compacted tab list "+values);
     require(new HashSet<>(values).size()==values.size(),"Duplicate native tabs");arrayFound=true;
    }
   }
   require(arrayFound,"No native fallback tab array");
   Method nav=read(zip,"classes2.dex","LX/04sT;").get("A0B");List<Instruction> code=list(nav);
   require(code.get(0).getOpcode()==Opcode.SGET_OBJECT&&field(code.get(0)).getName().equals("A08"),"Navigation does not load CLIPS identity");
   require(code.get(1).getOpcode()==Opcode.IF_EQ,"Navigation does not compare target identity");
   TwoRegisterInstruction cmp=(TwoRegisterInstruction)code.get(1);
   require(cmp.getRegisterA()==10&&cmp.getRegisterB()==0,"Wrong navigation target register");
   int target=((DexBackedInstruction)code.get(1)).instructionStart+((OffsetInstruction)code.get(1)).getCodeOffset()*2;
   require(code.stream().anyMatch(i->((DexBackedInstruction)i).instructionStart==target&&i.getOpcode()==Opcode.RETURN_VOID),"Reels navigation does not branch directly to return");
   require(code.get(2).getOpcode()==Opcode.CONST_WIDE_16,"Other navigation does not continue original body");
  }
 }
 public static void main(String[] args)throws Exception{
  boolean rejected=false;try{check(args[0]);}catch(IllegalStateException expected){rejected=true;System.out.println("PASS unpatched positive control rejected: "+expected.getMessage());}
  require(rejected,"Verifier accepted the unpatched APK");
  check(args[1]);System.out.println("PASS native player and menu semantic postconditions");
 }
}
