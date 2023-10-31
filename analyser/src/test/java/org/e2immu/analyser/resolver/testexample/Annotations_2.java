package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.a.Resource;
import org.e2immu.analyser.resolver.testexample.a.Resources;

import static org.e2immu.analyser.resolver.testexample.Annotations_2.XX;

@Resources({
        @Resource(name = XX, lookup = "yy", type = java.util.TreeMap.class),
        @Resource(name = Annotations_2.ZZ, type = Integer.class)
})
public class Annotations_2 {
    static final String XX = "xx";
    static final String ZZ = "zz";
}

