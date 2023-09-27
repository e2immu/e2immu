package org.e2immu.analyser.model;

public interface InfoObject {
    String niceClassName();

    String fullyQualifiedName();

    Identifier getIdentifier();

    TypeInfo getTypeInfo();

    // specifically used in StatementAnalyserImpl
    MethodInfo getMethodInfo();

    String name();

    Location newLocation();
}
