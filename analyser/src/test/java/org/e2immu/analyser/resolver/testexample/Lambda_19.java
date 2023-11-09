package org.e2immu.analyser.resolver.testexample;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Lambda_19 {

    boolean method(List<String> data) {
        return data.stream().allMatch(new HashSet<>()::add);
    }

    @Test
    public void test() {
        assertTrue(method(List.of("a", "b")));
        assertFalse(method(List.of("a", "a")));
    }
}
