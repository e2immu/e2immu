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
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@E2Immutable
public class InputConfiguration {
    public static final String DEFAULT_SOURCE_DIRS = "src/main/java";
    public static final String[] DEFAULT_CLASSPATH = {"build/classes/java/main",
            "jmods/java.base.jmod"};
    public static final String[] CLASSPATH_WITHOUT_ANNOTATED_APIS = {"build/classes/java/main",
            "jmods/java.base.jmod", "src/main/resources/annotations/minimal"};

    // input options
    public final List<String> sources;
    public final List<String> sourcesAnnotatedAPIs;
    public final Charset sourceEncoding;
    public final List<String> classPathParts;
    public final List<String> restrictSourceToPackages;
    public final String alternativeJREDirectory;

    public InputConfiguration(List<String> sources,
                              List<String> sourcesAnnotatedAPIs,
                              List<String> classPathParts,
                              List<String> restrictSourceToPackages,
                              String alternativeJREDirectory,
                              Charset sourceEncoding) {
        this.sources = sources;
        this.classPathParts = classPathParts;
        this.restrictSourceToPackages = restrictSourceToPackages;
        this.alternativeJREDirectory = alternativeJREDirectory;
        this.sourceEncoding = sourceEncoding;
        this.sourcesAnnotatedAPIs = sourcesAnnotatedAPIs;
    }

    @Override
    public String toString() {
        return "sources: " + sources +
                "\nsourcesAnnotatedAPIs: " + sourcesAnnotatedAPIs +
                "\nsourceEncoding: " + sourceEncoding.displayName() +
                "\nclassPathParts: " + classPathParts +
                "\nrestrictSourceToPackages: " + restrictSourceToPackages +
                "\nalternativeJREDirectory: " + (alternativeJREDirectory == null ? "<default>" : alternativeJREDirectory) +
                '\n';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputConfiguration that = (InputConfiguration) o;
        return sources.equals(that.sources) &&
                sourcesAnnotatedAPIs.equals(that.sourcesAnnotatedAPIs) &&
                sourceEncoding.equals(that.sourceEncoding) &&
                classPathParts.equals(that.classPathParts) &&
                restrictSourceToPackages.equals(that.restrictSourceToPackages) &&
                Objects.equals(alternativeJREDirectory, that.alternativeJREDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sources, sourcesAnnotatedAPIs, sourceEncoding, classPathParts, restrictSourceToPackages, alternativeJREDirectory);
    }

    @Container
    public static class Builder {
        private final List<String> sourceDirs = new ArrayList<>();
        private final List<String> annotatedAPISourceDirs = new ArrayList<>();
        private final List<String> classPathParts = new ArrayList<>();
        private final List<String> restrictSourceToPackages = new ArrayList<>();
        private String alternativeJREDirectory;
        private String sourceEncoding;

        public InputConfiguration build() {
            if (classPathParts.isEmpty()) {
                classPathParts.addAll(Arrays.asList(DEFAULT_CLASSPATH));
            }
            if (sourceDirs.isEmpty()) {
                sourceDirs.addAll(Arrays.asList(DEFAULT_SOURCE_DIRS.split(Configuration.PATH_SEPARATOR)));
            }
            Charset sourceCharset = sourceEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(sourceEncoding);
            return new InputConfiguration(List.copyOf(sourceDirs),
                    List.copyOf(annotatedAPISourceDirs),
                    List.copyOf(classPathParts),
                    List.copyOf(restrictSourceToPackages),
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
        public Builder addAnnotatedAPISources(String... sources) {
            annotatedAPISourceDirs.addAll(Arrays.asList(sources));
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
