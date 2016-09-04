package io.metamorphic.fileservices;

import java.util.Arrays;

/**
 * Created by markmo on 4/07/2015.
 */
public class BooleanParser implements ITypeParser<Boolean> {

    static final String[] affirmatives = new String[] { "true", "t", "yes", "y", "on", "1" };

    static final String[] negatives = new String[] { "false", "f", "no", "n", "off", "0" };

    @Override
    public Boolean parse(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        if (v.isEmpty()) return null;
        if (Arrays.asList(affirmatives).contains(v)) {
            return true;
        } else if (Arrays.asList(negatives).contains(v)) {
            return false;
        }
        return null;
    }
}
