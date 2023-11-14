package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.Map;

public class TypeDataImpl implements TypeData {
    private final TypeInspection.Builder typeInspectionBuilder;
    private final Map<String, MethodInspection.Builder> methodInspections = new HashMap<>();
    private final Map<FieldInfo, FieldInspection.Builder> fieldInspections = new HashMap<>();
    private volatile InspectionState inspectionState;

    public TypeDataImpl(TypeInspection.Builder typeInspectionBuilder, InspectionState inspectionState) {
        this.typeInspectionBuilder = typeInspectionBuilder;
        this.inspectionState = inspectionState;
    }

    @Override
    public void setInspectionState(InspectionState inspectionState) {
        this.inspectionState = inspectionState;
    }

    @Override
    public InspectionState getInspectionState() {
        return inspectionState;
    }

    @Override
    public TypeInspection.Builder getTypeInspectionBuilder() {
        return typeInspectionBuilder;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeDataImpl td && td.typeInspectionBuilder.typeInfo().equals(typeInspectionBuilder.typeInfo());
    }

    @Override
    public int hashCode() {
        return typeInspectionBuilder.typeInfo().fullyQualifiedName.hashCode();
    }

    @Override
    public Iterable<Map.Entry<String, MethodInspection.Builder>> methodInspectionBuilders() {
        return methodInspections.entrySet();
    }

    @Override
    public Iterable<Map.Entry<FieldInfo, FieldInspection.Builder>> fieldInspectionBuilders() {
        return fieldInspections.entrySet();
    }

    @Override
    public FieldInspection.Builder fieldInspectionsPut(FieldInfo fieldInfo, FieldInspection.Builder builder) {
        return fieldInspections.put(fieldInfo, builder);
    }

    @Override
    public MethodInspection.Builder methodInspectionsPut(String distinguishingName, MethodInspection.Builder builder) {
        return methodInspections.put(distinguishingName, builder);
    }

    @Override
    public FieldInspection fieldInspectionsGet(FieldInfo fieldInfo) {
        return fieldInspections.get(fieldInfo);
    }

    @Override
    public MethodInspection methodInspectionsGet(String distinguishingName) {
        return methodInspections.get(distinguishingName);
    }
}
