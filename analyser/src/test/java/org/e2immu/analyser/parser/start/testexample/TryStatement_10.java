package org.e2immu.analyser.parser.start.testexample;

public abstract class TryStatement_10 {

    abstract Throwable catchThrowable();

    public String method(String in) throws Throwable {
        Throwable t0 = catchThrowable();
        if (t0 == null) {
            // CRASH ONLY WITH ANNOTATED API + THE FOLLOWING LINE:
            System.out.println("input: " + in);
            return in.toUpperCase();
        }
        if (t0 instanceof NullPointerException npe) {
            System.out.println("Caught null!");
            throw npe;
        }
        throw t0;
    }
}
