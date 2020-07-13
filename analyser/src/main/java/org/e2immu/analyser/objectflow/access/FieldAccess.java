package org.e2immu.analyser.objectflow.access;

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

public class FieldAccess implements Access {
    public final FieldInfo fieldInfo;
    public final Access accessOnField;

    public FieldAccess(FieldInfo fieldInfo, @Nullable Access accessOnField) {
        this.fieldInfo = Objects.requireNonNull(fieldInfo);
        this.accessOnField = accessOnField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccess that = (FieldAccess) o;
        return fieldInfo.equals(that.fieldInfo) &&
                Objects.equals(accessOnField, that.accessOnField);
    }

    @Override
    public String safeToString(Set<ObjectFlow> visited, boolean detailed) {
        if (detailed) {
            return "access " + fieldInfo.name + (accessOnField == null ? "" : "." + accessOnField.safeToString(visited, false));
        }
        return toString();
    }

    @Override
    public String toString() {
        return "access " + fieldInfo.name + (accessOnField == null ? "" : "." + accessOnField.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldInfo, accessOnField);
    }
}
