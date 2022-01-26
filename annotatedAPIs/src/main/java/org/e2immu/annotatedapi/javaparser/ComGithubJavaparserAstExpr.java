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
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.Optional;

public class ComGithubJavaparserAstExpr {

    public static final String PACKAGE_NAME = "com.github.javaparser.ast.expr";

    interface ObjectCreationExpr$ {

        @Modified
        @Fluent
        ObjectCreationExpr setArguments(final NodeList<Expression> arguments);
    }

    interface SwitchExpr$ {
        
        @NotNull
        Expression getSelector();
    }


    interface MethodCallExpr$ {

        @NotNull1
        NodeList<Expression> getArguments();

        @NotNull
        SimpleName getName();

        @NotNull
        Expression getScope();
    }

    interface MethodReferenceExpr$ {

        @NotNull
        Expression getScope();
    }

    interface LambdaExpr$ {

        @NotNull1
        NodeList<Parameter> getParameters();

        @NotNull
        Optional<Expression> getExpressionBody();
    }

    interface Expression$ {

        @NotNull
        StringLiteralExpr asStringLiteralExpr();
    }
}
