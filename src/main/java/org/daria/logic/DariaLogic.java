package org.daria.logic;

import org.daria.DariaException;

/**
 * DariaLogic
 */
public interface DariaLogic{

	public void parseExcel() throws DariaException;

	public void execute() throws DariaException;

	public void outSql(String outPath) throws DariaException;

}
