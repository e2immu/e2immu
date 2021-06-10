package org.e2immu.analyser.testexample;

import java.util.LinkedList;
import java.util.List;

/*
Problem becomes infinite loop when the separator if-statement (combiner:2) is removed.
Otherwise, its a plain error overwriting CNN from 1 to 5 for a.list
 */
public class OutputBuilderSimplified_3 {
    interface OutputElement {
        int size();
    }

    final List<OutputElement> list = new LinkedList<>();

    public OutputBuilderSimplified_3 add(OutputElement outputElement) {
        list.add(outputElement);
        return this;
    }

    public OutputBuilderSimplified_3 add(OutputBuilderSimplified_3 b) {
        list.addAll(b.list);
        return this;
    }

    public static OutputBuilderSimplified_3 combiner(OutputBuilderSimplified_3 a,
                                                     OutputBuilderSimplified_3 b,
                                                     OutputElement separator,
                                                     OutputElement mid) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        if (separator != null)
            a.add(separator);
        return a.add(mid).add(b);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }
}
