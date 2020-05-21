package org.e2immu.analyser.model;

import java.util.Objects;

public class Location {

    public final TypeInfo typeInfo;
    public final MethodInfo methodInfo;
    public final FieldInfo fieldInfo;
    public final String statementId;
    public final ParameterInfo parameterInfo;

    public Location(FieldInfo fieldInfo) {
        this.typeInfo = fieldInfo.owner;
        this.fieldInfo = fieldInfo;
        this.methodInfo = null;
        this.statementId = null;
        this.parameterInfo = null;
    }

    public Location(TypeInfo typeInfo) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.fieldInfo = null;
        this.methodInfo = null;
        this.statementId = null;
        this.parameterInfo = null;
    }

    public Location(MethodInfo methodInfo) {
        this(methodInfo, null);
    }

    public Location(MethodInfo methodInfo, String statementId) {
        this.typeInfo = methodInfo.typeInfo;
        this.fieldInfo = null;
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.statementId = statementId;
        this.parameterInfo = null;
    }

    public Location(ParameterInfo parameterInfo) {
        this.parameterInfo = parameterInfo;
        this.methodInfo = parameterInfo.parameterInspection.get().owner;
        this.typeInfo = this.methodInfo.typeInfo;
        this.statementId = null;
        this.fieldInfo = null;
    }

    @Override
    public String toString() {
        if (statementId != null) {
            return "method " + methodInfo.distinguishingName() + ", statement " + statementId;
        }
        if (parameterInfo != null) {
            return "parameter" + parameterInfo.name + " of " + methodInfo.distinguishingName();
        }
        if (methodInfo != null) {
            return "method " + methodInfo.distinguishingName();
        }
        if (fieldInfo != null) {
            return "field " + fieldInfo.fullyQualifiedName();
        }
        return "type " + typeInfo.fullyQualifiedName;
    }
}
