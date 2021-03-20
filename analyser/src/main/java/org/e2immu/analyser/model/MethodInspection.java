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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MethodInspection extends Inspection {

    String getFullyQualifiedName();

    String getDistinguishingName();

    MethodInfo getMethodInfo(); // backlink, container... will become contextclass+immutable eventually

    ParameterizedType getReturnType(); // ContextClass

    Block getMethodBody();

    //@Immutable(level = 2, after="MethodAnalyzer.analyse()")
    //@Immutable
    List<ParameterInfo> getParameters();

    //@Immutable
    Set<MethodModifier> getModifiers();

    //@Immutable
    List<TypeParameter> getTypeParameters();

    //@Immutable
    List<ParameterizedType> getExceptionTypes();

    // if our type implements a number of interfaces, then the method definitions in these interfaces
    // that this method implements, are represented in this variable
    // this is used to check inherited annotations on methods
    //@Immutable
    List<MethodInfo> getImplementationOf();

    Map<CompanionMethodName, MethodInfo> getCompanionMethods();

    boolean isStatic();

    boolean isDefault();

    boolean isVarargs();

    boolean isPrivate();

    default ParameterizedType formalParameterType(int index) {
        int formalParams = getParameters().size();
        if (index < formalParams - 1 || index < formalParams && !isVarargs()) {
            return getParameters().get(index).parameterizedType;
        }
        return getParameters().get(formalParams - 1).parameterizedType.copyWithOneFewerArrays();
    }
}
