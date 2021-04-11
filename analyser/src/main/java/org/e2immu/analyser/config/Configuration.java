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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@E2Immutable
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    public static final String PATH_SEPARATOR = System.getProperty("path.separator");

    // input options
    public final InputConfiguration inputConfiguration;

    // common options
    public final Set<org.e2immu.analyser.util.Logger.LogTarget> logTargets;
    public final boolean quiet;
    public final boolean ignoreErrors;
    public final boolean skipAnalysis;

    // analyser
    public final AnalyserConfiguration analyserConfiguration;

    // upload
    public final UploadConfiguration uploadConfiguration;

    // write AA
    public final AnnotatedAPIConfiguration annotatedAPIConfiguration;

    // write a Xml
    public final AnnotationXmlConfiguration annotationXmlConfiguration;

    // for debugging purposes
    public final DebugConfiguration debugConfiguration;

    private Configuration(InputConfiguration inputConfiguration,

                          Set<org.e2immu.analyser.util.Logger.LogTarget> logTargets,
                          boolean quiet,
                          boolean ignoreErrors,
                          boolean skipAnalysis,

                          AnalyserConfiguration analyserConfiguration,
                          UploadConfiguration uploadConfiguration,
                          AnnotatedAPIConfiguration annotatedAPIConfiguration,
                          AnnotationXmlConfiguration annotationXmlConfiguration,
                          DebugConfiguration debugConfiguration) {
        this.inputConfiguration = inputConfiguration;
        this.logTargets = logTargets;
        this.quiet = quiet;
        this.ignoreErrors = ignoreErrors;
        this.skipAnalysis = skipAnalysis;
        this.uploadConfiguration = uploadConfiguration;
        this.annotatedAPIConfiguration = annotatedAPIConfiguration;
        this.annotationXmlConfiguration = annotationXmlConfiguration;
        this.debugConfiguration = debugConfiguration;
        this.analyserConfiguration = analyserConfiguration;
    }

    @Override
    public String toString() {
        return inputConfiguration +
                "logTargets: " + logTargets.stream().map(org.e2immu.analyser.util.Logger.LogTarget::toString).collect(Collectors.joining(", ")) +
                "\nquiet: " + quiet +
                "\nignoreErrors: " + ignoreErrors +
                "\n" +
                uploadConfiguration +
                annotatedAPIConfiguration +
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
        if (quiet) {
            org.e2immu.analyser.util.Logger.configure(Level.ERROR);
        } else {
            org.e2immu.analyser.util.Logger.configure(Level.INFO);
            org.e2immu.analyser.util.Logger.activate(logTargets);
        }
    }

    @Container
    public static class Builder {
        private InputConfiguration inputConfiguration;

        private boolean quiet;
        private boolean ignoreErrors;
        private boolean skipAnalysis;
        private final Set<org.e2immu.analyser.util.Logger.LogTarget> logTargets = new HashSet<>();

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
                    try {
                        org.e2immu.analyser.util.Logger.LogTarget logTarget = org.e2immu.analyser.util.Logger.LogTarget.valueOf(s.toUpperCase());
                        logTargets.add(logTarget);
                    } catch (RuntimeException rte) {
                        LOGGER.warn("Ignoring unrecognized log target '{}'", s.toUpperCase());
                    }
                }
            }
            return this;
        }
    }
}
