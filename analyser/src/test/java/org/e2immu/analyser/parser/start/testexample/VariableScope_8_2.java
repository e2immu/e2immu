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

package org.e2immu.analyser.parser.start.testexample;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class VariableScope_8_2 {

    interface OutputElement {

    }

    enum Precedence {
        P1, P2;

        public boolean greaterThan(Precedence other) {
            return ordinal() >= other.ordinal();
        }
    }

    enum Symbol implements OutputElement {
        LEFT_PARENTHESIS, RIGHT_PARENTHESIS, OPEN_CLOSE_PARENTHESIS, DOT, COMMA;
    }

    interface Expression {
        default OutputBuilder outputInParenthesis(Qualification qualification, Precedence precedence, Expression expression) {
            if (precedence.greaterThan(expression.precedence())) {
                return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
            }
            return expression.output(qualification);
        }

        OutputBuilder output(Qualification qualification);

        Precedence precedence();
    }

    interface GuideGenerator {
        OutputElement start();

        OutputElement end();

        OutputElement mid();

        static GuideGenerator defaultGuideGenerator() {
            return new GuideGenerator() {
                @Override
                public OutputElement start() {
                    return null;
                }

                @Override
                public OutputElement end() {
                    return null;
                }

                @Override
                public OutputElement mid() {
                    return null;
                }
            };
        }
    }

    record TypeName(String name) {
    }

    record ThisName(TypeName typeName, boolean required) {
    }

    record QualifiedName(String name, TypeName typeName, Required required) implements OutputElement {
        public enum Required {
            YES, // always write
            NO_FIELD, // don't write unless a field-related option says so
            NO_METHOD, // don't write unless a method-related option says so
            NEVER // never write
        }
    }

    interface Qualification {
        boolean qualifierRequired(TypeInfo typeInfo);

    }

    record TypeInfo(String name) {
        TypeName typeName(boolean b) {
            return new TypeName(name);
        }
    }

    record VariableExpression() implements Expression {

        @Override
        public OutputBuilder output(Qualification qualification) {
            return new OutputBuilder();
        }

        @Override
        public Precedence precedence() {
            return Precedence.P1;
        }
    }

    record TypeExpression(String name, TypeInfo typeInfo) implements Expression {

        @Override
        public OutputBuilder output(Qualification qualification) {
            return new OutputBuilder();
        }

        @Override
        public Precedence precedence() {
            return Precedence.P2;
        }
    }

    record OutputBuilder(List<OutputElement> outputElements) {
        public OutputBuilder() {
            this(new ArrayList<>());
        }

        OutputBuilder add(OutputElement outputElement) {
            outputElements.add(outputElement);
            return this;
        }

        OutputBuilder add(OutputBuilder outputBuilder) {
            outputElements.addAll(outputBuilder.outputElements);
            return this;
        }
    }

    record MethodCall(String name, Expression object, List<Expression> parameterExpressions) implements Expression {

        // will come directly here only from this method (chaining of method calls produces a guide)
        public OutputBuilder output2(Qualification qualification, GuideGenerator guideGenerator) {
            OutputBuilder outputBuilder = new OutputBuilder(new LinkedList<>());
            GuideGenerator gg;// = null;
            if (object != null) {
                if (object instanceof MethodCall methodCall) {
                    // chaining!
                    if (guideGenerator == null) {
                        gg = GuideGenerator.defaultGuideGenerator();
                    } else {
                        gg = guideGenerator;
                    }
                    //outputBuilder.add(methodCall.output2(qualification, gg)); // recursive call
                    outputBuilder.add(gg.mid()); // 2.0.0.0.1
                }
            }

            return outputBuilder;
        }

        @Override
        public OutputBuilder output(Qualification qualification) {
            return output2(qualification, GuideGenerator.defaultGuideGenerator());
        }

        @Override
        public Precedence precedence() {
            return null;
        }
    }
}
