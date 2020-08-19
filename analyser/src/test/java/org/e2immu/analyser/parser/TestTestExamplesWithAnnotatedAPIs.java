
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
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.InputConfiguration;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestTestExamplesWithAnnotatedAPIs extends CommonTestRunner {
    public TestTestExamplesWithAnnotatedAPIs() {
        super(true);
    }

    @Test
    public void testAnnotationsOnLambdas() throws IOException {
        testClass("AnnotationsOnLambdas", 0);
    }

    @Test
    public void testCyclicReferences() throws IOException {
        testClass("CyclicReferences", 0);
    }

    @Test
    public void testStaticImports() throws IOException {
        testClass("StaticImports", 0);
    }

    @Test
    public void testStaticSideEffectsOnlyChecks() throws IOException {
        testClass("StaticSideEffectsOnlyChecks", 0);
    }

    @Test
    public void testSubTypes() throws IOException {
        testClass("SubTypes", 2);
    }

}
