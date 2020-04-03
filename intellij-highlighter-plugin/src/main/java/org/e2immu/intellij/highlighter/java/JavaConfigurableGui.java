package org.e2immu.intellij.highlighter.java;

import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import org.e2immu.intellij.highlighter.Bundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JavaConfigurableGui {
    private JPanel rootPanel;
    private JBLabel info;
    private JPanel highlightStatementsPanel;

    // two checkboxes
    private JBCheckBox isHighlightDeclarations;
    private JBCheckBox isHighlightUnknownTypes;
    private JBCheckBox isHighlightStatements;
    private JTextField annotationServerUrl;
    private JTextField annotationProject;
    private JLabel projectNameLabel;
    private JLabel annotationServerLabel;

    public JavaConfigurableGui() {
        finishUpComponents();
    }

    @NotNull
    public JPanel getRootPanel() {
        return rootPanel;
    }

    @NotNull
    public Boolean isHighlightDeclarations() {
        return isHighlightDeclarations.isSelected();
    }

    @NotNull
    public Boolean isHighlightUnknownTypes() {
        return isHighlightUnknownTypes.isSelected();
    }

    @NotNull
    public String getAnnotationProject() {
        return annotationProject.getText();
    }

    public String getAnnotationServerUrl() {
        return annotationServerUrl.getText();
    }

    @NotNull
    public Boolean isHighlightStatements() {
        return isHighlightStatements.isSelected();
    }

    public void setHighlightDeclarations(@NotNull final Boolean value) {
        isHighlightDeclarations.setSelected(value);
    }

    public void setHighlightUnknownTypes(@NotNull final Boolean value) {
        isHighlightUnknownTypes.setSelected(value);
    }

    public void setHighlightStatements(@NotNull final Boolean value) {
        isHighlightStatements.setSelected(value);
    }

    public void setAnnotationProject(@NotNull final String annotationProject) {
        this.annotationProject.setText(annotationProject);
    }

    public void setAnnotationServerUrl(@NotNull final String annotationServerUrl) {
        this.annotationServerUrl.setText(annotationServerUrl);
    }

    private void finishUpComponents() {
        info.setText(Bundle.INSTANCE.get("e2i.settings.custom.java"));
        isHighlightDeclarations.setText(Bundle.INSTANCE.get("e2i.settings.custom.java.highlightDeclarations"));
        isHighlightUnknownTypes.setText(Bundle.INSTANCE.get("e2i.settings.custom.java.highlightUnknownTypes"));
        isHighlightStatements.setText(Bundle.INSTANCE.get("e2i.settings.custom.java.highlightStatements"));
        final String simpleGettersTooltip = Bundle.INSTANCE.get("e2i.settings.custom.java.highlightStatements.tooltip");
        highlightStatementsPanel.add(ContextHelpLabel.create(simpleGettersTooltip));
        projectNameLabel.setText(Bundle.INSTANCE.get("e2i.settings.custom.global.projectName"));
        annotationServerLabel.setText(Bundle.INSTANCE.get("e2i.settings.custom.global.annotationServer"));
    }
}
