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

package org.e2immu.annotatedapi.javaparser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

public class ComGithubJavaparserAstBody {

    public static final String PACKAGE_NAME = "com.github.javaparser.ast.body";

    interface CallableDeclaration$ {

        @NotNull1
        NodeList<TypeParameter> getTypeParameters();

    }

    interface TypeDeclaration$ {

        @NotNull1
        NodeList<BodyDeclaration<?>> getMembers();

        @NotNull
        SimpleName getName();
    }

    interface VariableDeclarator$ {

        @NotNull
        Type getType();
    }
    interface Parameter$ {

        @NotNull1
        NodeList<AnnotationExpr> getAnnotations();
    }
}
