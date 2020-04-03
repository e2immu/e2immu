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

import java.util.*;

import static org.e2immu.analyser.config.Configuration.setBooleanProperty;
import static org.e2immu.analyser.config.Configuration.setStringProperty;

@E2Immutable
public class AnnotationXmlConfiguration {

    // write a Xml
    public final boolean writeAnnotationXml;
    public final List<String> writeAnnotationXmlPackages;
    public final String writeAnnotationXmlDir;

    public AnnotationXmlConfiguration(
            boolean writeAnnotationXml,
            List<String> writeAnnotationXmlPackages,
            String writeAnnotationXmlDir) {
        this.writeAnnotationXml = writeAnnotationXml;
        this.writeAnnotationXmlPackages = writeAnnotationXmlPackages;
        this.writeAnnotationXmlDir = writeAnnotationXmlDir;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AnnotationXmlConfiguration.class.getSimpleName() + "[", "]")
                .add("writeAnnotationXml=" + writeAnnotationXml)
                .add("writeAnnotationXmlPackages=" + writeAnnotationXmlPackages)
                .add("writeAnnotationXmlDir='" + writeAnnotationXmlDir + "'")
                .toString();
    }

    public static AnnotationXmlConfiguration fromProperties(Map<String, String> analyserProperties) {
        Builder builder = new Builder();
        setBooleanProperty(analyserProperties, Main.WRITE_ANNOTATION_XML, builder::setAnnotationXml);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATION_XML_DIR, builder::setWriteAnnotationXmlDir);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATION_XML_PACKAGES, builder::addAnnotationXmlPackages);
        return builder.build();
    }

    @Container
    public static class Builder {

        private boolean writeAnnotationXml;
        private final List<String> writeAnnotationXmlPackages = new ArrayList<>();
        private String writeAnnotationXmlDir;

        public AnnotationXmlConfiguration build() {
            return new AnnotationXmlConfiguration(
                    writeAnnotationXml,
                    ImmutableList.copyOf(writeAnnotationXmlPackages),
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
        public Builder addAnnotationXmlPackages(String... packages) {
            writeAnnotationXmlPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
