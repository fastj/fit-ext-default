package org.fastj.net.api;

import java.util.ArrayList;
import java.util.List;

public class Table<T>{
	
	private List<T> rawData = new ArrayList<T>();
	
	public void addRow(T t)
	{
		rawData.add(t);
	}
	
	public List<T> getData()
	{
		return rawData;
	}
	
	public T getRow(int rowIdx)
	{
		if (rowIdx < 0 || rowIdx >= rawData.size())
		{
			throw new IllegalArgumentException("Row Index error.");
		}
		
		return rawData.get(rowIdx);
	}
	
	public int size()
	{
		return rawData.size();
	}
}
