package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.TypeInfo;

import java.util.*;

public class ImportMap {

    private final List<TypeInfo> staticAsterisk = new ArrayList<>();
    private final Map<String, TypeInfo> staticMemberToTypeInfo = new HashMap<>();
    private final Map<String, TypeInfo> typeMap = new HashMap<>();
    private final Set<TypeInfo> subtypeAsterisk = new LinkedHashSet<>();

    public void addStaticAsterisk(TypeInfo typeInfo) {
        staticAsterisk.add(typeInfo);
        subtypeAsterisk.add(typeInfo); // see InspectionGaps_13: we also add the subtypes
    }

    public void putStaticMemberToTypeInfo(String member, TypeInfo typeInfo) {
        staticMemberToTypeInfo.put(member, typeInfo);
    }

    public Iterable<? extends Map.Entry<String, TypeInfo>> staticMemberToTypeInfoEntrySet() {
        return staticMemberToTypeInfo.entrySet();
    }

    public Iterable<? extends TypeInfo> staticAsterisk() {
        return staticAsterisk;
    }

    public TypeInfo getStaticMemberToTypeInfo(String methodName) {
        return staticMemberToTypeInfo.get(methodName);
    }


    public void putTypeMap(String fullyQualifiedName, TypeInfo typeInfo, boolean highPriority) {
        if (highPriority || !typeMap.containsKey(fullyQualifiedName)) {
            typeMap.put(fullyQualifiedName, typeInfo);
        }
    }

    public TypeInfo isImported(String fullyQualifiedName) {
        TypeInfo typeInfo = typeMap.get(fullyQualifiedName);
        if (typeInfo == null) {
            int dot = fullyQualifiedName.lastIndexOf('.');
            if (dot > 0) {
                return isImported(fullyQualifiedName.substring(0, dot));
            }
        }
        return typeInfo;
    }

    public void addToSubtypeAsterisk(TypeInfo typeInfo) {
        subtypeAsterisk.add(typeInfo);
    }

    public boolean isSubtypeAsterisk(TypeInfo typeInfo) {
        return subtypeAsterisk.contains(typeInfo);
    }

    public Iterable<? extends TypeInfo> importAsterisk() {
        return new HashSet<>(subtypeAsterisk); // to avoid concurrent modification issues
    }
}
