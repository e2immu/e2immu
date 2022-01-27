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

import org.e2immu.analyser.inspector.ParameterizedTypeFactory;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class TypeParameterImpl implements TypeParameter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeParameterImpl.class);

    public final String name;
    public final int index;

    // typeInfo can be set straight away, but methodInfo has to wait until
    // method building is sufficiently far
    private final SetOnce<Either<TypeInfo, MethodInfo>> owner = new SetOnce<>();
    private final SetOnce<List<ParameterizedType>> typeBounds = new SetOnce<>();
    // type parameter can be created using byte code analysis; this flag can
    // then be set during AnnotatedAPI shallow analysis
    private final SetOnce<Boolean> annotatedWithIndependent = new SetOnce<>();


    public TypeParameterImpl(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public TypeParameterImpl(TypeInfo typeInfo, String name, int index) {
        this(name, index);
        owner.set(Either.left(typeInfo));
    }

    @Override
    public String toString() {
        String where = owner.isSet() ? (owner.get().isLeft() ? owner.get().getLeft().fullyQualifiedName :
                owner.get().getRight().fullyQualifiedName()) : "<no owner yet>";
        return name + " as #" + index + " in " + where;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeParameterImpl that = (TypeParameterImpl) o;
        return owner.equals(that.owner) &&
                index == that.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, index);
    }

    @Override
    public Either<TypeInfo, MethodInfo> getOwner() {
        return owner.getOrDefaultNull();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public List<ParameterizedType> getTypeBounds() {
        return typeBounds.getOrDefault(List.of());
    }

    @Override
    public OutputBuilder output(InspectionProvider inspectionProvider,
                                Qualification qualification,
                                Set<TypeParameter> visitedTypeParameters) {
        List<ParameterizedType> typeBounds = getTypeBounds();
        String name = qualification.useNumericTypeParameters()
                ? (isMethodTypeParameter() ? "M" : "T") + getIndex()
                : getName();
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text(name));
        if (!typeBounds.isEmpty() && visitedTypeParameters != null && !visitedTypeParameters.contains(this)) {
            visitedTypeParameters.add(this);
            outputBuilder.add(Space.ONE).add(new Text("extends")).add(Space.ONE);
            outputBuilder.add(getTypeBounds()
                    .stream()
                    .map(pt -> ParameterizedTypePrinter.print(inspectionProvider, qualification, pt, false,
                            Diamond.SHOW_ALL, false, visitedTypeParameters))
                    .collect(OutputBuilder.joining(Symbol.AND_TYPES)));
        }
        return outputBuilder;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setMethodInfo(MethodInfo methodInfo) {
        owner.set(Either.right(methodInfo));
    }
    // from method and type inspector

    public void inspect(TypeContext typeContext, com.github.javaparser.ast.type.TypeParameter typeParameter) {
        List<ParameterizedType> typeBounds = new ArrayList<>();
        typeParameter.getTypeBound().forEach(cit -> {
            LOGGER.debug("Inspecting type parameter {}", cit.getName().asString());
            ParameterizedType bound = ParameterizedTypeFactory.from(typeContext, cit);
            typeBounds.add(bound);
        });
        setTypeBounds(typeBounds);
    }

    // from byte code inspector

    public void setTypeBounds(List<ParameterizedType> typeBounds) {
        this.typeBounds.set(List.copyOf(typeBounds));
    }

    @Override
    public boolean isMethodTypeParameter() {
        return !owner.isSet() || owner.get().isRight();
    }

    @Override
    public Boolean isAnnotatedWithIndependent() {
        return annotatedWithIndependent.getOrDefaultNull();
    }

    public void setAnnotatedWithIndependent(boolean b) {
        annotatedWithIndependent.set(b);
    }
}
