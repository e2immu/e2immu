package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.importhelper.SubType_4Helper;

import java.util.List;

public class SubType_4 {

    void method(List<String> strings) {
        SubType_4Helper b = new SubType_4Helper();
        b.set(createD(strings));
    }

    private org.e2immu.analyser.resolver.testexample.importhelper.SubType_4Helper.D createD(List<String> strings) {
        org.e2immu.analyser.resolver.testexample.importhelper.SubType_4Helper.D d
                = new org.e2immu.analyser.resolver.testexample.importhelper.SubType_4Helper.D();
        System.out.println(d + " = " + strings);
        return d;
    }
}
