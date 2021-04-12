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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@E2Immutable
public record AnnotatedAPIConfiguration(boolean reportWarnings,
                                        WriteMode writeAnnotatedAPIs,
                                        List<String> writeAnnotatedAPIsPackages,
                                        String writeAnnotatedAPIsDir,
                                        String destinationPackage) {

    public static final String DEFAULT_DESTINATION_PACKAGE = "annotatedapi";
    public static final String DEFAULT_DESTINATION_DIRECTORY = "build/annotatedAPIs";

    public enum WriteMode {
        DO_NOT_WRITE,  // do not write annotated apis
        INSPECTED, // use the byte code inspector
        ANALYSED, // use the analyser to do better than the inspector, pre-populate
        USAGE, // limited to the methods, types and fields used in the parsed source code.
    }

    @Container
    public static class Builder {
        private boolean reportWarnings;
        private WriteMode writeAnnotatedAPIs;
        private final List<String> writeAnnotatedAPIsPackages = new ArrayList<>();
        private String writeAnnotatedAPIsDir;
        private String destinationPackage;

        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfiguration(reportWarnings,
                    writeAnnotatedAPIs == null ? WriteMode.DO_NOT_WRITE : writeAnnotatedAPIs,
                    List.copyOf(writeAnnotatedAPIsPackages),
                    writeAnnotatedAPIsDir == null || writeAnnotatedAPIsDir.isBlank() ?
                            DEFAULT_DESTINATION_DIRECTORY : writeAnnotatedAPIsDir,
                    destinationPackage == null || destinationPackage.isBlank() ?
                            DEFAULT_DESTINATION_PACKAGE : destinationPackage.trim());
        }

        @Fluent
        public Builder setWriteAnnotatedAPIsDir(String writeAnnotatedAPIsDir) {
            this.writeAnnotatedAPIsDir = writeAnnotatedAPIsDir;
            return this;
        }

        @Fluent
        public Builder setDestinationPackage(String destinationPackage) {
            this.destinationPackage = destinationPackage;
            return this;
        }

        @Fluent
        public Builder setAnnotatedAPIs(WriteMode writeAnnotatedAPIs) {
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
