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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.body.*;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.annotation.Modified;

import java.util.List;
import java.util.Map;

public interface MethodInspector {
    MethodInspection.Builder getBuilder();

    void inspect(AnnotationMemberDeclaration amd, ExpressionContext expressionContext);

    /*
        Compact constructor for records
         */
    @Modified
    boolean inspect(CompactConstructorDeclaration ccd,
                    ExpressionContext expressionContext,
                    Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                    List<RecordField> fields);

    /*
        Inspection of a static block
         */
    void inspect(InitializerDeclaration id,
                 ExpressionContext expressionContext,
                 int staticBlockIdentifier);

    /*
        Inspection of a constructor.
        Code block will be handled later.
         */
    void inspect(ConstructorDeclaration cd,
                 ExpressionContext expressionContext,
                 Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                 DollarResolver dollarResolver,
                 boolean makePrivate);

    void inspect(boolean isInterface,
                 String methodName,
                 MethodDeclaration md,
                 ExpressionContext expressionContext,
                 Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                 DollarResolver dollarResolver);
}
