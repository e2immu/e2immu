package org.e2immu.analyser.shallow;

import org.e2immu.graph.analyser.PackedInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPackedInt {

    @Test
    public void test1() {
        int f1 = PackedInt.FIELD.of(2);
        assertEquals("F:2", PackedInt.nice(f1));
        int f2 = PackedInt.FIELD.of(4);
        assertEquals("F:4", PackedInt.nice(f2));
        int f3 = PackedInt.sum(f1, f2);
        assertEquals("F:6", PackedInt.nice(f3));
        int h1 = PackedInt.HIERARCHY.of(1);
        int s1 = PackedInt.sum(h1, f3);
        assertEquals("H:1 F:6", PackedInt.nice(s1));

        int a1 = PackedInt.STATIC_METHOD_CALL_OR_ANNOTATION.of(20);
        int a2 = PackedInt.STATIC_METHOD_CALL_OR_ANNOTATION.of(200);
        int s2 = PackedInt.sum(a1, a2);
        assertEquals("A:31", PackedInt.nice(s2));
        int s3 = PackedInt.sum(s2, s1);
        assertEquals("H:1 F:6 A:31", PackedInt.nice(s3));
    }
}
