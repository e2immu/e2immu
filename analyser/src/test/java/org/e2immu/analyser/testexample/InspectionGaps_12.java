package org.e2immu.analyser.testexample;

// IMPORTANT: ensure that there is a * here
import org.e2immu.analyser.testexample.a.TypeWithStaticSubType.*;

public class InspectionGaps_12 {

    public static int method1() {
        SubType1 subType1 = new SubType1(2);
        return subType1.doSomething(C1.CONSTANT);
    }

    public static int method2(SubType2 subType2) {
        return subType2.doSomething(C2.CONSTANT);
    }
}
