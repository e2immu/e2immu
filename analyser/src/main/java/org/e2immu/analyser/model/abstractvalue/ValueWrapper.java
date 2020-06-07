package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;

public interface ValueWrapper {

    // there can never be two wrappers of the same class next to each other (meaning, on exactly the same Value object)

    int WRAPPER_ORDER_PROPERTY = 1;
    int WRAPPER_ORDER_NEGATED = 2;
    int WRAPPER_ORDER_CONSTRAINED_NUMERIC_VALUE = 3;

    Value getValue();

    int wrapperOrder();

}
