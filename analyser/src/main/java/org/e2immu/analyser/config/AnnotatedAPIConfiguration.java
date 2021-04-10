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
                .add("write annotatedAPIs directory: '" + writeAnnotatedAPIsDir + "'") + "\n";
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
