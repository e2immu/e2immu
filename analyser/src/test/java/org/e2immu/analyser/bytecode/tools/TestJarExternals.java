package org.e2immu.analyser.bytecode.tools;

import org.e2immu.analyser.util.Resources;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.analyser.PackedInt;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;

import static org.e2immu.analyser.bytecode.tools.ExtractTypesFromClassFile.STD;
import static org.junit.jupiter.api.Assertions.*;

public class TestJarExternals {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestJarExternals.class);

    @Test
    public void test() throws IOException {
        Resources resources = new Resources();
        int entries = resources.addJarFromClassPath("org/jgrapht/graph");
        assertTrue(entries > 0);
        ExtractTypesFromClassFile extractor = new ExtractTypesFromClassFile();
        extractor.go(resources);
        G<String> graph = extractor.build();
        assertEquals(569, graph.vertices().size());
        for (Map.Entry<V<String>, Map<V<String>, Long>> edge : graph.edges()) {
            for (Map.Entry<V<String>, Long> e2 : edge.getValue().entrySet()) {
                assertFalse(e2.getKey().t().startsWith("org.jgraph"));
                LOGGER.debug("{} --> {}, value {}", edge.getKey(), e2.getKey(),
                        PackedInt.nice((int) (long) e2.getValue()));
            }
        }
    }

    @Test
    public void test2a() {
        assertEquals("[org.apache.commons.pool.impl.CursorableLinkedList.ListIter]",
                ExtractTypesFromClassFile.extract("Lorg/apache/commons/pool/impl/CursorableLinkedList<TE;>.ListIter;").toString());
    }

    @Test
    public void test2b() {
        Matcher m = STD.matcher("[Ljava/lang/Object;");
        assertTrue(m.matches());
    }

    @Test
    public void test2c() {
        assertEquals("[java.util.List, java.util.Map, java.xx.String, java.yy.Double]",
                ExtractTypesFromClassFile.extract("Ljava/util/List<Ljava/util/Map<Ljava/xx/String;Ljava/yy/Double;>;>;").toString());
    }

    @Test
    public void test2d() {
        assertEquals("[org.apache.commons.pool.impl.GenericKeyedObjectPool.Latch]",
                ExtractTypesFromClassFile.extract("Lorg/apache/commons/pool/impl/GenericKeyedObjectPool<TK;TV;>.Latch<TLK;TLV;>;").toString());
    }
}
