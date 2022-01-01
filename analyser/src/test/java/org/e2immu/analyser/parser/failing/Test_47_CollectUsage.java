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

package org.e2immu.analyser.parser.failing;

import org.apache.commons.io.FileUtils;
import org.e2immu.analyser.annotatedapi.Composer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.testexample.CollectUsage_0;
import org.e2immu.analyser.usage.CollectUsages;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_47_CollectUsage extends CommonTestRunner {

    public Test_47_CollectUsage() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeContext typeContext = testClass("CollectUsage_0", 0, 0,
                new DebugConfiguration.Builder().build());
        CollectUsages collectUsages = new CollectUsages(List.of("java.lang"), Set.of("java.util"));
        List<TypeInfo> types = List.of(typeContext.getFullyQualified(CollectUsage_0.class));
        Set<WithInspectionAndAnalysis> set = collectUsages.collect(types);
        Set<TypeInfo> typesInSet = set.stream().filter(w -> w instanceof TypeInfo)
                .map(WithInspectionAndAnalysis::primaryType).filter(TypeInfo::isPrimaryType).collect(Collectors.toSet());

        String namesCsv = set.stream().map(WithInspectionAndAnalysis::fullyQualifiedName)
                .sorted().collect(Collectors.joining("\n")) + "\n";
        // we want
        // System, System.out, println
        // Random, Random constructor, Random.nextInt
        assertEquals("""
                java.lang.System
                java.lang.System.out
                java.util.Random
                java.util.Random.Random(long)
                java.util.Random.nextInt()
                """, namesCsv);

        File testDir = new File("build/test_47");
        FileUtils.deleteDirectory(testDir);
        Composer composer = new Composer(typeContext.typeMap, "test47",
                set::contains);
        Collection<TypeInfo> apiTypes = composer.compose(typesInSet);
        composer.write(apiTypes, testDir.getPath());

        assertTrue(testDir.isDirectory());
        File javaLang = new File(testDir, "test47/JavaLang.java");
        assertTrue(javaLang.canRead());
        File javaUtil = new File(testDir, "test47/JavaUtil.java");
        assertTrue(javaUtil.canRead());
    }

}
