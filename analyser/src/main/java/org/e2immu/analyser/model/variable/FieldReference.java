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

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.*;
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
    }

    @Override
    public TypeInfo getOwningType() {
        return fieldInfo.owner;
    }

    /**
     * @param o the other one
     * @return true if the same field is being referred to
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldReference that = (FieldReference) o;
        return fieldInfo.equals(that.fieldInfo) && Objects.equals(scope, that.scope);
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

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return isStatic() ? SideEffect.STATIC_ONLY : SideEffect.NONE_CONTEXT;
    }
}
