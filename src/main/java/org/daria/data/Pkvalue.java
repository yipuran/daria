package org.daria.data;

import java.io.Serializable;

/**
 * Pkvalue.java
 */
public class Pkvalue implements Serializable{
	public String name;
	public Object value;
	public Pkvalue(String name, Object value) {
		this.name = name;
		this.value = value;
	}
}
