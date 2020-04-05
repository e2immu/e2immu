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

package org.e2immu.analyser.util;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestResources.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.RESOURCES);
    }

    @Test
    public void testViaClassPath() throws IOException {
        Resources classPath = new Resources();
        classPath.addJarFromClassPath("org/e2immu/annotation");
        List<String[]> expansions = classPath.expandPaths("org.e2immu.annotation");
        AtomicInteger counter = new AtomicInteger();
        expansions.forEach(ss -> {
            counter.getAndIncrement();
            LOGGER.info("expand to {}", Arrays.toString(ss));
        });
        Assert.assertTrue(20 < counter.get());
    }

    @Test
    public void testLoadBytesViaClassPath() throws IOException {
        Resources resources = new Resources();
        resources.addJarFromClassPath("org/e2immu/annotation");
        byte[] bytes = resources.loadBytes("org/e2immu/annotation/AnnotationType.class");
        Assert.assertNotNull(bytes);
        Assert.assertTrue(bytes.length > 10);
    }

    @Test
    public void testSourceViaClassPath() throws IOException {
        Resources classPath = new Resources();
        classPath.addJarFromClassPath("org/e2immu/annotation");
        List<URL> expansions = classPath.expandURLs(".java");
        int counter = 0;
        for (URL url : expansions) {
            if (0 == counter++) {
                // let's see if we can read the file
                InputStreamReader isr = new InputStreamReader(url.openStream());
                String code = IOUtils.toString(isr);
                Assert.assertTrue("Code is " + code, code.contains("@Retention(RetentionPolicy.CLASS)"));
                LOGGER.info("Successfully read source!");
            }
            LOGGER.info("expand to {}", url);
        }
        Assert.assertTrue("Counter is " + counter, 20 < counter);
    }

    @Test
    public void testViaJar() throws IOException {
        Resources classPath = new Resources();
        classPath.addJar(new URL("jar:file:build/libs/analyser.jar!/"));
        List<String[]> expansions = classPath.expandPaths("org.e2immu.analyser.model");
        AtomicInteger counter = new AtomicInteger();
        expansions.forEach(ss -> {
            counter.getAndIncrement();
            LOGGER.info("expand to {}", Arrays.toString(ss));
        });
        Assert.assertTrue(50 < counter.get());
    }

    @Test
    public void testViaPath() throws IOException {
        Resources classPath = new Resources();
        classPath.addDirectoryFromFileSystem(new File("build/classes/java/main/"));
        List<String[]> expansions = classPath.expandPaths("org.e2immu.analyser.model");
        AtomicInteger counter = new AtomicInteger();
        expansions.forEach(ss -> {
            counter.getAndIncrement();
            LOGGER.info("expand to {}", Arrays.toString(ss));
        });
        Assert.assertTrue("Counter is " + counter, 50 < counter.get());
    }

    @Test
    public void testJmod() throws IOException {
        Resources classPath = new Resources();
        AtomicBoolean found = new AtomicBoolean();
        String url = "jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/";
        LOGGER.info("Adding {}", url);
        classPath.addJmod(new URL(url));
        classPath.expandPaths("java.util", ".class", (s, urls) -> {
            if ("List.class".equals(s[0]) && s.length == 1) {
                found.set(true);
            }
        });
        Assert.assertTrue(found.get());
    }
}
