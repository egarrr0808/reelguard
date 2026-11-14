import local.reelguard.TapGate;
public class TapGateTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("gesture check "+checks); }
    public static void main(String[] args) {
        TapGate gate=new TapGate(10);
        check(!gate.finish(0,0));
        gate.begin(100,100);check(gate.finish(100,100));
        gate.begin(100,100);gate.move(103,104);check(gate.finish(106,108));
        gate.begin(100,100);check(!gate.finish(100,111));
        gate.begin(100,100);gate.move(100,800);check(!gate.finish(100,100));
        gate.begin(100,100);gate.move(800,100);check(!gate.finish(100,100));
        gate.begin(100,100);gate.cancel();check(!gate.finish(100,100));
        gate.begin(100,100);gate.cancel();gate.move(100,100);check(!gate.finish(100,100));
        gate.begin(100,100);check(gate.finish(100,100));check(!gate.finish(100,100));
        gate.begin(Float.NaN,100);check(!gate.finish(100,100));
        gate.begin(100,100);check(!gate.finish(Float.POSITIVE_INFINITY,100));
        gate.begin(100,100);gate.move(103,104);gate.move(103,104);check(gate.finish(103,104));
        try { new TapGate(-1); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS "+checks+" gesture checks");
    }
}
