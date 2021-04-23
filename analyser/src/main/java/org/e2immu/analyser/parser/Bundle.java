package org.e2immu.analyser.parser;

import org.e2immu.analyser.util.Logger;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.e2immu.analyser.util.Logger.log;

public class Bundle {
    private static final String BUNDLE = "messages.Messages";
    public static final Bundle INSTANCE = new Bundle();

    private Bundle() {
    }

    private Reference<ResourceBundle> ourBundle;

    public String get(String keyword) {
        return getBundle().getString(keyword);
    }

    /*
    easy way to change default locale: add -Duser.country=BE -Duser.language=nl to JVM options
     */
    private ResourceBundle getBundle() {
        ResourceBundle bundle = ourBundle == null ? null : ourBundle.get();
        if (bundle == null) {
            Locale locale =Locale.getDefault();
            log(Logger.LogTarget.CONFIGURATION, "Use resource bundle for locale {}", locale);
            bundle = ResourceBundle.getBundle(BUNDLE, locale);
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }
}
