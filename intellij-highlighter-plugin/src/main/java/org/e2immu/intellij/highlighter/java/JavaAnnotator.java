package org.e2immu.intellij.highlighter.java;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;

import static org.e2immu.intellij.highlighter.Constants.*;

import org.e2immu.intellij.highlighter.store.AnnotationStore;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JavaAnnotator implements Annotator {
    private static final Logger LOGGER = Logger.getInstance(JavaAnnotator.class);

    private final JavaConfig config = JavaConfig.INSTANCE;
    private final AnnotationStore annotationStore = AnnotationStore.INSTANCE;

    private final Map<String, TextAttributes> textAttributesMap =
            TAK_MAP.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> EditorColorsManager.getInstance().getGlobalScheme().getAttributes(e.getValue())));

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ConfigData configData = config.getState();
        if (element instanceof PsiIdentifier) {
            PsiIdentifier identifier = (PsiIdentifier) element;
            PsiElement parent = identifier.getParent();

            // for fields, highlight both the types and the field itself (@NotModified)
            if (configData.isHighlightDeclarations() && parent instanceof PsiField) {
                PsiField psiField = (PsiField) parent;
                // highlight the field
                if (psiField.getParent() instanceof PsiClass) {
                    PsiClass classOfField = (PsiClass) psiField.getParent();
                    String qualifiedFieldName = classOfField.getQualifiedName() + ":" + identifier.getText();
                    LOGGER.debug("Marking field " + qualifiedFieldName);
                    mark(holder, identifier, FIELD, qualifiedFieldName);
                }
                // highlight the type and type parameters
                recursivelyFindTypeElements(psiField.getTypeElement(), typeElement ->
                        handleTypeElement(holder, typeElement));

                // and handle annotations
                handleAnnotations(holder, psiField.getAnnotations(), FIELD);
            }

            // only look at the types of local variables
            if (configData.isHighlightStatements() && parent instanceof PsiLocalVariable) {
                PsiLocalVariable localVariable = (PsiLocalVariable) parent;
                LOGGER.debug("Marking local variable " + identifier.getText());

                recursivelyFindTypeElements(localVariable.getTypeElement(), typeElement ->
                        handleTypeElement(holder, typeElement));
            }
            // in statements, we may look at method calls
            if (configData.isHighlightStatements()
                    && parent instanceof PsiReferenceExpression
                    && parent.getParent() instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) parent.getParent();
                PsiMethod method = methodCallExpression.resolveMethod();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null) {
                        String qualifiedName = methodQualifiedName(containingClass, method);
                        LOGGER.debug("Marking method " + qualifiedName);

                        mark(holder, identifier, METHOD, qualifiedName);
                    }
                }
            }
            if (configData.isHighlightDeclarations() && parent instanceof PsiMethod) {
                PsiMethod psiMethod = (PsiMethod) parent;
                if (psiMethod.getParent() instanceof PsiClass) {
                    PsiClass classOfMethod = (PsiClass) psiMethod.getParent();
                    String qualifiedMethodName = methodQualifiedName(classOfMethod, psiMethod);
                    LOGGER.debug("Marking method " + qualifiedMethodName);

                    mark(holder, identifier, METHOD, qualifiedMethodName);

                    // now also do the parameters
                    int index = 0;
                    for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {

                        // first, the types of the parameters
                        recursivelyFindTypeElements(parameter.getTypeElement(),
                                typeElement -> handleTypeElement(holder, typeElement));

                        // then, the parameter itself
                        mark(holder, parameter.getIdentifyingElement(), PARAM,qualifiedMethodName + "#" + index);

                        // then, annotations on the parameter
                        handleAnnotations(holder, parameter.getAnnotations(), PARAM);
                        index++;
                    }

                    // look at the return type
                    PsiTypeElement returnTypeElement = psiMethod.getReturnTypeElement();
                    if (returnTypeElement != null) {
                        recursivelyFindTypeElements(returnTypeElement, typeElement -> handleTypeElement(holder, typeElement));
                    }

                    // and check the annotations
                    handleAnnotations(holder, psiMethod.getAnnotations(), METHOD);
                }
            }
            if (configData.isHighlightDeclarations() && parent instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) parent;
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName != null) {
                    mark(holder, identifier, TYPE, qualifiedName);
                }
                handleAnnotations(holder, psiClass.getAnnotations(), TYPE);
            }
        }
    }

    private void handleAnnotations(AnnotationHolder holder, PsiAnnotation[] annotations, String context) {
        if(annotations == null || annotations.length ==0) return;
        for(PsiAnnotation annotation: annotations) {
            String qn = annotation.getQualifiedName();
            mark(holder, annotation.getNameReferenceElement(), context, qn);
        }
    }

    private void handleTypeElement(AnnotationHolder holder, PsiTypeElement typeElement) {
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass resolved = classType.resolve();
            if (resolved != null) {
                String qualifiedName = resolved.getQualifiedName();
                if (qualifiedName != null) {
                    mark(holder, firstChild(typeElement), TYPE, qualifiedName);
                }
            }
        }
    }

    private static String methodQualifiedName(PsiClass containingClass, PsiMethod method) {
        String name = method.getName();
        String typesCsv = Arrays.stream(method.getParameterList().getParameters())
                .map(JavaAnnotator::typeOfParameter)
                .collect(Collectors.joining(","));
        return containingClass.getQualifiedName() + "." + name + "(" + typesCsv + ")";
    }

    private static String typeOfParameter(PsiParameter psiParameter) {
        PsiType type = psiParameter.getType();
        if (type instanceof PsiPrimitiveType) {
            return ((PsiPrimitiveType) type).getName();
        }
        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();
            if (resolved == null) {
                return type.getCanonicalText();
            }
            if (resolved instanceof PsiTypeParameter) {
                PsiTypeParameter typeParameter = (PsiTypeParameter) resolved;
                String owner = (typeParameter.getOwner() instanceof PsiClass) ? "T" : "M";
                return owner + typeParameter.getIndex();
            }
            return resolved.getQualifiedName();
        }
        return type.getCanonicalText();
    }

    private static void recursivelyFindTypeElements(PsiElement element, Consumer<PsiTypeElement> whenTypeElement) {
        if (element instanceof PsiTypeElement) whenTypeElement.accept((PsiTypeElement) element);
        for (PsiElement child : element.getChildren()) {
            recursivelyFindTypeElements(child, whenTypeElement);
        }
    }

    private static PsiElement firstChild(PsiElement element) {
        if (element == null) return null;
        if (element.getFirstChild() != null) return firstChild(element.getFirstChild());
        return element;
    }

    private void mark(AnnotationHolder holder, PsiElement element, String context, String elementFQN) {
        annotationStore.mapAndMark(elementFQN, context, annotationName -> {
            TextAttributes textAttributes = textAttributesMap.get(annotationName);
            if (textAttributes != null && (config.getState().isHighlightUnknownTypes() || isValidAnnotation(annotationName))) {
                Annotation annotation = holder.createInfoAnnotation(element, null);
                annotation.setEnforcedTextAttributes(textAttributes);
            }
        });
    }

    private static boolean isValidAnnotation(String annotationName) {
        return !NOT_ANNOTATED_FIELD.equals(annotationName) &&
                !NOT_ANNOTATED_METHOD.equals(annotationName) &&
                !NOT_ANNOTATED_PARAM.equals(annotationName) &&
                !NOT_ANNOTATED_TYPE.equals(annotationName);
    }

}
