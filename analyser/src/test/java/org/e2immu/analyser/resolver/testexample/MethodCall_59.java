package org.e2immu.analyser.resolver.testexample;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MethodCall_59 {

    public void method(Collection ids) {
        List newList = new ArrayList();
        Iterator it = ids.iterator();
        while (it.hasNext()) {
            NumberFormat f = new DecimalFormat("0000000");
            String format = f.format(it.next());
            newList.add(format);
        }
    }
}
