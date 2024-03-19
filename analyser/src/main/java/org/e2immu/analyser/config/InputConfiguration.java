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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.e2immu.analyser.config.Configuration.NL_TAB;

public record InputConfiguration(List<String> sources,
                                 List<String> restrictSourceToPackages,
                                 List<String> testSources,
                                 List<String> restrictTestSourceToPackages,
                                 List<String> classPathParts,
                                 List<String> runtimeClassPathParts,
                                 List<String> testClassPathParts,
                                 List<String> testRuntimeClassPathParts,
                                 String alternativeJREDirectory,
                                 Charset sourceEncoding,
                                 List<String> dependencies) {
    public static final String DEFAULT_SOURCE_DIRS = "src/main/java";
    public static final String DEFAULT_TEST_SOURCE_DIRS = "src/test/java";

    public static final String[] DEFAULT_CLASSPATH = {"build/classes/java/main", "jmods/java.base.jmod",
            "jmods/java.xml.jmod", "jmods/java.net.http.jmod"};
    public static final String[] CLASSPATH_WITHOUT_ANNOTATED_APIS = {"build/classes/java/main",
            "jmods/java.base.jmod", "jmods/java.xml.jmod", "jmods/java.net.http.jmod",
            "src/main/resources/annotations/minimal"};

    @Override
    public String toString() {
        return "InputConfiguration:" +
                NL_TAB + "sources=" + sources +
                NL_TAB + "testSources=" + testSources +
                NL_TAB + "sourceEncoding=" + sourceEncoding.displayName() +
                NL_TAB + "restrictSourceToPackages=" + restrictSourceToPackages +
                NL_TAB + "restrictTestSourceToPackages=" + restrictTestSourceToPackages +
                NL_TAB + "classPathParts=" + classPathParts +
                NL_TAB + "alternativeJREDirectory=" + (alternativeJREDirectory == null ? "<default>" : alternativeJREDirectory);
    }

    @Container
    public static class Builder {
        private final List<String> sourceDirs = new ArrayList<>();
        private final List<String> testSourceDirs = new ArrayList<>();
        private final List<String> classPathParts = new ArrayList<>();
        private final List<String> runtimeClassPathParts = new ArrayList<>();
        private final List<String> testClassPathParts = new ArrayList<>();
        private final List<String> testRuntimeClassPathParts = new ArrayList<>();

        // result of dependency analysis: group:artifactId:version:configuration
        private final List<String> dependencies = new ArrayList<>();
        private final List<String> restrictSourceToPackages = new ArrayList<>();
        private final List<String> restrictTestSourceToPackages = new ArrayList<>();

        private String alternativeJREDirectory;
        private String sourceEncoding;

        public InputConfiguration build() {
            Charset sourceCharset = sourceEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(sourceEncoding);
            return new InputConfiguration(
                    List.copyOf(sourceDirs),
                    List.copyOf(restrictSourceToPackages),
                    List.copyOf(testSourceDirs),
                    List.copyOf(restrictTestSourceToPackages),
                    List.copyOf(classPathParts),
                    List.copyOf(runtimeClassPathParts),
                    List.copyOf(testClassPathParts),
                    List.copyOf(testRuntimeClassPathParts),
                    alternativeJREDirectory,
                    sourceCharset,
                    List.copyOf(dependencies)
            );
        }

        @Fluent
        public Builder addSources(String... sources) {
            sourceDirs.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addTestSources(String... sources) {
            testSourceDirs.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addClassPath(String... sources) {
            classPathParts.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addRuntimeClassPath(String... sources) {
            runtimeClassPathParts.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addTestClassPath(String... sources) {
            testClassPathParts.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addTestRuntimeClassPath(String... sources) {
            testRuntimeClassPathParts.addAll(Arrays.asList(sources));
            return this;
        }

        @Fluent
        public Builder addDependencies(String... deps) {
            dependencies.addAll(Arrays.asList(deps));
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

        @Fluent
        public Builder addRestrictTestSourceToPackages(String... packages) {
            restrictTestSourceToPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
