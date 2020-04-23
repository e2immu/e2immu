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
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.annotation.NotModified;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestParse {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestParse.class);

    @BeforeClass
    public static void beforeClass() {

        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, LAMBDA,
                METHOD_CALL, VARIABLE_PROPERTIES, LINKED_VARIABLES, INDEPENDENT, MODIFY_CONTENT,
                E2IMMUTABLE,
                CONTAINER, E1IMMUTABLE, SIDE_EFFECT, UTILITY_CLASS,
                NULL_NOT_ALLOWED);
    }

    @Test
    public void testDependencyGraph() throws IOException {
        goTest("DependencyGraph");
    }

    @Test
    public void testEither() throws IOException {
        goTest("Either");
    }

    @Test
    public void testFirstThen() throws IOException {
        goTest("FirstThen");
    }

    @Test
    public void testLazy() throws IOException {
        goTest("Lazy");
    }

    @Test
    public void testPair() throws IOException {
        goTest("Pair");
    }

    @Test
    public void testSetOnce() throws IOException {
        goTest("SetOnce");
    }

    @Test
    public void testSetOnceMap() throws IOException {
        goTest("SetOnceMap");
    }

    @Test
    public void testStringUtil() throws IOException {
        goTest("StringUtil");
    }

    @Test
    public void testTrie() throws IOException {
        goTest("Trie");
    }

    @NotModified
    private void goTest(String typeName) throws IOException {
        Parser parser = new Parser();
        String path = "src/main/java/org/e2immu/analyser/util/";
        TypeInfo freezable = parser.getTypeContext().typeStore.getOrCreate("org.e2immu.analyser.util.Freezable");
        TypeInfo other = parser.getTypeContext().typeStore.getOrCreate("org.e2immu.analyser.util." + typeName);
        URL urlFreezable = new File(path + "Freezable.java").toURI().toURL();
        URL urlOther = new File(path + typeName + ".java").toURI().toURL();

        List<SortedType> types = parser.parseJavaFiles(Map.of(freezable, urlFreezable, other, urlOther));
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
    }

}
