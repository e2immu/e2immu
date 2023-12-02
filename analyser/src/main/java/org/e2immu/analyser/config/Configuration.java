/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.config;

import org.e2immu.analyser.log.InitLoggers;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Basic use:
 * Input configuration: configure source path, configure class path to include
 * all libraries. Not changing JRE, not restricting
 * Upload activated, not writing XML, not writing annotation API files
 */
public record Configuration(InputConfiguration inputConfiguration,
                            Set<LogTarget> logTargets,
                            boolean quiet,
                            boolean ignoreErrors,
                            boolean skipAnalysis,
                            boolean parallel,
                            InspectorConfiguration inspectorConfiguration,
                            AnalyserConfiguration analyserConfiguration,
                            UploadConfiguration uploadConfiguration,
                            AnnotatedAPIConfiguration annotatedAPIConfiguration,
                            AnnotationXmlConfiguration annotationXmlConfiguration,
                            DebugConfiguration debugConfiguration) {

    public static final String EQUALS = "org.e2immu.analyser.EQUALS";
    static final String NL_TAB = "\n    ";

    @Override
    public String toString() {
        return "Configuration:" +
                NL_TAB + "ignoreErrors=" + ignoreErrors +
                NL_TAB + "logTargets=" + logTargets.stream().map(Object::toString).collect(Collectors.joining(",")) +
                NL_TAB + "parallel=" + parallel +
                NL_TAB + "quiet=" + quiet +
                NL_TAB + "skipAnalysis=" + skipAnalysis + "\n" +
                analyserConfiguration + "\n" +
                annotatedAPIConfiguration + "\n" +
                annotationXmlConfiguration + "\n" +
                inputConfiguration + "\n" +
                inspectorConfiguration + "\n" +
                uploadConfiguration;
    }

    // the equals method is here primarily for testing! It should include all fields

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Configuration that = (Configuration) o;
        return quiet == that.quiet &&
                ignoreErrors == that.ignoreErrors &&
                skipAnalysis == that.skipAnalysis &&
                inputConfiguration.equals(that.inputConfiguration) &&
                logTargets.equals(that.logTargets) &&
                uploadConfiguration.equals(that.uploadConfiguration) &&
                annotatedAPIConfiguration.equals(that.annotatedAPIConfiguration) &&
                annotationXmlConfiguration.equals(that.annotationXmlConfiguration) &&
                debugConfiguration.equals(that.debugConfiguration) &&
                inspectorConfiguration.equals(that.inspectorConfiguration) &&
                analyserConfiguration.equals(that.analyserConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logTargets, quiet, ignoreErrors, skipAnalysis,
                inputConfiguration,
                uploadConfiguration, annotatedAPIConfiguration, annotationXmlConfiguration,
                analyserConfiguration, inspectorConfiguration, debugConfiguration);
    }

    public void initializeLoggers() {
        InitLoggers.go(logTargets, quiet);
    }

    @Container
    public static class Builder {
        private boolean quiet;
        private boolean ignoreErrors;
        private boolean skipAnalysis;
        private boolean parallel;
        private final Set<LogTarget> logTargets = new HashSet<>();

        private InputConfiguration inputConfiguration;
        private UploadConfiguration uploadConfiguration;
        private AnnotatedAPIConfiguration annotatedAPIConfiguration;
        private AnnotationXmlConfiguration annotationXmlConfiguration;
        private DebugConfiguration debugConfiguration;
        private AnalyserConfiguration analyserConfiguration;
        private InspectorConfiguration inspectorConfiguration;

        public Configuration build() {
            return new Configuration(inputConfiguration != null ? inputConfiguration : new InputConfiguration.Builder().build(),
                    Set.copyOf(logTargets),
                    quiet,
                    ignoreErrors,
                    skipAnalysis,
                    parallel,
                    inspectorConfiguration != null ? inspectorConfiguration : new InspectorConfiguration.Builder().build(),
                    analyserConfiguration != null ? analyserConfiguration : new AnalyserConfiguration.Builder().build(),
                    uploadConfiguration != null ? uploadConfiguration : new UploadConfiguration.Builder().build(),
                    annotatedAPIConfiguration != null ? annotatedAPIConfiguration : new AnnotatedAPIConfiguration.Builder().build(),
                    annotationXmlConfiguration != null ? annotationXmlConfiguration : new AnnotationXmlConfiguration.Builder().build(),
                    debugConfiguration != null ? debugConfiguration : new DebugConfiguration.Builder().build()
            );
        }

        @Fluent
        public Builder setInspectorConfiguration(InspectorConfiguration inspectorConfiguration) {
            this.inspectorConfiguration = inspectorConfiguration;
            return this;
        }

        @Fluent
        public Builder setInputConfiguration(InputConfiguration inputConfiguration) {
            this.inputConfiguration = inputConfiguration;
            return this;
        }

        @Fluent
        public Builder setUploadConfiguration(UploadConfiguration uploadConfiguration) {
            this.uploadConfiguration = uploadConfiguration;
            return this;
        }


        @Fluent
        public Builder setIgnoreErrors(boolean ignoreErrors) {
            this.ignoreErrors = ignoreErrors;
            return this;
        }

        @Fluent
        public Builder setQuiet(boolean quiet) {
            this.quiet = quiet;
            return this;
        }

        @Fluent
        public Builder setSkipAnalysis(boolean skipAnalysis) {
            this.skipAnalysis = skipAnalysis;
            return this;
        }

        @Fluent
        public Builder setParallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        @Fluent
        public Builder setAnnotatedAPIConfiguration(AnnotatedAPIConfiguration annotatedAPIConfiguration) {
            this.annotatedAPIConfiguration = annotatedAPIConfiguration;
            return this;
        }

        @Fluent
        public Builder setAnnotationXmConfiguration(AnnotationXmlConfiguration annotationXmConfiguration) {
            this.annotationXmlConfiguration = annotationXmConfiguration;
            return this;
        }

        @Fluent
        public Builder setDebugConfiguration(DebugConfiguration debugConfiguration) {
            this.debugConfiguration = debugConfiguration;
            return this;
        }

        @Fluent
        public Builder setAnalyserConfiguration(AnalyserConfiguration analyserConfiguration) {
            this.analyserConfiguration = analyserConfiguration;
            return this;
        }

        @Fluent
        public Builder addDebugLogTargets(String debugLogTargets) {
            for (String s : debugLogTargets.split(",")) {
                if (s != null && !s.trim().isEmpty()) {
                    try {
                        LogTarget logTarget = LogTarget.valueOf(s.toUpperCase());
                        logTargets.add(logTarget);
                    } catch (IllegalArgumentException iae) {
                        System.err.println("Log target " + s + " unknown, ignored");
                    }
                }
            }
            return this;
        }

        @Fluent
        public Builder addDebugLogTargets(LogTarget... debugLogTargets) {
            logTargets.addAll(Arrays.asList(debugLogTargets));
            return this;
        }
    }
}
