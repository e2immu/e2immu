package org.e2immu.analyser.model;

public interface InfoObject {
    String niceClassName();

    String fullyQualifiedName();

    Identifier getIdentifier();

    TypeInfo getTypeInfo();

    TypeInfo primaryType();

    MethodInfo getMethodInfo();

    String name();

    Location newLocation();
}
