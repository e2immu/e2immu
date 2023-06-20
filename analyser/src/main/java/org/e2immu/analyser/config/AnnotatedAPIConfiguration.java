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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record AnnotatedAPIConfiguration(boolean disabled,
                                        // read
                                        List<String> annotatedAPISourceDirs,
                                        List<String> readAnnotatedAPIPackages,
                                        // writing fields
                                        boolean reportWarnings,
                                        WriteMode writeMode,
                                        List<String> writeAnnotatedAPIPackages,
                                        String writeAnnotatedAPIsDir,
                                        String destinationPackage) {

    public static final String DEFAULT_DESTINATION_PACKAGE = "annotatedapi";
    public static final String DEFAULT_DESTINATION_DIRECTORY = "build/annotatedAPIs";
    public static final String DO_NOT_READ_ANNOTATED_API = "do not read"; // is invalid anyway

    public enum WriteMode {
        DO_NOT_WRITE,  // do not write annotated apis
        INSPECTED, // use the byte code inspector
        ANALYSED, // use the analyser to do better than the inspector, pre-populate
        USAGE, // limited to the methods, types and fields used in the parsed source code.
    }

    @Container
    public static class Builder {
        private final List<String> readAnnotatedAPIPackages = new ArrayList<>();
        private final List<String> annotatedAPISourceDirs = new ArrayList<>();
        private boolean disabled;
        private WriteMode writeMode;
        private boolean reportWarnings;
        private final List<String> writeAnnotatedAPIsPackages = new ArrayList<>();
        private String writeAnnotatedAPIsDir;
        private String destinationPackage;

        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfiguration(
                    disabled,
                    // reading fields
                    List.copyOf(annotatedAPISourceDirs),
                    List.copyOf(readAnnotatedAPIPackages),
                    // writing fields
                    reportWarnings,
                    writeMode == null ? WriteMode.DO_NOT_WRITE : writeMode,
                    List.copyOf(writeAnnotatedAPIsPackages),
                    writeAnnotatedAPIsDir == null || writeAnnotatedAPIsDir.isBlank() ?
                            DEFAULT_DESTINATION_DIRECTORY : writeAnnotatedAPIsDir,
                    destinationPackage == null || destinationPackage.isBlank() ?
                            DEFAULT_DESTINATION_PACKAGE : destinationPackage.trim());
        }

        public Builder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
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
        public Builder setWriteMode(WriteMode writeMode) {
            this.writeMode = writeMode;
            return this;
        }

        @Fluent
        public Builder setReportWarnings(boolean reportWarnings) {
            this.reportWarnings = reportWarnings;
            return this;
        }

        @Fluent
        public Builder addWriteAnnotatedAPIPackages(String... packages) {
            writeAnnotatedAPIsPackages.addAll(Arrays.asList(packages));
            return this;
        }

        @Fluent
        public Builder addReadAnnotatedAPIPackages(String... packages) {
            readAnnotatedAPIPackages.addAll(Arrays.asList(packages));
            return this;
        }

        @Fluent
        public Builder addAnnotatedAPISourceDirs(String... packages) {
            annotatedAPISourceDirs.addAll(Arrays.asList(packages));
            return this;
        }
    }

    @Override
    public String toString() {
        return "AnnotatedAPIConfiguration:" +
                "\n    disabled=" + disabled +
                ",\n    annotatedAPISourceDirs=" + annotatedAPISourceDirs +
                ",\n    readAnnotatedAPIPackages=" + readAnnotatedAPIPackages +
                ",\n    reportWarnings=" + reportWarnings +
                ",\n    writeMode=" + writeMode +
                ",\n    writeAnnotatedAPIPackages=" + writeAnnotatedAPIPackages +
                ",\n    writeAnnotatedAPIsDir='" + writeAnnotatedAPIsDir + '\'' +
                ",\n    destinationPackage='" + destinationPackage + '\'';
    }
}
