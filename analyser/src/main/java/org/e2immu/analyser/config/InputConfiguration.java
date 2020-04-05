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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.cli.Main;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.e2immu.analyser.cli.Main.*;
import static org.e2immu.analyser.config.Configuration.setSplitStringProperty;
import static org.e2immu.analyser.config.Configuration.setStringProperty;

@E2Immutable
public class InputConfiguration {
    public static final String DEFAULT_SOURCE_DIRS = "src/main/java";
    public static final String[] DEFAULT_CLASSPATH = {"build/classes/java/main",
            "jmods/java.base.jmod", "src/main/resources/annotatedAPIs"};

    // input options
    public final List<String> sources;
    public final Charset sourceEncoding;
    public final List<String> classPathParts;
    public final List<String> restrictSourceToPackages;
    public final String alternativeJREDirectory;

    public InputConfiguration(List<String> sources,
                              List<String> classPathParts,
                              List<String> restrictSourceToPackages,
                              String alternativeJREDirectory,
                              Charset sourceEncoding) {
        this.sources = sources;
        this.classPathParts = classPathParts;
        this.restrictSourceToPackages = restrictSourceToPackages;
        this.alternativeJREDirectory = alternativeJREDirectory;
        this.sourceEncoding = sourceEncoding;
    }

    @Override
    public String toString() {
        return "sources: " + sources +
                "\nsourceEncoding: " + sourceEncoding.displayName() +
                "\nclassPathParts: " + classPathParts +
                "\nrestrictSourceToPackages: " + restrictSourceToPackages +
                "\nalternativeJREDirectory: " + (alternativeJREDirectory == null ? "<default>" : alternativeJREDirectory) +
                '\n';
    }

    public static InputConfiguration fromProperties(Map<String, String> analyserProperties) {
        Builder builder = new Builder();
        setStringProperty(analyserProperties, JRE, builder::setAlternativeJREDirectory);
        setStringProperty(analyserProperties, SOURCE_ENCODING, builder::setSourceEncoding);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, SOURCE, builder::addSources);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, CLASSPATH, builder::addClassPath);
        setSplitStringProperty(analyserProperties, COMMA, SOURCE_PACKAGES, builder::addRestrictSourceToPackages);
        return builder.build();
    }

    @Container
    public static class Builder {
        private final List<String> sourceDirs = new ArrayList<>();
        private final List<String> classPathParts = new ArrayList<>();
        private final List<String> restrictSourceToPackages = new ArrayList<>();
        private String alternativeJREDirectory;
        private String sourceEncoding;

        public InputConfiguration build() {
            if (classPathParts.isEmpty()) {
                classPathParts.addAll(Arrays.asList(DEFAULT_CLASSPATH));
            }
            if (sourceDirs.isEmpty()) {
                sourceDirs.addAll(Arrays.asList(DEFAULT_SOURCE_DIRS.split(PATH_SEPARATOR)));
            }
            Charset sourceCharset = sourceEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(sourceEncoding);
            return new InputConfiguration(ImmutableList.copyOf(sourceDirs),
                    ImmutableList.copyOf(classPathParts),
                    ImmutableList.copyOf(restrictSourceToPackages),
                    alternativeJREDirectory,
                    sourceCharset
            );
        }

        @Fluent
        public Builder addSources(String... sources) {
            sourceDirs.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addClassPath(String... sources) {
            classPathParts.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder setAlternativeJREDirectory(String alternativeJREDirectory) {
            this.alternativeJREDirectory = alternativeJREDirectory;
            return this;
        }

        @Fluent
        public Builder setSourceEncoding(String sourceEncoding) {
            this.sourceEncoding = sourceEncoding;
            return this;
        }

        @Fluent
        public Builder addRestrictSourceToPackages(String... packages) {
            restrictSourceToPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
