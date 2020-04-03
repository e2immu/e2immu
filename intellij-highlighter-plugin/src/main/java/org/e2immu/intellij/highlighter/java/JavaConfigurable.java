package org.e2immu.intellij.highlighter.java;

import com.intellij.openapi.options.SearchableConfigurable;
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.intellij.highlighter.Bundle;
import org.e2immu.intellij.highlighter.Constants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Container
public class JavaConfigurable implements SearchableConfigurable {

    private final JavaConfig config = JavaConfig.INSTANCE;
    private final JavaConfigurableGui gui = new JavaConfigurableGui();

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    @NotModified
    public String getDisplayName() {
        return Bundle.INSTANCE.get("e2i.app.presentableName");
    }

    @Override
    public void reset() {
        ConfigData state = config.getState();
        gui.setHighlightDeclarations(state.isHighlightDeclarations());
        gui.setHighlightStatements(state.isHighlightStatements());
        gui.setHighlightUnknownTypes(state.isHighlightUnknownTypes());
        gui.setAnnotationProject(state.getAnnotationProject());
        gui.setAnnotationServerUrl(state.getAnnotationServerUrl());
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        reset();
        return gui.getRootPanel();
    }

    @Override
    @NotModified
    public boolean isModified() {
        ConfigData state = config.getState();
        return gui.isHighlightDeclarations() != state.isHighlightDeclarations() ||
                gui.isHighlightStatements() != state.isHighlightStatements() ||
                gui.isHighlightUnknownTypes() != state.isHighlightUnknownTypes() ||
                !gui.getAnnotationProject().equals(state.getAnnotationProject()) ||
                !gui.getAnnotationServerUrl().equals(state.getAnnotationServerUrl());
    }

    @Override
    public void apply() {
        config.setState(new ConfigData(
                gui.isHighlightDeclarations(),
                gui.isHighlightUnknownTypes(),
                gui.isHighlightStatements(),
                gui.getAnnotationServerUrl(),
                gui.getAnnotationProject()));
    }

    @NotNull
    @Override
    @NotModified
    @Constant
    public String getId() {
        return "preferences" + Constants.APP_NAME;
    }
}
