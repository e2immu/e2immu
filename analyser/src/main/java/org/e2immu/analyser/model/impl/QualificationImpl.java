/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
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
                if(levelWithData == null) return false; // we did not start properly at the top, we're e.g. only outputting a method
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
                mi.methodResolution.get("Method resolution of "+mi.fullyQualifiedName).overrides().contains(methodInfo));
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
