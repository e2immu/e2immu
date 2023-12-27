package org.e2immu.analyser.cli;

/*
Used by the gradle plugin, and some implementations of Action.
 */
public enum GradleConfiguration {
    COMPILE("implementation", "i", false, false),
    TEST("testImplementation", "ti", false, false),
    RUNTIME("runtimeOnly", "r", false, true),
    TEST_RUNTIME("testRuntimeOnly", "tr", false, true),
    COMPILE_TRANSITIVE("compileClasspath", "i-t", true, false),
    TEST_TRANSITIVE("testCompileClasspath", "ti-t", true, false),
    RUNTIME_TRANSITIVE("runtimeClasspath", "r-t", true, true),
    TEST_RUNTIME_TRANSITIVE("testRuntimeClasspath", "tr-t", true, true);

    public final String gradle;
    public final String abbrev;
    public final boolean transitive;
    public final boolean runtime;

    GradleConfiguration(String gradle, String abbrev, boolean transitive, boolean runtime) {
        this.gradle = gradle;
        this.abbrev = abbrev;
        this.transitive = transitive;
        this.runtime = runtime;
    }

    public GradleConfiguration nonTransitive() {
        return switch (this) {
            case COMPILE_TRANSITIVE -> COMPILE;
            case TEST_TRANSITIVE -> TEST;
            case RUNTIME_TRANSITIVE -> RUNTIME;
            case TEST_RUNTIME_TRANSITIVE -> TEST_RUNTIME;
            default -> throw new UnsupportedOperationException();
        };
    }
}
