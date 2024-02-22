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

package org.e2immu.analyser.model.variable.impl;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.ThisName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.output.QualifiedName.Required.*;

public class FieldReferenceImpl extends VariableWithConcreteReturnType implements FieldReference {
    @NotNull
    private final FieldInfo fieldInfo;

    @NotNull
    private final Expression scope;
    @Nullable
    private final Variable scopeVariable;

    private final boolean isStatic;
    private final boolean isDefaultScope;
    @NotNull
    private final String fullyQualifiedName;

    public FieldReferenceImpl(InspectionProvider inspectionProvider, FieldInfo fieldInfo) {
        this(inspectionProvider, fieldInfo, null, null, null);
    }

    public FieldReferenceImpl(InspectionProvider inspectionProvider,
                              FieldInfo fieldInfo,
                              Expression scope,
                              TypeInfo owningType) {
        this(inspectionProvider, fieldInfo, scope, null, owningType);
    }

    public FieldReferenceImpl(InspectionProvider inspectionProvider,
                              FieldInfo fieldInfo,
                              Expression scope,
                              Variable overrideScopeVariable, // only provide when you want to override normal computation
                              TypeInfo owningType) {
        this(inspectionProvider, fieldInfo, scope, overrideScopeVariable,
                scope == null ? fieldInfo.type :
                        // it is possible that the field's type shares a type parameter with the scope
                        // if so, there *may* be a concrete type to fill
                        fieldInfo.type.inferConcreteFieldTypeFromConcreteScope(inspectionProvider,
                                fieldInfo.owner.asParameterizedType(inspectionProvider), scope.returnType()),
                owningType);
    }

    public FieldReferenceImpl(InspectionProvider inspectionProvider,
                              FieldInfo fieldInfo,
                              Expression scope,
                              Variable overrideScopeVariable,
                              ParameterizedType concreteType,
                              TypeInfo owningType) {
        super(concreteType);
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.isStatic = inspectionProvider.getFieldInspection(fieldInfo).isStatic();
        if (this.isStatic) {
            // IMPORTANT: the owner doesn't necessarily have a decent identifier, but the field should have one
            Identifier identifier = fieldInfo.getIdentifier();
            this.scope = new TypeExpression(identifier, fieldInfo.owner.asSimpleParameterizedType(), Diamond.NO);
            isDefaultScope = true;
            this.scopeVariable = null;
        } else if (scope == null) {
            scopeVariable = new This(inspectionProvider, fieldInfo.owner);
            Identifier identifier = fieldInfo.getIdentifier();
            this.scope = new VariableExpression(identifier, scopeVariable);
            isDefaultScope = true;
        } else {
            if (scope instanceof VariableExpression ve) {
                if (ve.variable() instanceof This thisVar) {
                    if (thisVar.typeInfo == fieldInfo.owner) {
                        this.scope = scope;
                        scopeVariable = ve.variable();
                    } else {
                        scopeVariable = new This(inspectionProvider, fieldInfo.owner);
                        this.scope = new VariableExpression(ve.identifier, scopeVariable);
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

    private String computeFqn() {
        if (isStatic || scopeIsThis()) {
            return fieldInfo.fullyQualifiedName();
        }
        return fieldInfo.fullyQualifiedName() + "#" + scopeVariable.fullyQualifiedName();
    }

    @Override
    public FieldInfo fieldInfo() {
        return fieldInfo;
    }

    @Override
    public Variable scopeVariable() {
        return scopeVariable;
    }

    @Override
    public Expression scope() {
        return scope;
    }

    @Override
    public boolean isDefaultScope() {
        return isDefaultScope;
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
        return o instanceof FieldReferenceImpl that && that.fullyQualifiedName.equals(fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return fullyQualifiedName.hashCode();
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
            return UpgradableBooleanMap.of(scope.typesReferenced(), parameterizedType.typesReferenced(explicit));
        }
        return parameterizedType.typesReferenced(explicit);
    }

    @Override
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        if (scope != null && !scopeIsThis()) {
            return PackedIntMap.of(scope.typesReferenced2(weight), parameterizedType.typesReferenced2(weight));
        }
        return parameterizedType.typesReferenced2(weight);
    }

    // this.x
    public boolean scopeIsThis() {
        return !isStatic && isDefaultScope;
    }

    // this.x.y as well!
    public boolean scopeIsRecursivelyThis() {
        if (scopeIsThis()) return true;
        if (scopeVariable instanceof FieldReferenceImpl fr) return fr.scopeIsRecursivelyThis();
        return false;
    }

    public Variable thisInScope() {
        if (scopeVariable instanceof This) return scopeVariable;
        if (scopeVariable instanceof FieldReferenceImpl fr) return fr.thisInScope();
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
    public void visit(Visitor visitor) {
        if (visitor.beforeVariable(this)) {
            if (scope != null) {
                scope.visit(visitor);
            }
        }
        visitor.afterVariable(this);
    }

    @Override
    public boolean hasScopeVariableCreatedAt(String index) {
        return scopeVariable != null && scopeVariable.hasScopeVariableCreatedAt(index);
    }

    public boolean hasAsScopeVariable(Variable pv) {
        return scopeVariable != null &&
                (pv.equals(scopeVariable) || scopeVariable instanceof FieldReferenceImpl fr && fr.hasAsScopeVariable(pv));
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return scope.causesOfDelay().merge(scopeVariable == null ? CausesOfDelay.EMPTY : scopeVariable.causesOfDelay());
    }

    @Override
    public boolean containsAtLeastOneOf(Set<? extends Variable> variables) {
        return scopeVariable != null && (variables.contains(scopeVariable) || scopeVariable.containsAtLeastOneOf(variables));
    }

    @Override
    public int getComplexity() {
        if (isStatic) return 2;
        return 1 + scope.getComplexity();
    }

    @Override
    public Stream<Variable> variableStream() {
        if (scopeVariable != null) {
            return Stream.concat(Stream.of(this), scopeVariable.variableStream());
        }
        return Stream.of(this);
    }

    @Override
    public int statementTime() {
        if (scope instanceof DelayedVariableExpression dve) {
            return dve.statementTime;
        }
        return VariableInfoContainer.IGNORE_STATEMENT_TIME;
    }
}
