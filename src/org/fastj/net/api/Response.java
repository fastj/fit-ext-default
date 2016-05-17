package org.fastj.net.api;

public class Response<T> {
	
	public static final int OK = 0x0;
	
	public static final int UNKOWN_ERROR = 0x1FFFFFFF;
	
	public static final int INTERNAL_ERROR = UNKOWN_ERROR + 1;
	
	public static final int TIMEOUT = 0x0F000001;
	
	public static final int AUTH_FAIL = 0x0F000002;
	
	public static final int IO_EXCEPTION = 0x0F000003;
	
	public static final int NOT_CONNECTED = 0x0F000004;
	
	public static final int NO_COMMAND_SENDED = 0x0F000005;
	
	public static final int PROTOCOL_UNMATCH = 0x0F000101;
	
	public static final int NOT_SUPPORT = 0x0F000102;
	
	public static final int UNREACHABLE = 0x0F000103;
	
	public static final int NULL_PARAM = 0x0F000104;
	
	public static final int INVALID_PARAM = 0x0F000105;
	
	//return code
	private int code = OK;
	
	//response body or fail reason 
	private String phrase = null;
	
	private long reqId;
	
	private T entity;

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getPhrase() {
		return phrase;
	}

	public void setPhrase(String phrase) {
		this.phrase = phrase;
	}

	public long getReqId() {
		return reqId;
	}

	public void setReqId(long reqId) {
		this.reqId = reqId;
	}
	
	public T getEntity()
	{
		return entity;
	}
	
	public void setEntity(T vbs)
	{
		this.entity = vbs;
	}
	
	public String toString()
	{
		return String.format("[Response] code = %s, entity = %s", code, entity);
	}
}
