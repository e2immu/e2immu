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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.ThisName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.e2immu.analyser.output.QualifiedName.Required.*;

public class FieldReference extends VariableWithConcreteReturnType {
    @NotNull
    public final FieldInfo fieldInfo;

    @NotNull
    public final Expression scope;
    @Nullable
    public final Variable scopeVariable;

    public final boolean isStatic;
    public final boolean isDefaultScope;
    @NotNull
    public final String fullyQualifiedName;
    private final int hashCode;

    public FieldReference(InspectionProvider inspectionProvider, FieldInfo fieldInfo) {
        this(inspectionProvider, fieldInfo, null, null, null);
    }

    public FieldReference(InspectionProvider inspectionProvider,
                          FieldInfo fieldInfo,
                          Expression scope,
                          TypeInfo owningType) {
        this(inspectionProvider, fieldInfo, scope, null, owningType);
    }

    public FieldReference(InspectionProvider inspectionProvider,
                          FieldInfo fieldInfo,
                          Expression scope,
                          Variable overrideScopeVariable, // only provide when you want to override normal computation
                          TypeInfo owningType) {
        super(scope == null ? fieldInfo.type :
                // it is possible that the field's type shares a type parameter with the scope
                // if so, there *may* be a concrete type to fill
                fieldInfo.type.inferConcreteFieldTypeFromConcreteScope(inspectionProvider,
                        fieldInfo.owner.asParameterizedType(inspectionProvider), scope.returnType()));
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.isStatic = fieldInfo.isStatic(inspectionProvider);
        if (this.isStatic) {
            Identifier identifier = fieldInfo.owner.getIdentifier();
            this.scope = new TypeExpression(identifier, fieldInfo.owner.asSimpleParameterizedType(), Diamond.NO);
            isDefaultScope = true;
            this.scopeVariable = null;
        } else if (scope == null) {
            scopeVariable = new This(inspectionProvider, fieldInfo.owner);
            this.scope = new VariableExpression(scopeVariable);
            isDefaultScope = true;
        } else {
            if (scope instanceof VariableExpression ve) {
                if (ve.variable() instanceof This thisVar) {
                    if (thisVar.typeInfo == fieldInfo.owner) {
                        this.scope = scope;
                        scopeVariable = ve.variable();
                    } else {
                        scopeVariable = new This(inspectionProvider, fieldInfo.owner);
                        this.scope = new VariableExpression(scopeVariable);
                    }
                    isDefaultScope = true;
                } else {
                    this.scope = scope;
                    isDefaultScope = false;
                    scopeVariable = ve.variable();
                }
            } else {
                // the scope is not a variable, we must introduce a new scope variable
                this.scope = scope;
                isDefaultScope = false;
                scopeVariable = overrideScopeVariable != null ? overrideScopeVariable
                        : new LocalVariableReference(newScopeVariable(scope, owningType), scope);
            }
        }
        this.fullyQualifiedName = computeFqn();
        this.hashCode = hash(this.fieldInfo, this.scopeVariable);
        assert (scopeVariable == null) == isStatic;
    }

    private LocalVariable newScopeVariable(Expression scope, TypeInfo owningType) {
        Identifier identifier = scope.getIdentifier();
        // in the first iteration, we have
        // "assert identifier instanceof Identifier.PositionalIdentifier;"
        // because it must come from the inspector.
        // but in a InlinedMethod replacement, the scope can literally come from everywhere
        String name = "scope-" + identifier.compact();
        VariableNature vn = new VariableNature.ScopeVariable();
        return new LocalVariable(Set.of(LocalVariableModifier.FINAL), name, scope.returnType(), List.of(), owningType, vn);
    }

    private static int hash(FieldInfo fieldInfo, Variable scopeVariable) {
        return fieldInfo.hashCode() + (scopeVariable != null ? 37 * scopeVariable.hashCode() : 0);

    }

    private String computeFqn() {
        if (isStatic || scopeIsThis()) {
            return fieldInfo.fullyQualifiedName();
        }
        return fieldInfo.fullyQualifiedName() + "#" + scopeVariable.fullyQualifiedName();
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
        return fieldInfo.equals(that.fieldInfo) && (scopeVariable == null && that.scopeVariable == null ||
                scopeVariable != null && scopeVariable.equals(that.scopeVariable));
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
    public String minimalOutput() {
        return scope.minimalOutput() + "." + simpleName();
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

    // this.x
    public boolean scopeIsThis() {
        return !isStatic && isDefaultScope;
    }

    // this.x.y as well!
    public boolean scopeIsRecursivelyThis() {
        if (scopeIsThis()) return true;
        if (scopeVariable instanceof FieldReference fr) return fr.scopeIsRecursivelyThis();
        return false;
    }

    public Variable thisInScope() {
        if (scopeVariable instanceof This) return scopeVariable;
        if (scopeVariable instanceof FieldReference fr) return fr.thisInScope();
        return null;
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

    @Override
    public void visit(Predicate<Element> predicate) {
        if (scope != null) scope.visit(predicate);
    }

    @Override
    public boolean hasScopeVariableCreatedAt(String index) {
        return scopeVariable != null && scopeVariable.hasScopeVariableCreatedAt(index);
    }

    public boolean hasAsScopeVariable(Variable pv) {
        return scopeVariable != null &&
                (pv.equals(scopeVariable) || scopeVariable instanceof FieldReference fr && fr.hasAsScopeVariable(pv));
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return scope.causesOfDelay().merge(scopeVariable == null ? CausesOfDelay.EMPTY : scopeVariable.causesOfDelay());
    }
}
