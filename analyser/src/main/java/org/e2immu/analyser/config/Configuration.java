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

import ch.qos.logback.classic.Level;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Basic use:
 * Input configuration: configure source path, configure class path to include
 * all libraries. Not changing JRE, not restricting
 * Upload activated, not writing XML, not writing annotation API files
 */
@E2Immutable
public record Configuration(InputConfiguration inputConfiguration,
                            Set<String> logTargets,
                            boolean quiet,
                            boolean ignoreErrors,
                            boolean skipAnalysis,
                            AnalyserConfiguration analyserConfiguration,
                            UploadConfiguration uploadConfiguration,
                            AnnotatedAPIConfiguration annotatedAPIConfiguration,
                            AnnotationXmlConfiguration annotationXmlConfiguration,
                            DebugConfiguration debugConfiguration) {

    public static final String EQUALS = "org.e2immu.analyser.EQUALS";

    @Override
    public String toString() {
        return "Configuration:" +
                "\n    logTargets: " + String.join(", ", logTargets) +
                "\n    quiet: " + quiet +
                "\n    ignoreErrors: " + ignoreErrors +
                "\n" +
                inputConfiguration + "\n" +
                uploadConfiguration + "\n" +
                annotatedAPIConfiguration + "\n" +
                annotationXmlConfiguration;
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
                annotationXmlConfiguration.equals(that.annotationXmlConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputConfiguration, logTargets, quiet, ignoreErrors,
                skipAnalysis,
                uploadConfiguration, annotatedAPIConfiguration, annotationXmlConfiguration);
    }

    public void initializeLoggers() {
        ch.qos.logback.classic.Logger overall = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger("org.e2immu.analyser");
        if (quiet) {
            overall.setLevel(Level.ERROR);
        } else {
            overall.setLevel(Level.INFO);
            for (String prefix : logTargets) {
                ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger("org.e2immu.analyser." + prefix);
                logger.setLevel(Level.DEBUG);
            }
        }
    }

    @Container
    public static class Builder {
        private InputConfiguration inputConfiguration;

        private boolean quiet;
        private boolean ignoreErrors;
        private boolean skipAnalysis;
        private final Set<String> logTargets = new HashSet<>();

        private UploadConfiguration uploadConfiguration;
        private AnnotatedAPIConfiguration annotatedAPIConfiguration;
        private AnnotationXmlConfiguration annotationXmlConfiguration;
        private DebugConfiguration debugConfiguration;
        private AnalyserConfiguration analyserConfiguration;

        public Configuration build() {
            return new Configuration(inputConfiguration != null ? inputConfiguration : new InputConfiguration.Builder().build(),
                    Set.copyOf(logTargets),
                    quiet,
                    ignoreErrors,
                    skipAnalysis,
                    analyserConfiguration != null ? analyserConfiguration : new AnalyserConfiguration.Builder().build(),
                    uploadConfiguration != null ? uploadConfiguration : new UploadConfiguration.Builder().build(),
                    annotatedAPIConfiguration != null ? annotatedAPIConfiguration : new AnnotatedAPIConfiguration.Builder().build(),
                    annotationXmlConfiguration != null ? annotationXmlConfiguration : new AnnotationXmlConfiguration.Builder().build(),
                    debugConfiguration != null ? debugConfiguration : new DebugConfiguration.Builder().build()
            );
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
                    logTargets.add(s);
                }
            }
            return this;
        }
    }
}
