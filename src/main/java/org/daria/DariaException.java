package org.daria;

/**
 * DariaException
 */
public class DariaException extends RuntimeException{

	public DariaException(String causeText){
		super(causeText);
	}

	public DariaException(String causeText, Throwable c){
		super(causeText, c);
	}

}
