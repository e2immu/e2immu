package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.Map;

public class Constructor_13B {

    private Constructor_13A a;
    private final Map<String, Object> map = new HashMap<>();

    void method() {
        a.new Inner().value = 3;
        map.put("key", a.new Inner());
    }
}
