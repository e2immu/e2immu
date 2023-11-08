package org.e2immu.analyser.resolver.testexample;

import java.util.ArrayList;
import java.util.List;

public class MethodCall_62 {

    interface Expression {
    }

    static Expression or(Expression[] array) {
        return array[0];
    }

    List<Expression> method(Expression[] array) {
        List<Expression> ands = new ArrayList<>();
        ands.add(or(array));
        return ands;
    }
}
