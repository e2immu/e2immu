package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.a.Resource;
import org.e2immu.analyser.resolver.testexample.a.Resources;

@Resources({
        @Resource(name = "xx", lookup = "yy", type = java.util.TreeMap.class),
        @Resource(name = "zz", type = java.lang.Integer.class)
})
public class Annotations_1 {
    static final String XX = "xx";
}

