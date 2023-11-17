package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.bytecode.TypeData;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Source;
import org.e2immu.annotation.Modified;

import java.util.List;

/*
In the local type map, types are either
 */
public interface LocalTypeMap extends InspectionProvider {

    /*
    now = directly
    trigger = leave in TRIGGER_BYTE_CODE state; if never visited, it'll not be loaded
    queue = ensure that it gets loaded before building the type map
     */
    enum LoadMode {NOW, TRIGGER, QUEUE}

    // null if not present
    TypeMap.InspectionAndState typeInspectionSituation(String fqn);

    /*
    up to a TRIGGER_BYTE_CODE_INSPECTION stage, or, when start is true,
    actual loading
     */
    @Modified
    TypeInspection getOrCreate(String fqn, LoadMode loadMode);

    /*
    same as the string version, but here we already know the enclosure relation
     */
    @Modified
    TypeInspection getOrCreate(TypeInfo subType);

    List<TypeData> loaded();
    /*
     Call from My*Visitor back to ByteCodeInspector, as part of a `inspectFromPath(Source)` call.
     */

    // do actual byte code inspection
    @Modified
    TypeInspection inspectFromPath(Source name, TypeContext typeContext, LoadMode loadMode);

    void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldInspectionBuilder);

    void registerMethodInspection(MethodInspection.Builder methodInspectionBuilder);
}
