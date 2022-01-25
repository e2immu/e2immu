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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@E2Immutable
public record InputConfiguration(List<String> sources,
                                 List<String> classPathParts,
                                 List<String> restrictSourceToPackages,
                                 String alternativeJREDirectory, Charset sourceEncoding) {
    public static final String DEFAULT_SOURCE_DIRS = "src/main/java";
    public static final String[] DEFAULT_CLASSPATH = {"build/classes/java/main", "jmods/java.base.jmod", "jmods/java.xml.jmod"};
    public static final String[] CLASSPATH_WITHOUT_ANNOTATED_APIS = {"build/classes/java/main",
            "jmods/java.base.jmod", "jmods/java.xml.jmod", "src/main/resources/annotations/minimal"};

    @Override
    public String toString() {
        return "InputConfiguration:" +
                "\n    sources: " + sources +
                ",\n    sourceEncoding: " + sourceEncoding.displayName() +
                ",\n    classPathParts: " + classPathParts +
                ",\n    restrictSourceToPackages: " + restrictSourceToPackages +
                ",\n    alternativeJREDirectory: " + (alternativeJREDirectory == null ? "<default>" : alternativeJREDirectory);
    }

    @Container
    public static class Builder {
        private final List<String> sourceDirs = new ArrayList<>();
        private final List<String> classPathParts = new ArrayList<>();
        private final List<String> restrictSourceToPackages = new ArrayList<>();
        private String alternativeJREDirectory;
        private String sourceEncoding;

        public InputConfiguration build() {
            Charset sourceCharset = sourceEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(sourceEncoding);
            return new InputConfiguration(List.copyOf(sourceDirs),
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
