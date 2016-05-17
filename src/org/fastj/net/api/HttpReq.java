package org.fastj.net.api;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.fastj.net.protocol.Proxy;

public class HttpReq {
	public static String reqEncoding = "UTF-8";
	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final String PUT = "PUT";
	private static final String DELETE = "DELETE";
	private static final String PATCH = "PATCH";
	
	private String url;
	private String method = GET;
	private String content = "";
	private String downloadFile;
	private NVar uploads;
	private Proxy proxy;
	private HashMap<String, String> headers = new HashMap<String, String>();
	
	public static HttpReq create()
	{
		return new HttpReq();
	}
	
	public HttpReq setUrl(String _url)
	{
		url = _url;
		return this;
	}
	
	public String getUrl()
	{
		return url;
	}
	
	public HttpReq setRequestType(String type) {
		String t = type.toUpperCase(Locale.ENGLISH);
		if (!Pattern.matches("^(GET|POST|PUT|DELETE|PATCH|OPTIONS)$", t)) {
			throw new IllegalArgumentException("Invalid or Not Support http request type : " + type);
		}
		
		method = t;
		
		return this;
	}
	
	public HttpReq setContent(String c)
	{
		this.content = c;
		return this;
	}
	
	public HttpReq addHeader(String name, String value)
	{
		if (null == name || name.isEmpty() || null == value || value.isEmpty())
		{
			throw new IllegalArgumentException("Invalid header name or value.");
		}
		
		headers.put(name, value);
		return this;
	}
	
	public HttpUriRequest request()
	{
		HttpRequestBase req = null;
		boolean multiBody = false;
		if (GET.equals(method))
		{
			req = new HttpGet(url);
		}
		else if (POST.equals(method))
		{
			HttpPost post = new HttpPost(url);
			if (uploads == null || uploads.getSize() == 0)
			{
				post.setEntity(new StringEntity(content, Charset.forName(reqEncoding)));
			}
			else
			{
				multiBody = true;
			}
			req = post;
		}
		else if (PUT.equals(method))
		{
			HttpPut put = new HttpPut(url);
			if (uploads == null || uploads.getSize() == 0)
			{
				put.setEntity(new StringEntity(content, Charset.forName(reqEncoding)));
			}
			else
			{
				multiBody = true;
			}
			req = put;
		}
		else if (DELETE.equals(method))
		{
			req = new HttpDelete(url);
		}
		else if (PATCH.equals(method))
		{
			HttpPatch patch = new HttpPatch(url);
			if (uploads == null || uploads.getSize() == 0)
			{
				patch.setEntity(new StringEntity(content, Charset.forName(reqEncoding)));
			}
			else
			{
				multiBody = true;
			}
			
			req = patch;
		}
		else if ("OPTIONS".equals(method))
		{
			req = new HttpOptions(url);
		}
		else
			return null;
		
		if (multiBody)
		{
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			for (String key : uploads.getKeys())
			{
				builder.addPart(key, new FileBody(new File(uploads.get(key))));
			}
			this.headers.remove("Content-Type");
			((HttpEntityEnclosingRequestBase)req).setEntity(builder.build());
		}
		
		for (String key : headers.keySet())
		{
			req.addHeader(key, headers.get(key));
		}
		
		if (proxy != null)
		{
			HttpHost hproxy = new HttpHost(proxy.getProxyIp(), proxy.getProxyPort(), proxy.getType().toLowerCase());
			RequestConfig rc = RequestConfig.custom().setProxy(hproxy).build();
			req.setConfig(rc);
		}
		
		return req;
	}

	public String getMethod() {
		return method;
	}

	public HttpReq setMethod(String method) {
		return setRequestType(method);
	}

	public String getDownloadFile() {
		return downloadFile;
	}

	public HttpReq setDownloadFile(String downloadFile) {
		this.downloadFile = downloadFile;
		return this;
	}

	public NVar getUploads() {
		return uploads;
	}

	public HttpReq addUpload(String item, String file)
	{
		if (this.uploads == null)
		{
			this.uploads = new NVar();
		}
		
		uploads.add(item, file);
		
		return this;
	}
	
	public HttpReq setUploads(NVar uploads) {
		this.uploads = uploads;
		return this;
	}

	public HashMap<String, String> getHeaders() {
		return headers;
	}

	public HttpReq setHeaders(HashMap<String, String> headers) {
		this.headers.clear();
		if (headers != null)
		{
			this.headers.putAll(headers);
		}
		return this;
	}

	public String getContent() {
		return content;
	}
	
	public String toString()
	{
		return method + " " + url;
	}
	
	public Proxy getProxy() {
		return proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	private static class HttpPatch extends HttpEntityEnclosingRequestBase
	{
		public final static String METHOD_NAME = "PATCH";

	    /**
	     * @throws IllegalArgumentException if the uri is invalid.
	     */
	    public HttpPatch(final String uri) {
	        super();
	        setURI(URI.create(uri));
	    }

	    @Override
	    public String getMethod() {
	        return METHOD_NAME;
	    }
	}
}
