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

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Objects;

import static org.e2immu.analyser.output.QualifiedName.Required.*;

public class FieldReference extends VariableWithConcreteReturnType {
    public final FieldInfo fieldInfo;

    // can be a Resolved field again, but ends with This
    // can be null, in which case this is a reference to a static field
    public final Variable scope;

    public FieldReference(InspectionProvider inspectionProvider, FieldInfo fieldInfo, Variable scope) {
        super(scope == null ? fieldInfo.type :
                // it is possible that the field's type shares a type parameter with the scope
                // if so, there *may* be a concrete type to fill
                fieldInfo.type.inferConcreteFieldTypeFromConcreteScope(inspectionProvider,
                        fieldInfo.owner.asParameterizedType(inspectionProvider), scope.concreteReturnType()));
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.scope = scope;
        assert !fieldInfo.isStatic(inspectionProvider) || !(scope instanceof This)
                : "Have This scope on static field " + fullyQualifiedName();
    }

    @Override
    public TypeInfo getOwningType() {
        return fieldInfo.owner;
    }

    /**
     * Two field references with the same fieldInfo object can only be different when
     * not both scopes are instances of This.
     *
     * @param o the other one
     * @return true if the same field is being referred to
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldReference that = (FieldReference) o;
        return fieldInfo.equals(that.fieldInfo) && (scope instanceof This && that.scope instanceof This || Objects.equals(scope, that.scope));
    }

    @Override
    public int hashCode() { //
        // return fieldInfo.hashCode();
        return Objects.hash(fieldInfo, scope);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return fieldInfo.type;
    }

    @Override
    public String simpleName() {
        return fieldInfo.name;
    }

    @Override
    public String nameInLinkedAnnotation() {
        return fieldInfo.owner.simpleName + "." + fieldInfo.name;
    }

    @Override
    public String fullyQualifiedName() {
        if (scope == null || scope instanceof This) {
            return fieldInfo.fullyQualifiedName();
        }
        return fieldInfo.fullyQualifiedName() + "#" + scope.fullyQualifiedName();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (scope == null) {
            // static!
            return new OutputBuilder().add(new QualifiedName(fieldInfo.name,
                    new TypeName(fieldInfo.owner, qualification.qualifierRequired(fieldInfo.owner)),
                    qualification.qualifierRequired(this) ? YES : NO_FIELD));
        }
        if (scope instanceof This thisVar) {
            ThisName thisName = new ThisName(thisVar.writeSuper,
                    new TypeName(thisVar.typeInfo, qualification.qualifierRequired(thisVar.typeInfo)),
                    qualification.qualifierRequired(thisVar));
            return new OutputBuilder().add(new QualifiedName(fieldInfo.name, thisName,
                    qualification.qualifierRequired(this) ? YES : NO_FIELD));
        }
        // real variable
        return new OutputBuilder().add(scope.output(qualification)).add(Symbol.DOT)
                .add(new QualifiedName(simpleName(), null, NEVER));
    }

    @Override
    public String toString() {
        return output(Qualification.EMPTY).toString();
    }

    @Override
    public boolean isStatic() {
        return fieldInfo.isStatic();
    }
}
