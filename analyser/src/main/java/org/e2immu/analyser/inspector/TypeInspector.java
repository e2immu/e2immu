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

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.TypeMap;

import java.util.List;

public interface TypeInspector {
    String PACKAGE_NAME_FIELD = "PACKAGE_NAME";

    void inspectAnonymousType(ParameterizedType typeImplemented,
                              ExpressionContext expressionContext,
                              NodeList<BodyDeclaration<?>> members);

    List<TypeInfo> inspect(boolean enclosingTypeIsInterface,
                           TypeInfo enclosingType,
                           TypeDeclaration<?> typeDeclaration,
                           ExpressionContext expressionContext);

    void inspectLocalClassDeclaration(ExpressionContext expressionContext,
                                      ClassOrInterfaceDeclaration cid);

    // only to be called on primary types
    void recursivelyAddToTypeStore(TypeMap.Builder typeStore, TypeDeclaration<?> typeDeclaration,
                                   boolean dollarTypesAreNormalTypes);

    TypeInfo getTypeInfo();
}
