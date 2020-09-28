package org.e2immu.analyser.objectflow;

public enum Origin {
    NO_ORIGIN,

    RESULT_OF_METHOD,
    RESULT_OF_OPERATOR, // very similar to result of method
    FIELD_ACCESS, // access to field or array element without assigning to it

    PARAMETER,
    INTERNAL,
    NEW_OBJECT_CREATION,
    LITERAL,

    // will be replaced by whatever is assigned to the field
    INITIAL_FIELD_FLOW,

    // will be replaced by PARAMETER
    INITIAL_PARAMETER_FLOW,

    // will be replaced by whatever is assigned to the method
    INITIAL_METHOD_FLOW,
}
