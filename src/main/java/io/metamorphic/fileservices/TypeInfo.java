package io.metamorphic.fileservices;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by markmo on 18/05/15.
 */
public class TypeInfo {

    private ValueTypes type;
    private Map<String, Object> info;

    public TypeInfo(ValueTypes type, Object ... keyvalues) {
        //Assert.notNull(type);
        //Assert.isTrue(keyvalues == null || keyvalues.length % 2 == 0, "Must be an even number of optional key-values");
        this.type = type;
        info = new HashMap<>();
        if (keyvalues != null) {
            for (int i = 0; i < keyvalues.length - 1; i++) {
                info.put(keyvalues[i].toString(), keyvalues[i + 1]);
            }
        }
    }

    public ValueTypes getType() {
        return type;
    }

    public Map<String, Object> getInfo() {
        return info;
    }

    public void setValue(String key, Object value) {
        info.put(key, value);
    }

    public Object getValue(String key) {
        return info.get(key);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeInfo typeInfo = (TypeInfo) o;

        return type == typeInfo.type;

    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }
}
