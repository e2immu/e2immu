package org.e2immu.analyser.testexample;

import java.util.List;

public class OutputBuilderSimplified_0 {

    static boolean len4(List<String> list) {
        return list.stream().allMatch(s -> s.length() > 3);
    }

}
