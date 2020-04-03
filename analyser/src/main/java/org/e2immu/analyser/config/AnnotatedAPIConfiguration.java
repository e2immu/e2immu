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
public class AnnotatedAPIConfiguration {

    // write AA
    public final boolean writeAnnotatedAPIs;
    public final List<String> writeAnnotatedAPIsPackages;
    public final String writeAnnotatedAPIsDir;

    public AnnotatedAPIConfiguration(boolean writeAnnotatedAPIs,
                                     List<String> writeAnnotatedAPIsPackages,
                                     String writeAnnotatedAPIsDir) {
        this.writeAnnotatedAPIs = writeAnnotatedAPIs;
        this.writeAnnotatedAPIsPackages = writeAnnotatedAPIsPackages;
        this.writeAnnotatedAPIsDir = writeAnnotatedAPIsDir;
    }

    public static AnnotatedAPIConfiguration fromProperties(Map<String, String> analyserProperties) {
        Builder builder = new Builder();
        setBooleanProperty(analyserProperties, Main.WRITE_ANNOTATED_API, builder::setAnnotatedAPIs);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATED_API_DIR, builder::setWriteAnnotatedAPIsDir);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATED_API_PACKAGES, builder::addAnnotatedAPIPackages);
        return builder.build();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AnnotatedAPIConfiguration.class.getSimpleName() + "[", "]")
                .add("writeAnnotatedAPIs=" + writeAnnotatedAPIs)
                .add("writeAnnotatedAPIsPackages=" + writeAnnotatedAPIsPackages)
                .add("writeAnnotatedAPIsDir='" + writeAnnotatedAPIsDir + "'")
                .toString();
    }

    @Container
    public static class Builder {

        private boolean writeAnnotatedAPIs;
        private final List<String> writeAnnotatedAPIsPackages = new ArrayList<>();
        private String writeAnnotatedAPIsDir;


        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfiguration(writeAnnotatedAPIs,
                    ImmutableList.copyOf(writeAnnotatedAPIsPackages),
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
        public Builder addAnnotatedAPIPackages(String... packages) {
            writeAnnotatedAPIsPackages.addAll(Arrays.asList(packages));
            return this;
        }
    }
}
