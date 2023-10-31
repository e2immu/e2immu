package org.e2immu.analyser.log;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Arrays;
import java.util.List;

/*
corresponds to packages inside the "org.e2immu.analyser" package
 */
public enum LogTarget {
    ANALYSER("analyser"), // must come AFTER COMPUTING, SHALLOW
    COMPUTING_ANALYSERS("analyser.impl.aggregating",
            "analyser.impl.computing",
            "analyser.impl.context",
            "analyser.impl.primary",
            "analyser.impl.util",
            "analyser.nonanalyser",
            "analyser.statementanalyser",
            "analyser.util"),
    SHALLOW_ANALYSERS("analyser.impl.shallow",
            "analyser.impl.primary"),
    ANALYSIS("analysis"),
    MODEL("model"),
    RESOLVER("resolver"),
    INSPECTOR("inspector"),
    BYTECODE("bytecode"),
    ANNOTATION_XML("annotationxml"),
    ANNOTATED_API("annotatedapi"),
    PARSER("parser");

    LogTarget(String... prefixes) {
        this.prefixes = prefixes;
    }

    private final String[] prefixes;


    public List<String> prefixes() {
        return Arrays.stream(prefixes).toList();
    }

    public static final String MAIN_PACKAGE = "org.e2immu.analyser";
    public static final Marker A_API = MarkerFactory.getMarker("a_api");
    public static final Marker SOURCE = MarkerFactory.getMarker("source");
}
