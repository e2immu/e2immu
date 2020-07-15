package org.e2immu.analyser.objectflow;

public enum Origin {
    NO_ORIGIN,
    RESULT_OF_METHOD,
    PARAMETER,
    INTERNAL,
    NEW_OBJECT_CREATION,
    LITERAL,

    // will be replaced by whatever is assigned to the field
    INITIAL_FIELD_FLOW,
}
