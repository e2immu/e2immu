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

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.cli.Main;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.cli.Main.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class TestConfiguration {

    @BeforeClass
    public static void beforeClass() {
        Logger.activate(CONFIGURATION);
    }

    @Test
    public void test() {
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.util")
                        .addClassPath("build/resources/main/annotatedAPIs")
                        .addClassPath("build/resources/main/annotations/jdkAnnotations")
                        .addClassPath("jmods/java.base.jmod").build())
                .setUploadConfiguration(new UploadConfiguration.Builder()
                        .setUpload(true).build())
                .addDebugLogTargets(BYTECODE_INSPECTOR.toString())
                .addDebugLogTargets(INSPECT.toString() + "," + ANALYSER.toString())
                .build();
        log(CONFIGURATION, "Config1:\n{}", configuration);

        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        Map<String, String> properties = builder
                .put(DEBUG, BYTECODE_INSPECTOR.toString() + "," + INSPECT.toString() + "," + ANALYSER.toString())
                .put(SOURCE, "src/main/java")
                .put(SOURCE_PACKAGES, "org.e2immu.analyser.util")
                .put(CLASSPATH, "build/resources/main/annotatedAPIs" + PATH_SEPARATOR + "build/resources/main/annotations/jdkAnnotations" + PATH_SEPARATOR + "jmods/java.base.jmod")
                .put(Main.UPLOAD, "true")
                .build();
        Configuration configuration2 = Configuration.fromProperties(properties);
        log(CONFIGURATION, "Config2:\n{}", configuration2);
        Assert.assertEquals(configuration.toString(), configuration2.toString());
        Assert.assertEquals(configuration, configuration2);
    }
}
