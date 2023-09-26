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

package org.e2immu.analyser.util;

import org.e2immu.analyser.bytecode.TestByteCodeInspector;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestResources.class);

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
        assertTrue(20 < counter.get());
    }

    @Test
    public void testLoadBytesViaClassPath() throws IOException {
        Resources resources = new Resources();
        resources.addJarFromClassPath("org/e2immu/annotation");
        byte[] bytes = resources.loadBytes("org/e2immu/annotation/Final.class");
        assertNotNull(bytes);
        assertTrue(bytes.length > 10);
    }

    @Test
    public void testSourceViaClassPath() throws IOException {
        Resources classPath = new Resources();
        classPath.addJarFromClassPath("org/e2immu/annotation");
        List<URI> expansions = classPath.expandURLs(".java");
        int counter = 0;
        for (URI uri : expansions) {
            if (0 == counter++) {
                // let's see if we can read the file
                InputStreamReader isr = new InputStreamReader(uri.toURL().openStream());
                StringWriter sw = new StringWriter(50_000);
                isr.transferTo(sw);
                String code = sw.toString();
                assertTrue(code.contains("@Retention(RetentionPolicy.CLASS)"));
                LOGGER.info("Successfully read source!");
            }
            LOGGER.info("expand to {}", uri);
        }
        assertTrue(20 < counter);
    }

    @Test
    public void testViaJar() throws IOException {
        Resources classPath = new Resources();
        classPath.addJar(new URL("jar:file:build/libs/" + TestByteCodeInspector.determineAnalyserJarName() + "!/"));
        List<String[]> expansions = classPath.expandPaths("org.e2immu.analyser.model");
        AtomicInteger counter = new AtomicInteger();
        expansions.forEach(ss -> {
            counter.getAndIncrement();
            LOGGER.info("expand to {}", Arrays.toString(ss));
        });
        assertTrue(50 < counter.get());
    }

    @Test
    public void testViaPath() {
        Resources classPath = new Resources();
        classPath.addDirectoryFromFileSystem(new File("build/classes/java/main/"));
        List<String[]> expansions = classPath.expandPaths("org.e2immu.analyser.model");
        AtomicInteger counter = new AtomicInteger();
        expansions.forEach(ss -> {
            counter.getAndIncrement();
            LOGGER.info("expand to {}", Arrays.toString(ss));
        });
        assertTrue(50 < counter.get());
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
        assertTrue(found.get());
    }
}
