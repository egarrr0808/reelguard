import java.io.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.baksmali.*;
public class ExtractSmali {
 public static void main(String[] a) throws Exception {
  DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(new FileInputStream(a[0])));
  BaksmaliOptions options=new BaksmaliOptions(); options.debugInfo=false;options.codeOffsets=true;
  if(!Baksmali.disassembleDexFile(dex,new File(a[1]),2,options,Arrays.asList(Arrays.copyOfRange(a,2,a.length))))throw new IOException("Disassembly failed");
 }
}
