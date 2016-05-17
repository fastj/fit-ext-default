package org.fastj.net.api;

public interface CmdLine {
	
	CmdLine env(String envName, String value);
	
	CmdLine autosend(String keyStr, String value);
	
	Response<String> exec(String ... cmdparas);
	
	Response<String> exec(int timeout, String ... cmdparas);
}
