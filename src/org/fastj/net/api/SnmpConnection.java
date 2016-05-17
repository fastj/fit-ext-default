package org.fastj.net.api;

import org.fastj.net.protocol.SnmpPara;

public interface SnmpConnection {
	
	int DEFAULT_TABLE_QUERY_TIMEOUT = 15000;
	
	boolean open(SnmpPara param);
	
	Throwable getError();
	
	boolean isConnected();
	
	void close();
	
	void setParam(int timeout, int retries);
	
	Response<NVar> get(String ... oid);
	
	Response<NVar> getNext(String ... oid);
	
	Response<NVar> set(String oid, String value, int type);
	
	Response<Table<NVar>> table(String ... oid);
	
	Response<Table<NVar>> table(int timeout, String ... oid);
	
	<T> Response<Table<T>> table(Class<T> tableClazz);
	
	<T> Response<Table<T>> table(int timeout, Class<T> tableClazz);
	
	<T> Response<T> get(Class<T> clazz);
}
