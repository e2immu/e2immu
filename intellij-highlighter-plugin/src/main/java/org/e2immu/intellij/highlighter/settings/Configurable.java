package org.e2immu.intellij.highlighter.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import org.e2immu.intellij.highlighter.Bundle;
import org.e2immu.intellij.highlighter.Constants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

public class Configurable implements SearchableConfigurable {

    private ConfigurableGui gui = new ConfigurableGui();

    public Configurable() {
        JLabel noSupportedLanguage = new JLabel(Bundle.INSTANCE.get("e2i.settings.custom.global.noLang"));
        Font font = noSupportedLanguage.getFont();
        noSupportedLanguage.setFont(font.deriveFont(
                font.getStyle() == Font.PLAIN ? Font.BOLD : font.getStyle()
        ));
        java.util.List<LinkLabel> list = com.intellij.openapi.options.Configurable.APPLICATION_CONFIGURABLE.getExtensionList().stream()
                .filter(cep -> cep.id != null && cep.id.startsWith("preferences." + Constants.APP_NAME + "."))
                .map(cep -> {
                    String language = cep.id.split(".")[2];
                    String text = Bundle.INSTANCE.get("e2i.settings." + language);
                    LinkLabel ll = LinkLabel.create(text, buildRunnable(cep));
                    ll.setAlignmentX(0f);
                    ll.setBorder(JBUI.Borders.empty(1, 1, 3, 1));
                    return ll;
                }).collect(Collectors.toList());
        if (list.isEmpty()) {
            gui.getListPanel().add(noSupportedLanguage);
        } else {
            list.forEach(ll -> gui.getListPanel().add(ll));
        }
    }

    private Runnable buildRunnable(ConfigurableEP<com.intellij.openapi.options.Configurable> configurableEP) {
        Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(gui.getRootPanel()));
        com.intellij.openapi.options.Configurable configurable = settings != null ? settings.find(configurableEP.id) : null;
        return settings != null ? () -> settings.select(configurable) : null; // should this be .dispose() ??
    }

    @NotNull
    @Override
    public String getId() {
        return "preferences."+Constants.APP_NAME;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return Bundle.INSTANCE.get("e2i.app.presentableName");
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return gui.getRootPanel();
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        // no code here
    }
}
