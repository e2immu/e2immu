package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.access.AbstractFilter;
import org.e2immu.analyser.resolver.testexample.access.Filter;

public class Import_12 {

    public Filter method() {
        return new AbstractFilter() {
            public Result filter(String s) {
                return Result.ACCEPT;
            }
        };
    }
}
