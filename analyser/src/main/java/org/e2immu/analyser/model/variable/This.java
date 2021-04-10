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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.ThisName;
import org.e2immu.analyser.output.TypeName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Objects;

/**
 * Variable representing the "this" keyword
 */
public class This implements Variable {
    public final TypeInfo typeInfo;
    public final boolean explicitlyWriteType;
    public final boolean writeSuper;
    public final ParameterizedType typeAsParameterizedType;

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        this(inspectionProvider, typeInfo, false, false);
    }

    public This(InspectionProvider inspectionProvider, TypeInfo typeInfo, boolean explicitlyWriteType, boolean writeSuper) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.explicitlyWriteType = explicitlyWriteType;
        this.writeSuper = writeSuper;
        typeAsParameterizedType = typeInfo.asParameterizedType(inspectionProvider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        This aThis = (This) o;
        return typeInfo.equals(aThis.typeInfo) && writeSuper == aThis.writeSuper;
    }

    @Override
    public TypeInfo getOwningType() {
        return typeInfo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return typeAsParameterizedType;
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return typeAsParameterizedType;
    }

    @Override
    public String simpleName() {
        if (explicitlyWriteType) return typeInfo.simpleName + (writeSuper ? ".super" : ".this");
        return writeSuper ? "super" : "this";
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new ThisName(writeSuper,
                new TypeName(typeInfo, qualification.qualifierRequired(typeInfo)),
                qualification.qualifierRequired(this)));
    }

    @Override
    public String toString() {
        return output(Qualification.EMPTY).toString();
    }

    @Override
    public String fullyQualifiedName() {
        return typeInfo.fullyQualifiedName + "." + (writeSuper ? "super" : "this");
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        boolean b = explicit && explicitlyWriteType;
        if (writeSuper) {
            return UpgradableBooleanMap.of(typeInfo, b, typeInfo.typeInspection.get().parentClass().bestTypeInfo(), false);
        }
        return UpgradableBooleanMap.of(typeInfo, b);
    }
}
