/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.ContractMark;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractAnalysisBuilder implements Analysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnalysisBuilder.class);

    public final SetOnceMap<AnnotationExpression, Boolean> annotations = new SetOnceMap<>();
    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);
    public final boolean hasBeenDefined;
    public final String simpleName; // for debugging purposes
    public final Primitives primitives;

    protected AbstractAnalysisBuilder(Primitives primitives, boolean hasBeenDefined, String simpleName) {
        this.simpleName = simpleName;
        this.hasBeenDefined = hasBeenDefined;
        this.primitives = primitives;
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, hasBeenDefined ? Level.DELAY : variableProperty.valueWhenAbsent(annotationMode()));
    }

    public int getPropertyAsIs(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public int internalGetProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, hasBeenDefined ? Level.DELAY : variableProperty.valueWhenAbsent(annotationMode()));
    }

    public void setProperty(VariableProperty variableProperty, int i) {
        if (variableProperty.canImprove) {
            properties.improve(variableProperty, i);
        } else {
            properties.put(variableProperty, i);
        }
    }

    public void setProperty(VariableProperty variableProperty, boolean b) {
        properties.put(variableProperty, Level.fromBool(b));
    }

    public void improveProperty(VariableProperty variableProperty, int i) {
        properties.improve(variableProperty, i);
    }


    @Override
    public Stream<Map.Entry<AnnotationExpression, Boolean>> getAnnotationStream() {
        return annotations.stream();
    }

    @Override
    public Boolean getAnnotation(AnnotationExpression annotationExpression) {
        return annotations.getOrDefault(annotationExpression, null);
    }

    public abstract void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions);

    protected void doNotNull(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

        // not null
        int notNull = getProperty(VariableProperty.NOT_NULL);
        if (notNull >= MultiLevel.EVENTUALLY_CONTENT2_NOT_NULL) {
            annotations.put(e2ImmuAnnotationExpressions.notNull2.get(), true);
            annotations.put(e2ImmuAnnotationExpressions.nullable.get(), false);
        } else {
            if (notNull != Level.DELAY) annotations.put(e2ImmuAnnotationExpressions.notNull2.get(), false);

            if (notNull >= MultiLevel.EVENTUALLY_CONTENT_NOT_NULL) {
                annotations.put(e2ImmuAnnotationExpressions.notNull1.get(), true);
                annotations.put(e2ImmuAnnotationExpressions.nullable.get(), false);
            } else {
                if (notNull > Level.DELAY) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull1.get(), false);
                }
                if (notNull >= MultiLevel.EVENTUAL) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull.get(), true);
                } else if (notNull > Level.DELAY) {
                    annotations.put(e2ImmuAnnotationExpressions.notNull.get(), false);
                }

                boolean nullablePresent = notNull < MultiLevel.EVENTUAL;
                // a delay on notNull0 on a non-primitive will get nullable present
                annotations.put(e2ImmuAnnotationExpressions.nullable.get(), nullablePresent);
            }
        }
    }

    protected void doNotModified1(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        // @NotModified1
        annotations.put(e2ImmuAnnotationExpressions.notModified1.get(), getProperty(VariableProperty.NOT_MODIFIED_1) == Level.TRUE);
    }

    protected void doSize(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        List<Expression> parameters = new ArrayList<>(3);

        // collect annotations
        // size copy
        int sizeCopy = getProperty(VariableProperty.SIZE_COPY);
        if (sizeCopy == Level.SIZE_COPY_MIN_TRUE) {
            parameters.add(new MemberValuePair("copyMin", new BooleanConstant(primitives, true)));
        } else if (sizeCopy == Level.SIZE_COPY_TRUE) {
            parameters.add(new MemberValuePair("copy", new BooleanConstant(primitives, true)));
        }

        // size
        int size = getProperty(VariableProperty.SIZE);
        if (size >= Level.IS_A_SIZE) {
            if (Level.haveEquals(size)) {
                parameters.add(new MemberValuePair("equals", new IntConstant(primitives, Level.decodeSizeEquals(size))));
            } else {
                parameters.add(new MemberValuePair("min", new IntConstant(primitives, Level.decodeSizeMin(size))));
            }
        }

        // size out
        int sizeOut = getProperty(VariableProperty.SIZE_OUT);
        if (sizeOut >= Level.IS_A_SIZE) { // implies parameter is modifying
            if (Level.haveEquals(sizeOut)) {
                parameters.add(new MemberValuePair("outEquals", new IntConstant(primitives, Level.decodeSizeEquals(sizeOut))));
            } else {
                parameters.add(new MemberValuePair("outMin", new IntConstant(primitives, Level.decodeSizeMin(sizeOut))));
            }
        }
        AnnotationExpression ae = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.size.get().typeInfo, parameters);
        annotations.put(ae, true);
    }

    protected void doImmutableContainer(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, int immutable, boolean betterThanFormal) {
        int container = getProperty(VariableProperty.CONTAINER);
        String mark;
        boolean isType = this instanceof TypeAnalysis;
        boolean isInterface = isType && ((TypeAnalysisImpl.Builder) this).typeInfo.isInterface();
        boolean eventual = isType && ((TypeAnalysis) this).isEventual();
        if (eventual) {
            mark = ((TypeAnalysis) this).allLabelsRequiredForImmutable();
        } else mark = "";
        Map<Class<?>, Map<String, String>> map = GenerateAnnotationsImmutable.generate(immutable, container, isType, isInterface,
                mark, betterThanFormal);
        for (Map.Entry<Class<?>, Map<String, String>> entry : map.entrySet()) {
            List<Expression> list;
            if (entry.getValue() == GenerateAnnotationsImmutable.TRUE) {
                list = List.of();
            } else {
                list = entry.getValue().entrySet().stream().map(e -> new MemberValuePair(e.getKey(),
                        new StringConstant(primitives, e.getValue()))).collect(Collectors.toList());
            }
            AnnotationExpression expression = AnnotationExpression.fromAnalyserExpressions(
                    e2ImmuAnnotationExpressions.getFullyQualified(entry.getKey().getCanonicalName()), list);
            annotations.put(expression, true);
        }
    }

    protected void doIndependent(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions, int independent, boolean isInterface) {
        if (independent == MultiLevel.FALSE || !isInterface && independent == MultiLevel.DELAY) {
            annotations.put(e2ImmuAnnotationExpressions.independent.get(), false);
            annotations.put(e2ImmuAnnotationExpressions.dependent.get(), true);
            return;
        }
        if (independent <= MultiLevel.FALSE) return;
        annotations.put(e2ImmuAnnotationExpressions.dependent.get(), false);
        if (independent == MultiLevel.EFFECTIVE) {
            annotations.put(e2ImmuAnnotationExpressions.independent.get(), true);
            return;
        }
        boolean eventual = this instanceof TypeAnalysis && ((TypeAnalysis) this).isEventual();
        if (!eventual) throw new UnsupportedOperationException("??");
        String mark = ((TypeAnalysis) this).allLabelsRequiredForImmutable();
        AnnotationExpression ae = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.independent.get().typeInfo,
                List.of(new MemberValuePair("after", new StringConstant(primitives, mark))));
        annotations.put(ae, true);
    }

    public Messages fromAnnotationsIntoProperties(boolean acceptVerify,
                                                  List<AnnotationExpression> annotations,
                                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        int immutable = -1;
        int notNull = -1;
        boolean container = false;
        Messages messages = new Messages();

        AnnotationExpression only = null;
        AnnotationExpression mark = null;

        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationType annotationType = Analysis.e2immuAnnotation(annotationExpression);
            if (annotationType == AnnotationType.CONTRACT ||
                    // VERIFY is the default in annotated APIs, and non-default method declarations in interfaces...
                    acceptVerify && annotationType == AnnotationType.VERIFY) {
                TypeInfo t = annotationExpression.typeInfo;
                if (e2ImmuAnnotationExpressions.e1Immutable.get().typeInfo == t) {
                    immutable = Math.max(0, immutable);
                } else if (e2ImmuAnnotationExpressions.mutableModifiesArguments.get().typeInfo == t) {
                    immutable = -1;
                    container = false;
                } else if (e2ImmuAnnotationExpressions.e2Immutable.get().typeInfo == t) {
                    immutable = 1;
                } else if (e2ImmuAnnotationExpressions.e2Container.get().typeInfo == t) {
                    immutable = 1;
                    container = true;
                } else if (e2ImmuAnnotationExpressions.e1Container.get().typeInfo == t) {
                    immutable = Math.max(0, immutable);
                    container = true;
                } else if (e2ImmuAnnotationExpressions.container.get().typeInfo == t) {
                    container = true;
                } else if (e2ImmuAnnotationExpressions.nullable.get().typeInfo == t) {
                    notNull = MultiLevel.NULLABLE;
                } else if (e2ImmuAnnotationExpressions.notNull.get().typeInfo == t) {
                    notNull = MultiLevel.EFFECTIVELY_NOT_NULL;
                } else if (e2ImmuAnnotationExpressions.notNull1.get().typeInfo == t) {
                    notNull = MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                } else if (e2ImmuAnnotationExpressions.notNull2.get().typeInfo == t) {
                    notNull = MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL;
                } else if (e2ImmuAnnotationExpressions.notModified.get().typeInfo == t) {
                    properties.put(VariableProperty.MODIFIED, Level.FALSE);
                } else if (e2ImmuAnnotationExpressions.modified.get().typeInfo == t) {
                    properties.put(VariableProperty.MODIFIED, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.effectivelyFinal.get().typeInfo == t) {
                    properties.put(VariableProperty.FINAL, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.variableField.get().typeInfo == t) {
                    properties.put(VariableProperty.FINAL, Level.FALSE);
                } else if (e2ImmuAnnotationExpressions.constant.get().typeInfo == t) {
                    properties.put(VariableProperty.CONSTANT, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.extensionClass.get().typeInfo == t) {
                    properties.put(VariableProperty.EXTENSION_CLASS, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.fluent.get().typeInfo == t) {
                    properties.put(VariableProperty.FLUENT, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.identity.get().typeInfo == t) {
                    properties.put(VariableProperty.IDENTITY, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.ignoreModifications.get().typeInfo == t) {
                    properties.put(VariableProperty.IGNORE_MODIFICATIONS, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.independent.get().typeInfo == t) {
                    properties.put(VariableProperty.INDEPENDENT, MultiLevel.EFFECTIVE);
                } else if (e2ImmuAnnotationExpressions.dependent.get().typeInfo == t) {
                    properties.put(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                } else if (e2ImmuAnnotationExpressions.mark.get().typeInfo == t) {
                    mark = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.only.get().typeInfo == t) {
                    only = annotationExpression;
                } else if (e2ImmuAnnotationExpressions.singleton.get().typeInfo == t) {
                    properties.put(VariableProperty.SINGLETON, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.utilityClass.get().typeInfo == t) {
                    properties.put(VariableProperty.UTILITY_CLASS, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.linked.get().typeInfo == t) {
                    properties.put(VariableProperty.LINKED, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.notModified1.get().typeInfo == t) {
                    properties.put(VariableProperty.NOT_MODIFIED_1, Level.TRUE);
                } else if (e2ImmuAnnotationExpressions.size.get().typeInfo == t) {
                    properties.put(VariableProperty.SIZE, extractSizeMin("min", "equals", messages, annotationExpression));
                    properties.put(VariableProperty.SIZE_COPY, extractSizeCopy(annotationExpression));
                    properties.put(VariableProperty.SIZE_OUT, extractSizeMin("outMin", "outEquals", messages, annotationExpression));
                } else if (e2ImmuAnnotationExpressions.precondition.get().typeInfo == t) {
                    //String value = annotationExpression.extract("value", "");
                    throw new UnsupportedOperationException("Not yet implemented");
                } else throw new UnsupportedOperationException("TODO: " + t.fullyQualifiedName);
            }
        }
        if (container) {
            properties.put(VariableProperty.CONTAINER, Level.TRUE);
        }
        if (immutable >= 0) {
            int value = switch (immutable) {
                case 0 -> MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                case 1 -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                default -> throw new UnsupportedOperationException();
            };
            properties.put(VariableProperty.IMMUTABLE, value);
        }
        if (notNull >= 0) {
            properties.put(VariableProperty.NOT_NULL, notNull);
        }
        if (mark != null && only == null) {
            String markValue = mark.extract("value", "");
            List<Value> values = safeSplit(markValue).stream().map(ContractMark::new).collect(Collectors.toList());
            ((MethodAnalysisImpl.Builder) this).writeMarkAndOnly(new MethodAnalysis.MarkAndOnly(values, markValue, true, null));
        } else if (only != null) {
            String markValue = mark == null ? null : mark.extract("value", "");
            String before = only.extract("before", "");
            String after = only.extract("after", "");
            //boolean framework = only.extract("framework", false); // TODO! implement
            boolean isAfter = before.isEmpty();
            String onlyMark = isAfter ? after : before;
            if (markValue != null && !onlyMark.equals(markValue)) {
                LOGGER.warn("Have both @Only and @Mark, with different values? {} vs {}", onlyMark, markValue);
            }
            List<Value> values = safeSplit(onlyMark).stream().map(ContractMark::new).collect(Collectors.toList());
            ((MethodAnalysisImpl.Builder) this).writeMarkAndOnly(new MethodAnalysis.MarkAndOnly(values, onlyMark, mark != null, isAfter));
        }
        return messages;
    }

    private static List<String> safeSplit(String s) {
        String[] ss = s.split(",\\s*");
        return Arrays.stream(ss).filter(l -> l.trim().isEmpty()).collect(Collectors.toList());
    }

    /**
     * Values: -1 = absent; 0 = NOT A SIZE; 1 = min=0,(is a size);  2 = equals 0 (empty) ; 3 = min 1 (not empty); 4 = equals 1; 5 = min 2; 6 = equals 2
     *
     * @param annotationExpression the annotation
     * @return encoded value
     */
    public int extractSizeMin(String minString, String equalsString, Messages messages, AnnotationExpression annotationExpression) {
        Integer min = annotationExpression.extract(minString, -1);
        if (min >= 0) {
            // min = 0 is FALSE; min = 1 means FALSE at level 1 (value 2), min = 2 means FALSE at level 2 (value 4)
            return Level.encodeSizeMin(min);
        }
        Integer equals = annotationExpression.extract(equalsString, -1);
        if (equals >= 0) {
            // equals 0 means TRUE at level 0, equals 1 means TRUE at level 1 (value 3)

            // @Size is the default
            return Level.encodeSizeEquals(equals);
        }
        // ignore! raise warning
        messages.add(Message.newMessage(location(), Message.SIZE_NEED_PARAMETER));
        return Level.DELAY;
    }

    public static int extractSizeCopy(AnnotationExpression annotationExpression) {
        Boolean copy = annotationExpression.extract("copy", false);
        if (copy) return Level.SIZE_COPY_TRUE;
        Boolean copyMin = annotationExpression.extract("copyMin", false);
        if (copyMin) return Level.SIZE_COPY_MIN_TRUE;
        return Level.FALSE;
    }

    public Map<VariableProperty, Integer> getProperties(Set<VariableProperty> properties) {
        Map<VariableProperty, Integer> res = new HashMap<>();
        for (VariableProperty property : properties) {
            int value = getProperty(property);
            res.put(property, value);
        }
        return res;
    }


    public interface Modification extends Runnable {
    }

    public class SetProperty implements Modification {
        public final VariableProperty variableProperty;
        public int value;

        public SetProperty(VariableProperty variableProperty, int value) {
            this.value = value;
            this.variableProperty = variableProperty;
        }

        @Override
        public void run() {
            setProperty(variableProperty, value);
        }
    }
}
