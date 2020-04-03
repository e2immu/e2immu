package org.e2immu.intellij.highlighter.settings;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.e2immu.intellij.highlighter.Bundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ConfigurableGui {
    private JPanel rootPanel;
    private JPanel listPanel;

    @NotNull
    public JPanel getRootPanel() {
        return rootPanel;
    }

    @NotNull
    public JPanel getListPanel() {
        return listPanel;
    }

    private void createUIComponents() {
        final JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BorderLayout());
        rootPanel.add(
                new JBLabel(Bundle.INSTANCE.get("e2i.settings.custom.global")),
                BorderLayout.NORTH
        );

        final JPanel listPanel = buildListPanel();
        rootPanel.add(listPanel, BorderLayout.WEST);

        this.rootPanel = rootPanel;
        this.listPanel = listPanel;
    }

    private JPanel buildListPanel() {
        final JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(JBUI.Borders.emptyLeft(10));
        listPanel.add(Box.createRigidArea(JBUI.size(0, 10)));
        return listPanel;
    }
}
