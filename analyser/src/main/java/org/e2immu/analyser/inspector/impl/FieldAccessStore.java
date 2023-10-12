package org.e2immu.analyser.inspector.impl;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FieldAccessStore {

    private final Map<FieldInfo, Set<TypeInfo>> map = new HashMap<>();

    public void add(FieldInfo fieldInfo, TypeInfo currentType) {
        Set<TypeInfo> set = map.computeIfAbsent(fieldInfo, f -> new HashSet<>());
        set.add(currentType);
    }

    public boolean fieldsOfTypeAreAccessedOutsideTypeInsidePrimaryType(TypeInfo typeInfo,
                                                                       TypeInspection typeInspection) {
        TypeInfo primaryType = typeInfo.primaryType();
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            Set<TypeInfo> types = map.get(fieldInfo);
            if (types != null) {
                for (TypeInfo type : types) {
                    if (type != typeInfo && type.primaryType().equals(primaryType)) return true;
                }
            }
        }
        return false;
    }
}
