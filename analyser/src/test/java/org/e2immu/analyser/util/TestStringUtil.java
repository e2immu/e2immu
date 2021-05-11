package org.e2immu.analyser.util;

import org.junit.jupiter.api.Test;

import static org.e2immu.analyser.util.StringUtil.inScopeOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStringUtil {

    @Test
    public void testInScopeOf() {
        assertTrue(inScopeOf("3.0.0", "3.0.0.0.0"));
        assertTrue(inScopeOf("3.0.0", "3.0.1"));
        assertTrue(inScopeOf("3.0.0", "3.0.1.1.0"));
        assertFalse(inScopeOf("3.0.0", "3.1.0"));
        assertFalse(inScopeOf("3.0.0", "2"));
        assertFalse(inScopeOf("3.0.0", "4"));

        assertTrue(inScopeOf("3", "3.0.0.0.0"));
        assertTrue(inScopeOf("3", "3.0.1"));
        assertTrue(inScopeOf("3", "3.0.1.1.0"));
        assertTrue(inScopeOf("3", "3.1.0"));
        assertFalse(inScopeOf("3", "2"));
        assertTrue(inScopeOf("3", "4"));
    }
}
