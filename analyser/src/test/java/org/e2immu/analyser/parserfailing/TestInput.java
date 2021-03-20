/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.parser.Input;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInput.class);

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.CONFIGURATION);
    }

    @Test
    public void testInput() throws IOException, URISyntaxException {
        Input input = Input.create(new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addAnnotatedAPISources("../annotatedAPIs/src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.util")
                        .addClassPath("jmods/java.base.jmod")
                        .build())
                .build());
        assertTrue(15 <= input.sourceURLs().size(), "Have at least 15 classes in util package");

        for (URL url : input.sourceURLs().values()) {
            LOGGER.info("Have source URL {}", url);
            File file = new File(url.toURI());
            assertTrue(file.canRead());
        }

        assertTrue(3 <= input.annotatedAPIs().size(), "Have at least 3 annotated API sources");

        for (URL url : input.annotatedAPIs().values()) {
            LOGGER.info("Have annotated API URL {}", url);
            File file = new File(url.toURI());
            assertTrue(file.canRead());
        }
    }

}
