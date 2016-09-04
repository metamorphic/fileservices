package io.metamorphic.fileservices;

/**
 * Created by markmo on 11/07/2015.
 */
public class TypesContainer {

    public TypeInfo[] types;
    public DataTypes[] sqlTypes;

    public TypesContainer(TypeInfo[] types, DataTypes[] sqlTypes) {
        this.types = types;
        this.sqlTypes = sqlTypes;
    }
}
