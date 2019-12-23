package org.daria.data;

import java.io.Serializable;

/**
 * Column
 */
public class Column implements Serializable{
	public String columnName;
	public String dataType;
	public boolean isNullable;
	public ValueType vtype;
}
