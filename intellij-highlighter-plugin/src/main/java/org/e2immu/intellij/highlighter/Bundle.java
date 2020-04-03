package org.e2immu.intellij.highlighter;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Singleton;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

@Singleton
@Container
public class Bundle {
    private static final String BUNDLE = "messages.E2ImmuHighlighterBundle";
    public static final Bundle INSTANCE = new Bundle();

    private Bundle() {
    }

    private Reference<ResourceBundle> ourBundle;

    public String get(@PropertyKey(resourceBundle = BUNDLE) String keyword, Object... params) {
        ResourceBundle bundle = getBundle();
        if (bundle == null) return "";
        return CommonBundle.message(bundle, keyword, params);
    }

    private ResourceBundle getBundle() {
        ResourceBundle bundle = SoftReference.dereference(ourBundle);
        if (bundle == null) {
            bundle = ResourceBundle.getBundle(BUNDLE);
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }

}
