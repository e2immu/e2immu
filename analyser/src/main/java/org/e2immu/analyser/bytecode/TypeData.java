package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeMap;

import java.util.Map;

public interface TypeData {
    InspectionState getInspectionState();

    void setInspectionState(InspectionState inspectionState);

    TypeInspection.Builder getTypeInspectionBuilder();

    Iterable<Map.Entry<String, MethodInspection.Builder>> methodInspectionBuilders();

    Iterable<Map.Entry<FieldInfo, FieldInspection.Builder>> fieldInspectionBuilders();

    FieldInspection.Builder fieldInspectionsPut(FieldInfo fieldInfo, FieldInspection.Builder builder);

    MethodInspection.Builder methodInspectionsPut(String distinguishingName, MethodInspection.Builder builder);

    FieldInspection fieldInspectionsGet(FieldInfo fieldInfo);

    MethodInspection methodInspectionsGet(String distinguishingName);

    default TypeMap.InspectionAndState toInspectionAndState() {
        return new TypeMap.InspectionAndState(getTypeInspectionBuilder(), getInspectionState());
    }
}
