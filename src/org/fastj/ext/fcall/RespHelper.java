/*
 * Copyright 2015  FastJ
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fastj.ext.fcall;

import java.util.HashMap;
import java.util.Map;

import org.fastj.net.api.HttpRsp;
import org.fastj.net.api.Response;

/**
 * JSON取值，xml转换
 * 
 * @author zhouqingquan
 *
 */
public class RespHelper {
	
	public static Map<String, Object> getJson(Response<HttpRsp<String>> hresp)
	{
		Map<String, Object> jo = org.fastj.fit.tool.JSONHelper.getJson(hresp.getEntity().getContent());
		
		jo.put("httpcode", hresp.getEntity().getHttpCode());
		Map<String, Object> headers = new HashMap<String, Object>();
		jo.put("header", headers);
		for (String h : hresp.getEntity().getHeaders().keySet())
		{
			headers.put(h, hresp.getEntity().getHeaders().get(h));
		}
		
		return jo;
	}
	
}
