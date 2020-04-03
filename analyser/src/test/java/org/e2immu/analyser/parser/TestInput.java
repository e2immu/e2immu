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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class TestInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInput.class);

    @Test
    public void testInput() throws IOException, URISyntaxException {
        Input input = new Input(new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.util")
                        .addClassPath("build/resources/main/annotations/jdkAnnotations")
                        .addClassPath("build/resources/main/annotatedAPIs")
                        .addClassPath("build/classes/java/main")
                        .build())
                .build());
        Assert.assertTrue("Have at least 15 classes in util package", 15 <= input.getSourceURLs().size());
        Resources sourcePath = input.getSourcePath();
        for (URL url : sourcePath.expandURLs(".java")) {
            LOGGER.info("Have source URL {}", url);
            File file = new File(url.toURI());
            Assert.assertTrue(file.canRead());
        }
    }
}
