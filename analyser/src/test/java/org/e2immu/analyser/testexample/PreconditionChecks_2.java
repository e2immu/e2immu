package org.e2immu.analyser.testexample;

public class PreconditionChecks_2 {

    private int i;

    // some examples of combined preconditions...
    // this one shows that you cannot simply say in the 2nd case: there was one already!

    private static boolean combinedPrecondition1$Precondition(int p1, int p2) {
        return p1 >= 2 && p2 > 0;
    }

    public void combinedPrecondition1(int p1, int p2) {
        if (p1 < 2 || p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

    private static boolean combinedPrecondition2$Precondition(int p1, int p2) {
        return p1 >= 2 && p2 > 0;
    }

    public void combinedPrecondition2(int p1, int p2) {
        if (p1 <= 0) throw new UnsupportedOperationException(); // IRRELEVANT given the next one
        if (p1 < 2 || p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

    // here, the first condition does not disappear, because of the AND rather than the OR
    static boolean combinedPrecondition3$Precondition(int p1, int p2) {
        return p1 >= 1 && (p1 >= 2 || p2 >= 1);
    }

    public void combinedPrecondition3(int p1, int p2) {
        if (p1 <= 0) throw new UnsupportedOperationException();
        if (p1 < 2 && p2 <= 0) throw new UnsupportedOperationException();
        this.i = p1 > p2 ? p1 + 3 : p2;
    }

}
