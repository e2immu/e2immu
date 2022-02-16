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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.ThisName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Objects;

import static org.e2immu.analyser.output.QualifiedName.Required.*;

public class FieldReference extends VariableWithConcreteReturnType {
    public final FieldInfo fieldInfo;

    // can be a Resolved field again, but ends with This
    // cannot be null
    public final Expression scope;
    public final boolean isStatic;
    public final boolean isDefaultScope;
    public final String fullyQualifiedName;
    private final int hashCode;

    public FieldReference(InspectionProvider inspectionProvider, FieldInfo fieldInfo) {
        this(inspectionProvider, fieldInfo, null);
    }

    public FieldReference(InspectionProvider inspectionProvider, FieldInfo fieldInfo, Expression scope) {
        super(scope == null ? fieldInfo.type :
                // it is possible that the field's type shares a type parameter with the scope
                // if so, there *may* be a concrete type to fill
                fieldInfo.type.inferConcreteFieldTypeFromConcreteScope(inspectionProvider,
                        fieldInfo.owner.asParameterizedType(inspectionProvider), scope.returnType()));
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.isStatic = fieldInfo.isStatic(inspectionProvider);
        if (this.isStatic) {
            this.scope = new TypeExpression(fieldInfo.owner.asSimpleParameterizedType(), Diamond.NO);
            isDefaultScope = true;
        } else if (scope == null) {
            this.scope = new VariableExpression(new This(inspectionProvider, fieldInfo.owner));
            isDefaultScope = true;
        } else {
            IsVariableExpression ive;
            if (((ive = scope.asInstanceOf(IsVariableExpression.class)) != null) && ive.variable() instanceof This thisVar) {
                this.scope = thisVar.typeInfo == fieldInfo.owner ? scope
                        : new VariableExpression(new This(inspectionProvider, fieldInfo.owner));
                isDefaultScope = true;
            } else {
                this.scope = scope;
                isDefaultScope = false;
            }
        }
        this.fullyQualifiedName = computeFqn();
        this.hashCode = hash(this.fieldInfo, this.scope);
    }

    private static int hash(FieldInfo fieldInfo, Expression scope) {
        return fieldInfo.hashCode() + 37 * scope.hashCode();

    }

    private String computeFqn() {
        if (isStatic || scopeIsThis()) {
            return fieldInfo.fullyQualifiedName();
        }
        if (scope instanceof ConstructorCall cc && cc.anonymousClass() != null) {
            return fieldInfo.fullyQualifiedName() + "#" + cc.anonymousClass().fullyQualifiedName();
        }
        return fieldInfo.fullyQualifiedName() + "#" + scope.output(Qualification.FULLY_QUALIFIED_NAME);
    }

    // should only be used by translate
    public FieldReference(FieldReference fieldReference, Expression newScope) {
        super(fieldReference.parameterizedType);
        this.fieldInfo = fieldReference.fieldInfo;
        this.isStatic = fieldReference.isStatic;
        this.isDefaultScope = fieldReference.isDefaultScope;
        this.scope = newScope;
        this.fullyQualifiedName = computeFqn();
        this.hashCode = hash(fieldInfo, scope);
    }

    // called from VariableExpression.translate, where no inspection provider is present
    public FieldReference(FieldInfo fieldInfo, Expression scope, ParameterizedType parameterizedType, boolean isStatic,
                          boolean isDefaultScope) {
        super(parameterizedType);
        this.fieldInfo = fieldInfo;
        this.scope = scope;
        this.isStatic = isStatic;
        this.isDefaultScope = isDefaultScope;
        this.fullyQualifiedName = computeFqn();
        this.hashCode = hash(fieldInfo, scope);
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
        // we do not use FQN for equality, because then we should also use fully qualified names
        // in the #part, which makes them even more unreadable. There are potential clashes when translating
        // see e.g. ExplicitConstructorInvocation_6.
        return fieldInfo.equals(that.fieldInfo) && scope.equals(that.scope);
    }

    @Override
    public boolean specialEquals(Variable variable) {
        if (this == variable) return true;
        if (variable instanceof FieldReference fr) {
            return fieldInfo.equals(fr.fieldInfo);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String simpleName() {
        return fieldInfo.name;
    }

    @Override
    public String debug() {
        if (scope == null) return simpleName();
        return scope.debugOutput() + "." + simpleName();
    }

    @Override
    public String nameInLinkedAnnotation() {
        return fieldInfo.owner.simpleName + "." + fieldInfo.name;
    }

    @Override
    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (scope == null) {
            // static!
            return new OutputBuilder().add(new QualifiedName(fieldInfo.name,
                    fieldInfo.owner.typeName(qualification.qualifierRequired(fieldInfo.owner)),
                    qualification.qualifierRequired(this) ? YES : NO_FIELD));
        }
        if (scope instanceof VariableExpression ve && ve.variable() instanceof This thisVar) {
            ThisName thisName = new ThisName(thisVar.writeSuper,
                    thisVar.typeInfo.typeName(qualification.qualifierRequired(thisVar.typeInfo)),
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
        return isStatic;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        if (scope != null && !scopeIsThis()) {
            return UpgradableBooleanMap.of(scope.typesReferenced(), parameterizedType().typesReferenced(explicit));
        }
        return parameterizedType().typesReferenced(explicit);
    }

    public boolean scopeIsThis() {
        return !isStatic && isDefaultScope;
    }

    /*
    the type in which we're evaluating the field reference
    does is the scope mine?
     */
    public boolean scopeIsThis(TypeInfo currentType) {
        IsVariableExpression ive;
        if ((ive = scope.asInstanceOf(IsVariableExpression.class)) != null && ive.variable() instanceof This thisVar) {
            return thisVar.typeInfo.equals(currentType);
        }
        return false;
    }
}
