package org.fastj.net.api;

import java.util.HashMap;

public class NVar {
	
	private HashMap<String, String> rowData = new HashMap<String, String>();
	
	public String get(String name)
	{
		return rowData.get(name);
	}
	
	public NVar add(String name, String val)
	{
		rowData.put(name, val);
		return this;
	}
	
	public int getSize()
	{
		return rowData.size();
	}
	
	public String[] getValues()
	{
		return rowData.values().toArray(new String[rowData.size()]);
	}
	
	public String[] getKeys()
	{
		return rowData.keySet().toArray(new String[rowData.size()]);
	}
	
	public String toString()
	{
		return rowData.toString();
	}
}
