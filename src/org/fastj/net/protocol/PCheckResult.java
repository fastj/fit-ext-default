package org.fastj.net.protocol;

import java.util.Map;

public class PCheckResult {
	
	private int code;
	
	private Map<String, String> phrase;

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public Map<String, String> getPhrase() {
		return phrase;
	}

	public void setPhrase(Map<String, String> phrase) {
		this.phrase = phrase;
	}
	
}
