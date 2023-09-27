package org.e2immu.analyser.config;

import java.util.Arrays;
import java.util.List;

/*
corresponds to packages inside the "org.e2immu.analyser" package
 */
public enum LogTarget {
    ANALYSER("analyser"), // must come AFTER COMPUTING, SHALLOW
    COMPUTING_ANALYSERS("analyser.impl.aggregating", "analyser.impl.computing"),
    SHALLOW_ANALYSERS("analyser.impl.shallow"),
    ANALYSIS("analysis"),
    MODEL("model"),
    RESOLVER("resolver"),
    INSPECTOR("inspector"),
    BYTECODE("bytecode"),
    ANNOTATION_XML("annotationxml"),
    ANNOTATED_API("annotatedapi");

    LogTarget(String... prefixes) {
        this.prefixes = prefixes;
    }

    private final String[] prefixes;


    public List<String> prefixes() {
        return Arrays.stream(prefixes).toList();
    }
}
