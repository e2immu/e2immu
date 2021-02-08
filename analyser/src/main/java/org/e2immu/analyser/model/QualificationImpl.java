/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.TypeName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
In each type, the method list and the this list get filled.
In each method, the fields list gets filled.
The typesNotImported are computed once per primary type.
For this reason, a hierarchical implementations seems most efficient.

The this list is not read recursively but only from the nearest level
where there is data.
 */

public class QualificationImpl implements Qualification {
    private final Set<FieldInfo> unqualifiedFields = new HashSet<>();
    private final Set<MethodInfo> unqualifiedMethods = new HashSet<>();
    private final Set<This> unqualifiedThis = new HashSet<>();
    private final Map<TypeInfo, TypeName.Required> typesNotImported;
    private final Set<String> simpleTypeNames;
    private final QualificationImpl parent;
    private final QualificationImpl top;

    public QualificationImpl() {
        parent = null;
        top = this;
        typesNotImported = new HashMap<>();
        simpleTypeNames = new HashSet<>();
    }

    public QualificationImpl(Qualification parent) {
        this.parent = (QualificationImpl) parent;
        top = ((QualificationImpl) parent).top;
        typesNotImported = null;
        simpleTypeNames = null;
    }

    @Override
    public boolean qualifierRequired(Variable variable) {
        if (variable instanceof FieldReference fieldReference) {
            if (unqualifiedFields.contains(fieldReference.fieldInfo)) return false;
            return parent == null || parent.qualifierRequired(variable);
        }
        if (variable instanceof This thisVar) {
            QualificationImpl levelWithData = this;
            while (levelWithData.unqualifiedThis.isEmpty()) {
                levelWithData = levelWithData.parent;
                assert levelWithData != null : "Forgot to add this info at the type level?";
            }
            return !levelWithData.unqualifiedThis.contains(thisVar);
        }
        return false;
    }

    public void addField(FieldInfo fieldInfo) {
        boolean newName = unqualifiedFields.stream().noneMatch(fi -> fi.name.equals(fieldInfo.name));
        if (newName) {
            unqualifiedFields.add(fieldInfo);
        } // else: we'll have to qualify, because the name has already been taken
    }

    public void addThis(This thisVar) {
        unqualifiedThis.add(thisVar);
    }

    @Override
    public boolean qualifierRequired(MethodInfo methodInfo) {
        if (unqualifiedMethods.contains(methodInfo)) return false;
        return parent == null || parent.qualifierRequired(methodInfo);
    }

    public void addMethodUnlessOverride(MethodInfo methodInfo) {
        boolean newMethod = unqualifiedMethods.stream().noneMatch(mi ->
                mi.methodResolution.get().overrides().contains(methodInfo));
        if (newMethod) {
            unqualifiedMethods.add(methodInfo);
        }
    }

    @Override
    public TypeName.Required qualifierRequired(TypeInfo typeInfo) {
        assert top.typesNotImported != null; // to keep IntelliJ happy
        return top.typesNotImported.getOrDefault(typeInfo, TypeName.Required.SIMPLE);
    }

    public boolean addTypeReturnImport(TypeInfo typeInfo) {
        assert parent == null; // only add these at the top level
        assert typesNotImported != null; // to keep IntelliJ happy
        assert simpleTypeNames != null;
        // IMPROVE also code for subtypes!
        if(simpleTypeNames.contains(typeInfo.simpleName)) {
            typesNotImported.put(typeInfo, TypeName.Required.FQN);
            return false;
        } else {
            simpleTypeNames.add(typeInfo.simpleName);
            return true;
        }
    }
}
