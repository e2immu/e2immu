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

import java.util.LinkedList;
import java.util.List;

public class VariableScope_8 {

    interface OutputElement {

    }

    enum Symbol implements OutputElement {
        LEFT_PARENTHESIS, RIGHT_PARENTHESIS, OPEN_CLOSE_PARENTHESIS, DOT, COMMA;
    }

    interface Expression {
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

    }

    record TypeExpression(String name, TypeInfo typeInfo) implements Expression {
    }

    record OutputBuilder(List<OutputElement> outputElements) {
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
        public OutputBuilder output(Qualification qualification, GuideGenerator guideGenerator) {
            OutputBuilder outputBuilder = new OutputBuilder(new LinkedList<>());
            boolean last = false;
            boolean start = false;
            GuideGenerator gg = null;
            if (object != null) {
                VariableExpression ve;
                if (object instanceof MethodCall methodCall) {
                    // chaining!
                    if (guideGenerator == null) {
                        gg = GuideGenerator.defaultGuideGenerator();
                        last = true;
                    } else {
                        gg = guideGenerator;
                    }
                    outputBuilder.add(methodCall.output(qualification, gg)); // recursive call
                    outputBuilder.add(gg.mid());
                    outputBuilder.add(Symbol.DOT);
                } else if (object instanceof TypeExpression typeExpression) {
                /*
                we may or may not need to write the type here.
                (we check methodInspection is set, because of debugOutput)
                 */
                    TypeInfo typeInfo = typeExpression.typeInfo;
                    TypeName typeName = typeInfo.typeName(qualification.qualifierRequired(typeInfo));
                    outputBuilder.add(new QualifiedName(this.name, typeName,
                            qualification.qualifierRequired(typeExpression.typeInfo) ? QualifiedName.Required.YES : QualifiedName.Required.NO_METHOD));
                    if (guideGenerator != null) start = true;
                } else if (object instanceof VariableExpression variableExpression) {
                    //     (we check methodInspection is set, because of debugOutput)
                    // TypeName typeName = thisVar.typeInfo.typeName(qualification.qualifierRequired(thisVar.typeInfo));
                    //  ThisName thisName = new ThisName( typeName, qualification.qualifierRequired(thisVar));
                    // outputBuilder.add(new QualifiedName(methodInfo.name, thisName,
                    //          qualification.qualifierRequired(methodInfo) ? YES : NO_METHOD));
                    if (guideGenerator != null) start = true;
                } else {
                    // next level is NOT a gg; if gg != null we're at the start of the chain
                    //outputBuilder.add(outputInParenthesis(qualification, precedence(), object));
                    if (guideGenerator != null) outputBuilder.add(guideGenerator.start());
                    outputBuilder.add(Symbol.DOT);
                }
            }

            if (parameterExpressions.isEmpty()) {
                outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
            } else {
                outputBuilder
                        .add(Symbol.LEFT_PARENTHESIS)
                        .add(Symbol.RIGHT_PARENTHESIS);
            }
            if (start) {
                outputBuilder.add(guideGenerator.start());
            }
            if (last) {
                outputBuilder.add(gg.end());
            }
            return outputBuilder;
        }
    }
}
