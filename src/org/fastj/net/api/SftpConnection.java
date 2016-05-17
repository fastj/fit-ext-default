package org.fastj.net.api;

import org.fastj.net.protocol.Protocol;

public interface SftpConnection {
	
	Response<String> open(Protocol param);
	
	boolean isConnected();
	
	Response<String> upload(String dir, String rfile, String lfile);
	
	Response<String> download(String dir, String rfile, String lfile);
	
	void close();
}
