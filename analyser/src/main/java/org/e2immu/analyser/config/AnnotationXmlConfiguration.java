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
public class AnnotationXmlConfiguration {

    // write a Xml
    public final boolean writeAnnotationXml;
    public final List<String> writeAnnotationXmlPackages;
    public final List<String> readAnnotationXmlPackages;
    public final String writeAnnotationXmlDir;

    public AnnotationXmlConfiguration(
            boolean writeAnnotationXml,
            List<String> writeAnnotationXmlPackages,
            List<String> readAnnotationXmlPackages,
            String writeAnnotationXmlDir) {
        this.writeAnnotationXml = writeAnnotationXml;
        this.writeAnnotationXmlPackages = writeAnnotationXmlPackages;
        this.writeAnnotationXmlDir = writeAnnotationXmlDir;
        this.readAnnotationXmlPackages = readAnnotationXmlPackages;
    }

    @Override
    public String toString() {
        return new StringJoiner("\n")
                .add("write annotationXml: " + writeAnnotationXml)
                .add("write annotationXml restrict to packages: " + writeAnnotationXmlPackages)
                .add("read annotationXml restrict to packages: " + readAnnotationXmlPackages)
                .add("write annotationXml directory: " + writeAnnotationXmlDir) + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotationXmlConfiguration that = (AnnotationXmlConfiguration) o;
        return writeAnnotationXml == that.writeAnnotationXml &&
                writeAnnotationXmlPackages.equals(that.writeAnnotationXmlPackages) &&
                Objects.equals(writeAnnotationXmlDir, that.writeAnnotationXmlDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(writeAnnotationXml, writeAnnotationXmlPackages, writeAnnotationXmlDir);
    }

    public boolean isReadAnnotationXmlPackages() {
        return readAnnotationXmlPackages.isEmpty() || !"none".equalsIgnoreCase(readAnnotationXmlPackages.get(0));
    }

    @Container
    public static class Builder {

        private boolean writeAnnotationXml;
        private final List<String> writeAnnotationXmlPackages = new ArrayList<>();
        private final List<String> readAnnotationXmlPackages = new ArrayList<>();
        private String writeAnnotationXmlDir;

        public AnnotationXmlConfiguration build() {
            return new AnnotationXmlConfiguration(
                    writeAnnotationXml,
                    List.copyOf(writeAnnotationXmlPackages),
                    List.copyOf(readAnnotationXmlPackages),
                    writeAnnotationXmlDir
            );
        }

        @Fluent
        public Builder setWriteAnnotationXmlDir(String writeAnnotationXmlDir) {
            this.writeAnnotationXmlDir = writeAnnotationXmlDir;
            return this;
        }

        @Fluent
        public Builder setAnnotationXml(boolean writeAnnotationXml) {
            this.writeAnnotationXml = writeAnnotationXml;
            return this;
        }

        @Fluent
        public Builder addAnnotationXmlWritePackages(String... packages) {
            writeAnnotationXmlPackages.addAll(Arrays.asList(packages));
            return this;
        }

        @Fluent
        public Builder addAnnotationXmlReadPackages(String... packages) {
            readAnnotationXmlPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
