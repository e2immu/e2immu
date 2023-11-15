package org.e2immu.analyser.resolver.testexample;

import java.util.ArrayList;
import java.util.List;

public class MethodCall_67 {
    List<String> onDemandHistory = new ArrayList<>();

    private void error(String msg, Object object) {
        System.out.println(msg + ": " + object);
    }

    void method() {
        error("On-demand history:\n{}", String.join("\n", onDemandHistory));
    }
}
