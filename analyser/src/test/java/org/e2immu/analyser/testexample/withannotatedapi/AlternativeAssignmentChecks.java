package org.e2immu.analyser.testexample.withannotatedapi;

import java.util.Map;

public class AlternativeAssignmentChecks {

    private final Map<String, Integer> map;

    public AlternativeAssignmentChecks(Map<String, Integer> map) {
        this.map = map;
    }

    // all 3 get methods should return the same in-lined value
    // local variable [ conditional (c, wrapper( ), default) ]

    // detected in ConditionalValue.conditionalValue
    public int get1(String label1, int defaultValue1) {
        return map.get(label1) == null ? defaultValue1 : map.get(label1);
    }

    // detected using the joinReturnStatements pattern, and then ConditionalValue.conditionalValue
    public int get2(String label2, int defaultValue2) {
        Integer i2 = map.get(label2);
        if(i2 == null) return defaultValue2;
        return i2;
    }

    // detected in ConditionalValue.conditionalValue
    public int get3(String label3, int defaultValue3) {
        Integer i3 = map.get(label3);
        return i3 != null ? i3: defaultValue3;
    }
}
