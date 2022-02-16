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

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.ThisName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Objects;

/**
 * Variable representing the "this" keyword
 */
public class This implements Variable {
    public final TypeInfo typeInfo;
    public final ParameterizedType typeAsParameterizedType;

    // display information.
    public final TypeInfo explicitlyWriteType;
    public final boolean writeSuper;

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        this(inspectionProvider, typeInfo, null, false);
    }

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo, TypeInfo explicitlyWriteType, boolean writeSuper) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.explicitlyWriteType = explicitlyWriteType;
        this.writeSuper = writeSuper;
        typeAsParameterizedType = typeInfo.asParameterizedType(inspectionProvider);
    }

    public static Variable create(TypeContext typeContext, boolean writeSuper, TypeInfo enclosingType, String typeName) {
        if (typeName != null) {
            NamedType superTypeNamed = typeContext.get(typeName, true);
            if (!(superTypeNamed instanceof TypeInfo superType)) throw new UnsupportedOperationException();
            if (writeSuper) {
                // SomeType.super. --> write SomeType explicitly; go upwards from its parent
                TypeInfo parentType = typeContext.getTypeInspection(superType).parentClass().bestTypeInfo();
                assert parentType != null : "?? if super is allowed, it should have a parent!";
                return new This(typeContext, parentType, superType, true);
            }
            // SomeType.this. --> write SomeType explicitly; and start at SomeType going upwards
            return new This(typeContext, superType, superType, false);
        }

        if (writeSuper) {
            // super. --> go up from enclosing type's parent
            TypeInfo parentType = typeContext.getTypeInspection(enclosingType).parentClass().bestTypeInfo();
            return new This(typeContext, parentType, null, true);
        }
        // this. --> here
        return new This(typeContext, enclosingType, null, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        This aThis = (This) o;
        // as long as it points to the correct type, it's the same object
        return typeInfo.equals(aThis.typeInfo);
    }

    @Override
    public TypeInfo getOwningType() {
        return typeInfo;
    }

    @Override
    public int hashCode() {
        return typeInfo.fullyQualifiedName.hashCode();
    }

    @Override
    public ParameterizedType parameterizedType() {
        return typeAsParameterizedType;
    }

    @Override
    public String simpleName() {
        String superOrThis = writeSuper ? "super" : "this";
        if (explicitlyWriteType != null) return explicitlyWriteType.simpleName + "+" + superOrThis;
        return superOrThis;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new ThisName(writeSuper,
                typeInfo.typeName(qualification.qualifierRequired(typeInfo)),
                qualification.qualifierRequired(this)));
    }

    @Override
    public String toString() {
        return output(Qualification.EMPTY).toString();
    }

    @Override
    public String fullyQualifiedName() {
        return typeInfo.fullyQualifiedName + ".this";
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        if (explicitlyWriteType != null && explicitlyWriteType != typeInfo) {
            return UpgradableBooleanMap.of(explicitlyWriteType, true, typeInfo, false);
        }
        return UpgradableBooleanMap.of(typeInfo, explicitlyWriteType == typeInfo);
    }
}
