package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.model.ParameterizedType;

import java.util.HashSet;
import java.util.Set;

public class ParameterNameFactory {
    private final Set<String> names = new HashSet<>();

    public String next(ParameterizedType type) {
        String base;
        if (type.typeInfo != null) {
            // cheap way of finding out with high likelihood if we're dealing with a primitive
            // under no conditions can we trigger a type inspection here!
            if (type.typeInfo.fullyQualifiedName.indexOf('.') < 0) {
                base = firstLetterLowerCase(type.typeInfo.simpleName).substring(0, 1);
            } else {
                base = firstLetterLowerCase(type.typeInfo.simpleName);
            }
        } else if (type.typeParameter != null) {
            base = firstLetterLowerCase(type.typeParameter.name);
        } else {
            base = "p";
        }
        if (!names.contains(base)) {
            names.add(base);
            return base;
        }
        int index = 1;
        while (true) {
            String name = base + index;
            if (!names.contains(name)) {
                names.add(name);
                return name;
            }
            index++;
        }
    }

    private static String firstLetterLowerCase(String s) {
        return Character.toLowerCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }
}
