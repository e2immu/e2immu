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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.annotationxml.model.FieldItem;
import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.annotationxml.model.TypeItem;
import org.e2immu.analyser.bytecode.ExpressionFactory;
import org.e2immu.analyser.bytecode.JetBrainsAnnotationTranslator;
import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM7;

public class MyClassVisitor extends ClassVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyClassVisitor.class);

    private final List<TypeInfo> types;
    private final TypeContext typeContext;
    private final OnDemandInspection onDemandInspection;
    private final AnnotationStore annotationStore;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final Stack<TypeInfo> enclosingTypes;
    private final Set<TypeInfo> inProcess;
    private TypeInfo currentType;
    private String currentTypePath;
    private boolean currentTypeIsInterface;
    private TypeInspection.TypeInspectionBuilder typeInspectionBuilder;

    public MyClassVisitor(OnDemandInspection onDemandInspection,
                          AnnotationStore annotationStore,
                          TypeContext typeContext,
                          E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                          List<TypeInfo> types,
                          Set<TypeInfo> inProcess,
                          Stack<TypeInfo> enclosingTypes) {
        super(ASM7);
        this.types = types;
        this.enclosingTypes = enclosingTypes;
        this.inProcess = inProcess;
        this.typeContext = typeContext;
        this.onDemandInspection = onDemandInspection;
        this.annotationStore = annotationStore;
        jetBrainsAnnotationTranslator = annotationStore != null ? new JetBrainsAnnotationTranslator(typeContext.getPrimitives(),
                e2ImmuAnnotationExpressions) : null;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        log(BYTECODE_INSPECTOR_DEBUG, "Visit {} {} {} {} {} {}", version, access, name, signature, superName, interfaces);
        int dollar = name.indexOf('$');
        if (dollar >= 0 && enclosingTypes.isEmpty()) {
            currentType = null;
            currentTypePath = null;
            return;
        }
        String fqName = pathToFqn(name);
        currentType = typeContext.typeStore.get(fqName);
        if (currentType == null) {
            currentType = new TypeInfo(fqName);
            typeContext.typeStore.add(currentType);
        } else if (currentType.typeInspection.isSet()) {
            log(BYTECODE_INSPECTOR_DEBUG, "Inspection of " + fqName + " has been set already");
            types.add(currentType);
            currentType = null;
            currentTypePath = null;
            return;
        }
        inProcess.add(currentType);
        currentTypePath = name;
        typeInspectionBuilder = new TypeInspection.TypeInspectionBuilder();

        // may be overwritten, but this is the default
        typeInspectionBuilder.setParentClass(typeContext.getPrimitives().objectParameterizedType);

        TypeNature currentTypeNature = typeNatureFromOpCode(access);
        typeInspectionBuilder.setTypeNature(currentTypeNature);
        if (!enclosingTypes.isEmpty()) {
            typeInspectionBuilder.setEnclosingType(enclosingTypes.peek());
        } else {
            typeInspectionBuilder.setPackageName(packageName(fqName));
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PROTECTED);
        if ((access & Opcodes.ACC_PUBLIC) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PUBLIC);

        currentTypeIsInterface = currentTypeNature == TypeNature.INTERFACE;

        if (currentTypeNature == TypeNature.CLASS) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.ABSTRACT);
            if ((access & Opcodes.ACC_FINAL) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.FINAL);
        }

        String parentFqName = superName == null ? null : pathToFqn(superName);
        boolean haveParentType = superName != null && !Primitives.JAVA_LANG_OBJECT.equals(parentFqName);

        if (signature == null) {
            if (haveParentType) {
                TypeInfo typeInfo = mustFindTypeInfo(parentFqName, superName);
                if (typeInfo == null) {
                    log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, parent type {} unknown",
                            currentType.fullyQualifiedName, parentFqName);
                    errorStateForType(parentFqName);
                    return;
                }
                typeInspectionBuilder.setParentClass(typeInfo.asParameterizedType());
            }
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    String fqn = pathToFqn(interfaceName);
                    TypeInfo typeInfo = mustFindTypeInfo(fqn, interfaceName);
                    if (typeInfo == null) {
                        log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, interface type {} unknown",
                                currentType.fullyQualifiedName, fqn);
                        errorStateForType(fqn);
                        return;
                    }
                    typeInspectionBuilder.addInterfaceImplemented(typeInfo.asParameterizedType());
                }
            }
        } else {
            int pos = 0;
            if (signature.charAt(0) == '<') {
                pos = parseTypeGenerics(signature) + 1;
            }
            {
                ParameterizedTypeFactory.Result res = ParameterizedTypeFactory.from(typeContext,
                        this::mustFindTypeInfo, signature.substring(pos));
                if (res == null) {
                    log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, parent type unknown",
                            currentType.fullyQualifiedName);
                    errorStateForType(parentFqName);
                    return;
                }
                if (typeContext.getPrimitives().objectTypeInfo != res.parameterizedType.typeInfo) {
                    typeInspectionBuilder.setParentClass(res.parameterizedType);
                }
                pos += res.nextPos;
            }
            if (interfaces != null) {
                for (int i = 0; i < interfaces.length; i++) {
                    ParameterizedTypeFactory.Result interFaceRes = ParameterizedTypeFactory.from(typeContext,
                            this::mustFindTypeInfo, signature.substring(pos));
                    if (interFaceRes == null) {
                        log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, interface type unknown",
                                currentType.fullyQualifiedName);
                        errorStateForType(parentFqName);
                        return;
                    }
                    if (typeContext.getPrimitives().objectTypeInfo != interFaceRes.parameterizedType.typeInfo) {
                        typeInspectionBuilder.addInterfaceImplemented(interFaceRes.parameterizedType);
                    }
                    pos += interFaceRes.nextPos;
                }
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(fqName);
            if (typeItem != null && !typeItem.getAnnotations().isEmpty()) {
                jetBrainsAnnotationTranslator.mapAnnotations(typeItem.getAnnotations(), typeInspectionBuilder);
            }
        }
    }

    /**
     * Both parameters are two versions of the same type reference
     *
     * @param fqn  dot-separated
     * @param path / and $ separated
     * @return the type
     */
    private TypeInfo mustFindTypeInfo(String fqn, String path) {
        if (path.equals(currentTypePath)) return currentType;
        TypeInfo alreadyKnown = typeContext.typeStore.get(fqn);
        if (alreadyKnown != null && alreadyKnown.typeInspection.isSet()) {
            return alreadyKnown;
        }
        if (alreadyKnown != null) {
            if (inProcess.contains(alreadyKnown)) return alreadyKnown;
        }

        // let's look at the super-name... is it part of the same primary type?
        TypeInfo parentType = enclosingTypes.isEmpty() ? null : enclosingTypes.peek();
        if (parentType != null && parentType.fullyQualifiedName.startsWith(fqn)) {
            // the parent is in the hierarchy of objects... we should definitely NOT inspect
            TypeInfo inHierarchy = inEnclosingTypes(fqn);
            if (inHierarchy != null) return inHierarchy;
        }
        if (parentType != null && isDirectChildOf(fqn, parentType.fullyQualifiedName)) {
            onDemandInspection.inspectFromPath(path, inProcess, enclosingTypes, typeContext);
        } else {
            onDemandInspection.inspectFromPath(path, inProcess);
        }
        // try again... result can be null or not inspected, in case the path is not on the classpath
        TypeInfo result = typeContext.typeStore.get(fqn);
        return result != null && result.typeInspection.isSet() ? result : null;
    }

    private static final Pattern ILLEGAL_IN_FQN = Pattern.compile("[/;$]");

    private TypeInfo getOrCreateTypeInfo(String fqn, String path) {
        Matcher m = ILLEGAL_IN_FQN.matcher(fqn);
        if (m.find()) throw new UnsupportedOperationException("Illegal FQN: " + fqn + "; path is " + path);
        // this causes really heavy recursions: return mustFindTypeInfo(fqn, path);
        return typeContext.typeStore.getOrCreate(fqn);
    }

    private TypeInfo inEnclosingTypes(String parentFqName) {
        for (TypeInfo typeInfo : enclosingTypes) {
            if (typeInfo.fullyQualifiedName.equals(parentFqName)) return typeInfo;
        }
        // Example of this situation: java.util.Comparators$NullComparator is being parsed, but Comparator itself
        // has not been seen yet.
        log(BYTECODE_INSPECTOR_DEBUG, "Could not find " + parentFqName + " in stack of enclosing types " +
                enclosingTypes.stream().map(ti -> ti.fullyQualifiedName).collect(Collectors.joining(" -> ")));
        return null;
    }

    // return true when child = parent + $ + somethingWithoutDollars
    static boolean isDirectChildOf(String child, String parent) {
        if (!child.startsWith(parent)) return false;
        int dollar = parent.length();
        if (child.length() <= dollar + 1) return false;
        int otherDollar = child.indexOf('$', dollar + 1);
        return otherDollar < 0;
    }

    public static String pathToFqn(String path) {
        String withoutDotClass = path.endsWith(".class") ? path.substring(0, path.length() - 6) : path;
        return withoutDotClass.replaceAll("[/$]", ".");
    }

    private static class IterativeParsing {
        int startPos;
        int endPos;
        ParameterizedType result;
        boolean more;
        String name;
        boolean typeNotFoundError;
    }

    private int parseTypeGenerics(String signature) {
        IterativeParsing iterativeParsing = new IterativeParsing();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing,
                        name -> {
                            TypeParameter typeParameter = new TypeParameter(currentType, name, index.getAndIncrement());
                            typeInspectionBuilder.addTypeParameter(typeParameter);
                            typeContext.addToContext(typeParameter);
                            return typeParameter;
                        }, typeContext,
                        this::mustFindTypeInfo);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing();
        }
        return iterativeParsing.endPos;
    }

    private IterativeParsing iterativelyParseGenerics(String signature,
                                                      IterativeParsing iterativeParsing,
                                                      Function<String, TypeParameter> onTypeParameterName,
                                                      TypeContext typeContext,
                                                      FindType findType) {
        int colon = signature.indexOf(':', iterativeParsing.startPos);
        int afterColon = colon + 1;
        boolean typeNotFoundError = iterativeParsing.typeNotFoundError;
        // example for extends keyword: sig='<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)TT;' for
        // method getAnnotation in java.lang.reflect.AnnotatedElement

        String name = signature.substring(iterativeParsing.startPos, colon);
        TypeParameter typeParameter = onTypeParameterName.apply(name);
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext, findType,
                signature.substring(afterColon));
        if (result == null) return null; // unable to load type
        List<ParameterizedType> typeBounds;
        if (result.parameterizedType.typeInfo != null && typeContext.getPrimitives().objectTypeInfo == result.parameterizedType.typeInfo) {
            typeBounds = List.of();
        } else {
            typeBounds = List.of(result.parameterizedType);
        }
        typeParameter.typeParameterInspection.set(new TypeParameterInspection(typeBounds));

        int end = result.nextPos + afterColon;
        char atEnd = signature.charAt(end);
        IterativeParsing next = new IterativeParsing();
        next.typeNotFoundError = typeNotFoundError || result.typeNotFoundError;
        next.name = name;
        next.result = result.parameterizedType;

        if ('>' == atEnd) {
            next.more = false;
            next.endPos = end;
        } else {
            next.more = true;
            next.startPos = end;
        }
        return next;
    }


    private static String packageName(String fqName) {
        int dot = fqName.lastIndexOf('.');
        if (dot < 0) return fqName;
        return fqName.substring(0, dot);
    }

    private static TypeNature typeNatureFromOpCode(int opCode) {
        if ((opCode & Opcodes.ACC_ANNOTATION) != 0) return TypeNature.ANNOTATION;
        if ((opCode & Opcodes.ACC_ENUM) != 0) return TypeNature.ENUM;
        if ((opCode & Opcodes.ACC_INTERFACE) != 0) return TypeNature.INTERFACE;
        return TypeNature.CLASS;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (currentType == null) return null;
        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        log(BYTECODE_INSPECTOR_DEBUG, "Field {} {} desc='{}' sig='{}' {} synthetic? {}", access,
                name, descriptor, signature, value, synthetic);
        if (synthetic) return null;

        ParameterizedType type = ParameterizedTypeFactory.from(typeContext,
                this::getOrCreateTypeInfo,
                signature != null ? signature : descriptor).parameterizedType;

        FieldInfo fieldInfo = new FieldInfo(type, name, currentType);
        FieldInspection.FieldInspectionBuilder fieldInspectionBuilder = new FieldInspection.FieldInspectionBuilder();

        if ((access & Opcodes.ACC_STATIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.STATIC);
        if ((access & Opcodes.ACC_PUBLIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PUBLIC);
        if ((access & Opcodes.ACC_PRIVATE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) fieldInspectionBuilder.addModifier(FieldModifier.FINAL);
        if ((access & Opcodes.ACC_VOLATILE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.VOLATILE);

        if (value != null) {
            Expression expression = ExpressionFactory.from(typeContext, value);
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                fieldInspectionBuilder.setInitializer(expression);
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                FieldItem fieldItem = typeItem.getFieldItems().get(name);
                if (fieldItem != null && !fieldItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(fieldItem.getAnnotations(), fieldInspectionBuilder);
                }
            }
        }

        return new MyFieldVisitor(typeContext, fieldInfo, fieldInspectionBuilder, typeInspectionBuilder);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (currentType == null) return null;

        if (name.startsWith("lambda$") || name.equals("<clinit>")) return null;
        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;

        log(BYTECODE_INSPECTOR_DEBUG, "Method {} {} desc='{}' sig='{}' {} synthetic? {}", access, name,
                descriptor, signature, Arrays.toString(exceptions), synthetic);
        if (synthetic) return null;

        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

        MethodInfo methodInfo;
        if ("<init>".equals(name)) {
            methodInfo = new MethodInfo(currentType, List.of());
        } else {
            methodInfo = new MethodInfo(currentType, name, isStatic);
        }
        MethodInspection.MethodInspectionBuilder methodInspectionBuilder = new MethodInspection.MethodInspectionBuilder();

        if ((access & Opcodes.ACC_PUBLIC) != 0 && !currentTypeIsInterface) {
            methodInspectionBuilder.addModifier(MethodModifier.PUBLIC);
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) methodInspectionBuilder.addModifier(MethodModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) methodInspectionBuilder.addModifier(MethodModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) methodInspectionBuilder.addModifier(MethodModifier.FINAL);
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        if (isAbstract && (!currentTypeIsInterface || isStatic))
            methodInspectionBuilder.addModifier(MethodModifier.ABSTRACT);
        boolean lastParameterIsVarargs = (access & Opcodes.ACC_VARARGS) != 0;

        TypeContext methodContext = new TypeContext(typeContext);

        String signatureOrDescription = signature != null ? signature : descriptor;
        if (signatureOrDescription.startsWith("<")) {
            int end = parseMethodGenerics(signatureOrDescription, methodInfo, methodInspectionBuilder, methodContext);
            if (end < 0) {
                // error state
                errorStateForType(signatureOrDescription);
                return null; // dropping the method, and the type!
            }
            signatureOrDescription = signatureOrDescription.substring(end + 1); // 1 to get rid of >
        }
        List<ParameterizedType> types = parseParameterTypesOfMethod(methodContext, signatureOrDescription);
        methodInspectionBuilder.setReturnType(types.get(types.size() - 1));

        MethodItem methodItem = null;
        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                String methodSignature = makeMethodSignature(name, currentType, types.subList(0, types.size() - 1));
                methodItem = typeItem.getMethodItems().get(methodSignature);
                if (methodItem != null && !methodItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(methodItem.getAnnotations(), methodInspectionBuilder);
                }
            }
        }
        return new MyMethodVisitor(methodContext, methodInfo, methodInspectionBuilder, typeInspectionBuilder, types,
                lastParameterIsVarargs, methodItem, jetBrainsAnnotationTranslator);
    }

    // result should be
    // entrySet()                                       has a complicated return type, but that is skipped
    // addFirst(E)                                      type parameter of interface/class as first argument
    // ArrayList(java.util.Collection<? extends E>)     this is a constructor
    // copyOf(U[], int, java.lang.Class<? extends T[]>) spaces between parameter types

    private static String makeMethodSignature(String name, TypeInfo typeInfo, List<ParameterizedType> types) {
        String methodName = "<init>".equals(name) ? typeInfo.simpleName : name;
        return methodName + "(" +
                types.stream().map(ParameterizedType::detailedString).collect(Collectors.joining(", ")) +
                ")";
    }

    private int parseMethodGenerics(String signature,
                                    MethodInfo methodInfo,
                                    MethodInspection.MethodInspectionBuilder methodInspectionBuilder,
                                    TypeContext methodContext) {
        IterativeParsing iterativeParsing = new IterativeParsing();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing, name -> {
                            TypeParameter typeParameter = new TypeParameter(methodInfo, name, index.getAndIncrement());
                            methodInspectionBuilder.addTypeParameter(typeParameter);
                            methodContext.addToContext(typeParameter);
                            return typeParameter;
                        },
                        methodContext,
                        this::getOrCreateTypeInfo);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing();
        }
        return iterativeParsing.endPos;
    }

    private List<ParameterizedType> parseParameterTypesOfMethod(TypeContext typeContext, String signature) {
        if (signature.startsWith("()")) {
            return List.of(ParameterizedTypeFactory.from(typeContext,
                    this::getOrCreateTypeInfo, signature.substring(2)).parameterizedType);
        }
        List<ParameterizedType> methodTypes = new ArrayList<>();

        IterativeParsing iterativeParsing = new IterativeParsing();
        iterativeParsing.startPos = 1;
        do {
            iterativeParsing = iterativelyParseMethodTypes(typeContext, signature, iterativeParsing);
            methodTypes.add(iterativeParsing.result);
        } while (iterativeParsing.more);
        return methodTypes;
    }

    private IterativeParsing iterativelyParseMethodTypes(TypeContext typeContext, String signature, IterativeParsing iterativeParsing) {
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext,
                this::getOrCreateTypeInfo, signature.substring(iterativeParsing.startPos));
        int end = iterativeParsing.startPos + result.nextPos;
        IterativeParsing next = new IterativeParsing();
        next.result = result.parameterizedType;
        if (end >= signature.length()) {
            next.more = false;
            next.endPos = end;
        } else {
            char atEnd = signature.charAt(end);
            if (atEnd == '^') {
                // NOTE NOTE: this marks the "throws" block, which we're NOT parsing at the moment!!
                next.more = false;
                next.endPos = end;
            } else {
                next.more = true;
                if (atEnd == ')') {
                    next.startPos = end + 1;
                } else {
                    next.startPos = end;
                }
            }
        }
        return next;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (currentType == null) return;

        log(BYTECODE_INSPECTOR_DEBUG, "Visit inner class {} {} {} {}", name, outerName, innerName, access);
        if (name.equals(currentTypePath)) {
            if ((access & Opcodes.ACC_STATIC) != 0) {
                log(BYTECODE_INSPECTOR_DEBUG, "Mark subtype {} as static", name);
                typeInspectionBuilder.addTypeModifier(TypeModifier.STATIC);
            }
        } else if (innerName != null && currentTypePath.equals(outerName)) {
            log(BYTECODE_INSPECTOR_DEBUG, "Processing sub-type {} of {}", name, currentType.fullyQualifiedName);
            TypeInfo subTypeInMap = typeContext.typeStore.get(pathToFqn(name));
            if (subTypeInMap == null || !subTypeInMap.typeInspection.isSet() && !inProcess.contains(subTypeInMap)) {
                enclosingTypes.push(currentType);
                TypeInfo subType = onDemandInspection.inspectFromPath(name, inProcess, enclosingTypes, typeContext);
                enclosingTypes.pop();
                if (subType != null) {
                    typeInspectionBuilder.addSubType(subType);
                } else {
                    errorStateForType(name);
                }
            }
        }
    }

    // not overriding visitOuterClass

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (currentType == null) return null;

        log(BYTECODE_INSPECTOR_DEBUG, "Have class annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, descriptor, typeInspectionBuilder);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        if (currentType == null) return null;

        log(BYTECODE_INSPECTOR_DEBUG, "Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitEnd() {
        if (currentType != null) {
            try {
                log(BYTECODE_INSPECTOR_DEBUG, "Visit end of class " + currentType.fullyQualifiedName);
                if (typeInspectionBuilder == null)
                    throw new UnsupportedOperationException("? was expecting a type inspection builder");
                currentType.typeInspection.set(typeInspectionBuilder.build(false, currentType));
                types.add(currentType);
                inProcess.remove(currentType);
                currentType = null;
                typeInspectionBuilder = null;
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception bytecode inspecting type {}", currentType.fullyQualifiedName);
                throw rte;
            }
        }
    }

    private void errorStateForType(String pathCausingFailure) {
        if (currentType == null || currentType.typeInspection.isSet())
            throw new UnsupportedOperationException();
        String message = "Unable to inspect " + currentType.fullyQualifiedName + ": Cannot load " + pathCausingFailure;
        log(BYTECODE_INSPECTOR, message);
        currentType.typeInspection.setRunnable(() -> {
            throw new RuntimeException(message);
        });
        inProcess.remove(currentType);
        currentType = null;
        typeInspectionBuilder = null;
    }
}
