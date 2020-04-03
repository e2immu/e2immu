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

package org.e2immu.analyser.parser;

import com.google.common.collect.ImmutableMap;

import static org.e2immu.analyser.cli.Main.*;

import org.e2immu.analyser.cli.Main;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.UploadConfiguration;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestParseUtilPackage {

    @Test
    public void test() throws IOException {
        Logger.activate(Logger.LogTarget.BYTECODE_INSPECTOR,
                Logger.LogTarget.INSPECT,
                Logger.LogTarget.ANALYSER);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.util")
                        .addClassPath("build/resources/main/annotatedAPIs")
                        .addClassPath("build/resources/main/annotations/jdkAnnotations")
                        .addClassPath("jmods/java.base.jmod").build())
                .setUploadConfiguration(new UploadConfiguration.Builder()
                        .setUpload(true).build())
                .build();
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        Assert.assertTrue(types.size() >= 15);
    }

    @Test
    public void testViaProperties() throws IOException {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        Map<String, String> properties = builder
                .put(DEBUG, BYTECODE_INSPECTOR.toString() + "," + INSPECT.toString() + "," + ANALYSER.toString())
                .put(SOURCE, "src/main/java")
                .put(RESTRICT_SOURCE, "org.e2immu.analyser.util")
                .put(CLASSPATH, "build/resources/main/annotatedAPIs" + PATH_SEPARATOR + "build/resources/main/annotations/jdkAnnotations" + PATH_SEPARATOR + "jmods/java.base.jmod")
                .put(Main.UPLOAD, "true")
                .build();
        Configuration configuration = Configuration.fromProperties(properties);
        Logger.activate(configuration.logTargets);
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        Assert.assertTrue(types.size() >= 15);
    }
}
