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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// this( )
public class ExplicitConstructorInvocation extends StatementWithStructure {

    public final boolean isSuper;
    public final MethodInfo methodInfo;

    public ExplicitConstructorInvocation(Identifier identifier,
                                         boolean isSuper,
                                         MethodInfo methodInfo,
                                         List<Expression> parameterExpressions,
                                         Comment comment) {
        super(identifier, null, new Structure.Builder()
                .setUpdaters(parameterExpressions)
                .setComment(comment)
                .build());
        this.isSuper = isSuper;
        this.methodInfo = methodInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ExplicitConstructorInvocation other) {
            return identifier.equals(other.identifier)
                    && isSuper == other.isSuper
                    && methodInfo.equals(other.methodInfo)
                    && structure.updaters().equals(other.structure.updaters());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, isSuper, methodInfo, structure.updaters());
    }

    @Override
    public int getComplexity() {
        return 1 + structure.updaters().stream().mapToInt(Expression::getComplexity).sum();
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        return List.of(new ExplicitConstructorInvocation(identifier, isSuper, methodInfo, structure.updaters().stream()
                .map(updater -> updater.translate(inspectionProvider, translationMap))
                .collect(Collectors.toList()), structure.comment()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        String name = isSuper ? "super" : "this";
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text(name));
        if (structure.updaters().isEmpty()) {
            outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(structure.updaters().stream()
                            .map(expression -> expression.output(qualification))
                            .collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        return outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
    }

    @Override
    public List<? extends Element> subElements() {
        return structure.updaters();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            structure.updaters().forEach(updater -> updater.visit(predicate));
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeStatement(this)) {
            structure.updaters().forEach(updater -> updater.visit(visitor));
        }
        visitor.afterStatement(this);
    }
}
