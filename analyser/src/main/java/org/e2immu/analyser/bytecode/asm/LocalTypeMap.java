package org.e2immu.analyser.bytecode.asm;

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

    // null if not present
    TypeMap.InspectionAndState typeInspectionSituation(String fqn);

    /*
    up to a TRIGGER_BYTE_CODE_INSPECTION stage, or, when start is true,
    actual loading
     */
    @Modified
    TypeInspection getOrCreate(String fqn, boolean start);

    /*
    same as the string version, but here we already know the enclosure relation
     */
    @Modified
    TypeInspection getOrCreate(TypeInfo subType);

    List<TypeMap.InspectionAndState> loaded();
    /*
     Call from My*Visitor back to ByteCodeInspector, as part of a `inspectFromPath(Source)` call.
     */

    // do actual byte code inspection
    @Modified
    TypeInspection inspectFromPath(Source name, TypeContext typeContext, boolean start);

    /*
    Non-modifying; query classpath. Delegates to classPath.fqnToPath
     */
    Source fqnToPath(String fullyQualifiedName);

    void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldInspectionBuilder);

    void registerMethodInspection(MethodInspection.Builder methodInspectionBuilder);
}
