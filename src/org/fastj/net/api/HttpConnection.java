package org.fastj.net.api;

public interface HttpConnection {
	Response<HttpRsp<String>> exec(HttpReq req);
}
