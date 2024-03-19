package org.e2immu.analyser.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTypeName {

    @Test
    public void test() {
        TypeName typeName1 = new TypeName("Bar2", "com.foo.Bar.Bar2",
                "Bar.Bar2", TypeName.Required.QUALIFIED_FROM_PRIMARY_TYPE);
        assertEquals("Bar.Bar2", typeName1.minimal());
        TypeName typeName2 = new TypeName("Bar2", "com.foo.Bar.Bar2",
                "Bar.Bar2", TypeName.Required.FQN);
        assertEquals("com.foo.Bar.Bar2", typeName2.minimal());
        TypeName typeName3 = new TypeName("Bar2", "com.foo.Bar.Bar2",
                "Bar.Bar2", TypeName.Required.DOLLARIZED_FQN);
        assertEquals("com.foo.Bar$Bar2", typeName3.minimal());
    }
}
