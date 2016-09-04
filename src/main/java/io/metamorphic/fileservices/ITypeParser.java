package io.metamorphic.fileservices;

/**
 * Created by markmo on 4/07/2015.
 */
public interface ITypeParser<T> {

    T parse(String value);
}
