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

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.Finalizer;

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

    Map<CompanionMethodName, MethodInfo> getCompanionMethods();

    boolean isStatic();

    boolean isDefault();

    boolean isVarargs();

    default boolean isPrivate() {
        return getModifiers().contains(MethodModifier.PRIVATE);
    }

    default boolean isPublic() {
        return isPublic(InspectionProvider.DEFAULT);
    }

    default boolean isPublic(InspectionProvider inspectionProvider) {
        return getMethodInfo().typeInfo.isPublic(inspectionProvider) &&
                (getModifiers().contains(MethodModifier.PUBLIC) ||
                        getMethodInfo().typeInfo.isInterface(inspectionProvider));
    }

    boolean isCompactConstructor();

    boolean isStaticBlock();

    default ParameterizedType formalParameterType(int index) {
        int formalParams = getParameters().size();
        if (index < formalParams - 1 || index < formalParams && !isVarargs()) {
            return getParameters().get(index).parameterizedType;
        }
        return getParameters().get(formalParams - 1).parameterizedType.copyWithOneFewerArrays();
    }

    default boolean hasContractedFinalizer() {
        return getAnnotations().stream()
                .filter(ae -> {
                    AnnotationParameters ap = ae.e2ImmuAnnotationParameters();
                    return ap != null && ap.contract();
                })
                .anyMatch(ae -> ae.typeInfo().fullyQualifiedName.equals(Finalizer.class.getCanonicalName()));
    }

    default boolean isAbstract() {
        return getModifiers().contains(MethodModifier.ABSTRACT);
    }

    default boolean hasStatements() {
        return getMethodBody() != null && !getMethodBody().isEmpty();
    }

    default boolean isFactoryMethod() {
        assert isStatic();
        if (getParameters().isEmpty()) return false;
        return getReturnType().typeInfo != null && getReturnType().typeInfo == getMethodInfo().typeInfo;
    }

    default boolean isVoid() {
        return getReturnType().isVoid();
    }

    /**
     * in a functional interface, we need exactly one non-static, non-default method, but you can always
     * add equals() or hashCode() or any other method from java.lang.Object() to the overload list...
     *
     * @return true for equals, hashCode etc.
     */
    default boolean isOverloadOfJLOMethod() {
        if ("equals".equals(getMethodInfo().name) && getParameters().size() == 1) return true;
        if ("hashCode".equals(getMethodInfo().name) && getParameters().size() == 0) return true;
        return "toString".equals(getMethodInfo().name) && getParameters().size() == 0;
    }
}
