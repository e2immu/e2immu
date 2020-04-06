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

import ch.qos.logback.classic.Level;
import org.e2immu.annotation.NotModified;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestParse.class);
    public static final String SRC_MAIN_JAVA_ORG_E2IMMU_ANALYSER = "src/main/java/org/e2immu/analyser/";

    @BeforeClass
    public static void beforeClass() {

        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, LAMBDA,
                METHOD_CALL, VARIABLE_PROPERTIES, LINKED_VARIABLES, INDEPENDENT, MODIFY_CONTENT,
                E2IMMUTABLE,
                CONTAINER, VALUE_CLASS, SIDE_EFFECT, UTILITY_CLASS, CONTEXT_ANNOTATIONS, PURE_ANNOTATIONS,
                NULL_NOT_ALLOWED);
    }

    @Test
    public void testDependencyGraph() throws IOException {
        goTest("util/DependencyGraph.java");
    }

    @Test
    public void testEither() throws IOException {
        goTest("util/Either.java");
    }

    @Test
    public void testFirstThen() throws IOException {
        goTest("util/FirstThen.java");
    }

    @Test
    public void testLazy() throws IOException {
        goTest("util/Lazy.java");
    }

    @Test
    public void testPair() throws IOException {
        goTest("util/Pair.java");
    }

    @Test
    public void testSetOnce() throws IOException {
        goTest("util/SetOnce.java");
    }

    @Test
    public void testSetOnceMap() throws IOException {
        goTest("util/SetOnceMap.java");
    }

    @Test
    public void testStringUtil() throws IOException {
        goTest("util/StringUtil.java");
    }

    @Test
    public void testTrie() throws IOException {
        goTest("util/Trie.java");
    }

    @NotModified
    private void goTest(String fileName) throws IOException {
        Parser parser = new Parser();
        List<SortedType> types = parser.parseJavaFiles(
                new File(SRC_MAIN_JAVA_ORG_E2IMMU_ANALYSER + "util/Freezable.java"),
                new File(SRC_MAIN_JAVA_ORG_E2IMMU_ANALYSER + fileName));
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
    }

}
