package org.fastj.net.api;

import java.util.HashMap;

public class HttpRsp<T> {
	private HashMap<String, String> headers = new HashMap<String, String>();
	private int httpCode = 200;
	private T content;

	public int getHttpCode() {
		return httpCode;
	}

	public void setHttpCode(int httpCode) {
		this.httpCode = httpCode;
	}

	public T getContent() {
		return content;
	}

	public void setContent(T content) {
		this.content = content;
	}

	public HashMap<String, String> getHeaders() {
		return headers;
	}

	public void addHeader(String key, String value)
	{
		headers.put(key, value);
	}
}
