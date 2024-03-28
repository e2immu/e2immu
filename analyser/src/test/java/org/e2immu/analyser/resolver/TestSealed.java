package org.e2immu.analyser.resolver;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.model.TypeModifier;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.Sealed_0;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSealed extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Sealed_0.class);
        TypeInfo sealed0 = typeMap.get(Sealed_0.class);
        TypeInspection typeInspection = sealed0.typeInspection.get();
        assertTrue(typeInspection.modifiers().contains(TypeModifier.SEALED));
        List<TypeInfo> subTypes = typeInspection.subTypes();
        assertTrue(typeInspection.isSealed());
        assertEquals(subTypes, typeInspection.permittedWhenSealed());
        TypeInfo sub1 = subTypes.get(0);
        assertTrue(sub1.typeInspection.get().modifiers().contains(TypeModifier.FINAL));
    }
}
