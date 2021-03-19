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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;

import java.util.*;

@E2Immutable
public class AnnotatedAPIConfiguration {

    public final boolean reportWarnings;

    // write AA
    public final boolean writeAnnotatedAPIs;
    public final List<String> writeAnnotatedAPIsPackages;
    public final String writeAnnotatedAPIsDir;

    public AnnotatedAPIConfiguration(
            boolean reportWarnings,
            boolean writeAnnotatedAPIs,
            List<String> writeAnnotatedAPIsPackages,
            String writeAnnotatedAPIsDir) {
        this.writeAnnotatedAPIs = writeAnnotatedAPIs;
        this.writeAnnotatedAPIsPackages = writeAnnotatedAPIsPackages;
        this.writeAnnotatedAPIsDir = writeAnnotatedAPIsDir;
        this.reportWarnings = reportWarnings;
    }

    @Override
    public String toString() {
        return new StringJoiner("\n")
                .add("write annotatedAPIs: " + writeAnnotatedAPIs)
                .add("write annotatedAPIs restrict to packages: " + writeAnnotatedAPIsPackages)
                .add("write annotatedAPIs directory: '" + writeAnnotatedAPIsDir + "'")
                .toString() + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotatedAPIConfiguration that = (AnnotatedAPIConfiguration) o;
        return writeAnnotatedAPIs == that.writeAnnotatedAPIs &&
                writeAnnotatedAPIsPackages.equals(that.writeAnnotatedAPIsPackages) &&
                Objects.equals(writeAnnotatedAPIsDir, that.writeAnnotatedAPIsDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(writeAnnotatedAPIs, writeAnnotatedAPIsPackages, writeAnnotatedAPIsDir);
    }

    @Container
    public static class Builder {
        private boolean reportWarnings;
        private boolean writeAnnotatedAPIs;
        private final List<String> writeAnnotatedAPIsPackages = new ArrayList<>();
        private String writeAnnotatedAPIsDir;


        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfiguration(reportWarnings,
                    writeAnnotatedAPIs,
                    List.copyOf(writeAnnotatedAPIsPackages),
                    writeAnnotatedAPIsDir);
        }

        @Fluent
        public Builder setWriteAnnotatedAPIsDir(String writeAnnotatedAPIsDir) {
            this.writeAnnotatedAPIsDir = writeAnnotatedAPIsDir;
            return this;
        }

        @Fluent
        public Builder setAnnotatedAPIs(boolean writeAnnotatedAPIs) {
            this.writeAnnotatedAPIs = writeAnnotatedAPIs;
            return this;
        }

        @Fluent
        public Builder setReportWarnings(boolean reportWarnings) {
            this.reportWarnings = reportWarnings;
            return this;
        }

        @Fluent
        public Builder addAnnotatedAPIPackages(String... packages) {
            writeAnnotatedAPIsPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
