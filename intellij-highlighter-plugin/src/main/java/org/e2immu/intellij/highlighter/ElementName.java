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

package org.e2immu.intellij.highlighter;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.e2immu.intellij.highlighter.java.JavaAnnotator;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.e2immu.intellij.highlighter.ElementType.*;

public class ElementName {
    public final String elementQn;
    public final ElementType elementType;

    private ElementName(String elementQn, ElementType elementType) {
        this.elementQn = elementQn;
        this.elementType = elementType;
    }

    public static ElementName fromField(PsiClass classOfField, PsiIdentifier identifier) {
        return new ElementName(classOfField.getQualifiedName() + ":" + identifier.getText(), FIELD);
    }

    public static ElementName fromMethod(PsiClass containingClass, PsiMethod method) {
        String name = method.getName();
        String typesCsv = Arrays.stream(method.getParameterList().getParameters())
                .map(JavaAnnotator::typeOfParameter)
                .collect(Collectors.joining(","));
        return new ElementName(containingClass.getQualifiedName() + "." + name + "(" + typesCsv + ")", METHOD);
    }

    public static ElementName parameter(ElementName methodName, int index) {
        return new ElementName(methodName.elementQn + "#" + index, PARAM);
    }

    public static ElementName type(String qualifiedName) {
        return new ElementName(qualifiedName, TYPE);
    }

    public static ElementName fromAnnotation(PsiAnnotation annotation, ElementType elementType) {
        String qn = annotation.getQualifiedName();
        return new ElementName(qn, elementType);
    }

    public static ElementName dynamicTypeAnnotation(String typeFqn, ElementName methodOrFieldContext) {
        ElementType elementType = methodOrFieldContext.elementType == METHOD ? TYPE_OF_METHOD : TYPE_OF_FIELD;
        String combinedName = methodOrFieldContext.elementQn + " " + typeFqn;
        return new ElementName(combinedName, elementType);
    }

    @Override
    public String toString() {
        return elementQn;
    }

}
