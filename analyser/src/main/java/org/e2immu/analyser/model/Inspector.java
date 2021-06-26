package org.e2immu.analyser.model;

/*
Information about the way inspection was carried out.
For now, the only relevant thing we can deduce is whether the type's statements were inspected, or not.

(So we could have put a simple boolean in TypeInspection, rather than this object.)

Note that "synthetic" is not quite equivalent to "BY_HAND". The byte code inspector can add synthetic
fields and methods; at the moment it cannot add synthetic types but it may do so later?
 */
public record Inspector(boolean statements) {

    public static final Inspector BYTE_CODE_INSPECTION = new Inspector(false);
    public static final Inspector JAVA_PARSER_INSPECTION = new Inspector(true);

    public static final Inspector BY_HAND = new Inspector(true);
    public static final Inspector BY_HAND_WITHOUT_STATEMENTS = new Inspector(false);

}
