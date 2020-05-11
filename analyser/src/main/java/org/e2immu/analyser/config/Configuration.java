/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.config;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.cli.Main;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.e2immu.analyser.cli.Main.COMMA;

/**
 * Basic use:
 * Input configuration: configure source path, configure class path to include
 * all libraries. Not changing JRE, not restricting
 * Upload activated, not writing XML, not writing annotation API files
 */
@E2Immutable
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    // input options
    public final InputConfiguration inputConfiguration;

    // common options
    public final Set<org.e2immu.analyser.util.Logger.LogTarget> logTargets;
    public final boolean quiet;
    public final boolean ignoreErrors;
    public final boolean skipAnalysis;

    // upload
    public final UploadConfiguration uploadConfiguration;

    // write AA
    public final AnnotatedAPIConfiguration annotatedAPIConfiguration;

    // write a Xml
    public final AnnotationXmlConfiguration annotationXmlConfiguration;

    public Configuration(InputConfiguration inputConfiguration,

                         Set<org.e2immu.analyser.util.Logger.LogTarget> logTargets,
                         boolean quiet,
                         boolean ignoreErrors,
                         boolean skipAnalysis,

                         UploadConfiguration uploadConfiguration,
                         AnnotatedAPIConfiguration annotatedAPIConfiguration,
                         AnnotationXmlConfiguration annotationXmlConfiguration) {
        this.inputConfiguration = inputConfiguration;
        this.logTargets = logTargets;
        this.quiet = quiet;
        this.ignoreErrors = ignoreErrors;
        this.skipAnalysis = skipAnalysis;
        this.uploadConfiguration = uploadConfiguration;
        this.annotatedAPIConfiguration = annotatedAPIConfiguration;
        this.annotationXmlConfiguration = annotationXmlConfiguration;
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

    public static Configuration fromProperties(Map<String, String> analyserProperties) {
        Builder builder = new Builder();
        builder.setInputConfiguration(InputConfiguration.fromProperties(analyserProperties));
        builder.setUploadConfiguration(UploadConfiguration.fromProperties(analyserProperties));
        builder.setAnnotatedAPIConfiguration(AnnotatedAPIConfiguration.fromProperties(analyserProperties));
        builder.setWriteAnnotationXmConfiguration(AnnotationXmlConfiguration.fromProperties(analyserProperties));

        setBooleanProperty(analyserProperties, Main.QUIET, builder::setQuiet);
        setBooleanProperty(analyserProperties, Main.IGNORE_ERRORS, builder::setIgnoreErrors);
        setBooleanProperty(analyserProperties, Main.SKIP_ANALYSIS, builder::setSkipAnalysis);

        setSplitStringProperty(analyserProperties, COMMA, Main.DEBUG, builder::addDebugLogTargets);

        return builder.build();
    }

    static void setStringProperty(Map<String, String> properties, String key, Consumer<String> consumer) {
        String value = properties.get(key);
        if (value != null) {
            String trim = value.trim();
            if (!trim.isEmpty()) consumer.accept(trim);
        }
    }

    static void setSplitStringProperty(Map<String, String> properties, String separator, String key, Consumer<String> consumer) {
        String value = properties.get(key);
        LOGGER.debug("Have {}: {}", key, value);
        if (value != null) {
            String[] parts = value.split(separator);
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    consumer.accept(part);
                }
            }
        }
    }

    static void setBooleanProperty(Map<String, String> properties, String key, Consumer<Boolean> consumer) {
        String value = properties.get(key);
        if (value != null) {
            consumer.accept("true".equalsIgnoreCase(value.trim()));
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

        public Configuration build() {
            return new Configuration(inputConfiguration != null ? inputConfiguration : new InputConfiguration.Builder().build(),
                    ImmutableSet.copyOf(logTargets),
                    quiet,
                    ignoreErrors,
                    skipAnalysis,
                    uploadConfiguration != null ? uploadConfiguration : new UploadConfiguration.Builder().build(),
                    annotatedAPIConfiguration != null ? annotatedAPIConfiguration : new AnnotatedAPIConfiguration.Builder().build(),
                    annotationXmlConfiguration != null ? annotationXmlConfiguration : new AnnotationXmlConfiguration.Builder().build()
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
        public Builder setWriteAnnotationXmConfiguration(AnnotationXmlConfiguration annotationXmConfiguration) {
            this.annotationXmlConfiguration = annotationXmConfiguration;
            return this;
        }

        @Fluent
        public Builder addDebugLogTargets(String debugLogTargets) {
            for (String s : debugLogTargets.split(COMMA)) {
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
