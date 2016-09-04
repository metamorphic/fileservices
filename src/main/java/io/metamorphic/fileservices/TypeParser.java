package io.metamorphic.fileservices;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by markmo on 4/07/2015.
 */
public class TypeParser {

    private Map<Class<?>, ITypeParser<?>> registry = new HashMap<>();

    public <T> void registerTypeParser(Class<T> key, ITypeParser<T> typeParser) {
        registry.put(key, typeParser);
    }

    @SuppressWarnings("unchecked")
    public <T> T parse(String value, Class<T> type) {
        ITypeParser<T> typeParser = (ITypeParser<T>)registry.get(type);
        if (typeParser == null) return null;
        return typeParser.parse(value);
    }
}
