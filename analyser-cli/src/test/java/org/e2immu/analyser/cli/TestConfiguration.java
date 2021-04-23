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

package org.e2immu.analyser.cli;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestConfiguration {

    @BeforeAll
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
                .addDebugLogTargets(INSPECTOR + "," + ANALYSER)
                .build();
        log(CONFIGURATION, "Config1:\n{}", configuration);

        Map<String, String> properties = new HashMap<>();
        properties.put(Main.DEBUG, BYTECODE_INSPECTOR + "," + INSPECTOR + "," + ANALYSER);
        properties.put(Main.SOURCE, "src/main/java");
        properties.put(Main.SOURCE_PACKAGES, "org.e2immu.analyser.util");
        properties.put(Main.CLASSPATH, "build/resources/main/annotatedAPIs" + Main.PATH_SEPARATOR +
                "build/resources/main/annotations/jdkAnnotations" + Main.PATH_SEPARATOR +
                "jmods/java.base.jmod");
        properties.put(Main.UPLOAD, "true");
        Configuration configuration2 = Main.fromProperties(properties);
        log(CONFIGURATION, "Config2:\n{}", configuration2);
        assertEquals(configuration.toString(), configuration2.toString());
        assertEquals(configuration, configuration2);
    }
}
