package org.e2immu.analyser.analyser;

public enum IsMyself {

    NO,
    PTA, // in same primary type analyzer,
    YES;

    public boolean toFalse(Property property) {
        return switch (property) {
            case CONTAINER, CONTEXT_CONTAINER -> this == PTA || this == YES;
            case IMMUTABLE, INDEPENDENT -> this == YES;
            default -> false;
        };
    }
}
