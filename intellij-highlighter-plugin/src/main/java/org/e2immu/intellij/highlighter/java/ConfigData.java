package org.e2immu.intellij.highlighter.java;

import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;

/**
 * NOTE: if the setters are not present, IntelliJ will not save the preferences
 * So we can make it eventually immutable
 */
@E2Immutable(after = "preferences")
public class ConfigData {
    // presence needed for Xml Serialization
    public ConfigData() {
        this(true, true, true,
                "http://localhost:8281", "default");
    }

    public ConfigData(boolean isHighlightDeclarations,
                      boolean isHighlightUnknownTypes,
                      boolean isHighlightStatements,
                      String annotationServerUrl,
                      String annotationProject) {
        this.isHighlightDeclarations = isHighlightDeclarations;
        this.isHighlightStatements = isHighlightStatements;
        this.isHighlightUnknownTypes = isHighlightUnknownTypes;
        this.annotationServerUrl = annotationServerUrl;
        this.annotationProject = annotationProject;
    }

    private boolean isHighlightDeclarations;
    private boolean isHighlightUnknownTypes;
    private boolean isHighlightStatements;
    private String annotationServerUrl;
    private String annotationProject;

    public String getAnnotationProject() {
        return annotationProject;
    }

    public String getAnnotationServerUrl() {
        return annotationServerUrl;
    }

    public boolean isHighlightDeclarations() {
        return isHighlightDeclarations;
    }

    public boolean isHighlightStatements() {
        return isHighlightStatements;
    }

    public boolean isHighlightUnknownTypes() {
        return isHighlightUnknownTypes;
    }

    @Mark("preferences")
    @Only(framework = true)
    public void setAnnotationProject(String annotationProject) {
        this.annotationProject = annotationProject;
    }

    @Mark("preferences")
    @Only(framework = true)
    public void setAnnotationServerUrl(String annotationServerUrl) {
        this.annotationServerUrl = annotationServerUrl;
    }

    @Mark("preferences")
    @Only(framework = true)
    public void setHighlightDeclarations(boolean highlightDeclarations) {
        isHighlightDeclarations = highlightDeclarations;
    }

    @Mark("preferences")
    @Only(framework = true)
    public void setHighlightStatements(boolean highlightStatements) {
        isHighlightStatements = highlightStatements;
    }

    @Mark("preferences")
    @Only(framework = true)
    public void setHighlightUnknownTypes(boolean highlightUnknownTypes) {
        isHighlightUnknownTypes = highlightUnknownTypes;
    }
}
