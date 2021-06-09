package org.e2immu.analyser.testexample;

import java.util.List;

public class OutputBuilderSimplified_1 {

    static boolean notStart(List<String> list ) {
        return !list.stream().allMatch(s -> s.length() > 3);
    }

}
