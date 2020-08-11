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
