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
