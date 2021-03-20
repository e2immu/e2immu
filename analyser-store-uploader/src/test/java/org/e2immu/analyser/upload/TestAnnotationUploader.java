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

package org.e2immu.analyser.upload;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAnnotationUploader {

    public static final String BASICS_0 = "org.e2immu.analyser.upload.Basics_0";

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE);
    }

    @Test
    public void test() throws IOException {
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages(BASICS_0)
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/core")
                        .build())
                .build();
        Parser parser = new Parser(configuration);
        parser.run();
        TypeContext typeContext = parser.getTypeContext();

        TypeInfo basics = typeContext.typeMapBuilder.get(BASICS_0);
        UpgradableBooleanMap<TypeInfo> typesReferredTo = basics.typesReferenced();
        assertTrue(typesReferredTo.get(typeContext.getPrimitives().stringTypeInfo));

        AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration,
                parser.getTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
        Map<String, String> map = annotationUploader.createMap(Set.of(basics));
        map.forEach((k, v) -> System.out.println(k + " --> " + v));

        assertEquals("e2container-mt", map.get("org.e2immu.analyser.testexample.Basics_0.getExplicitlyFinal() java.lang.String"));
    }
}
