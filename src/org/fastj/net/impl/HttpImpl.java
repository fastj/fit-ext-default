package org.fastj.net.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.fastj.fit.log.LogUtil;
import org.fastj.net.api.HttpConnection;
import org.fastj.net.api.HttpReq;
import org.fastj.net.api.HttpRsp;
import org.fastj.net.api.Response;

public class HttpImpl implements HttpConnection {

	private static PoolingHttpClientConnectionManager connManager;
	private static CloseableHttpClient httpclient;

	static {
		LayeredConnectionSocketFactory ssl = null;
		
		TrustManager tm = new X509TrustManager()
		{
			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		};
		
		try {
			SSLContext sslcontext = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
			sslcontext.init(null, new TrustManager[]{tm}, new SecureRandom());
			ssl = new SSLConnectionSocketFactory(sslcontext, new AllowAllHostnameVerifier());
		} catch (final SecurityException ignore) {
			LogUtil.error("Create SSLCtx : {}", ignore.getMessage());
		} catch (final KeyManagementException ignore) {
			LogUtil.error("Create SSLCtx : {}", ignore.getMessage());
		} catch (final NoSuchAlgorithmException ignore) {
			LogUtil.error("Create SSLCtx : {}", ignore.getMessage());
		}

		final Registry<ConnectionSocketFactory> sfr = RegistryBuilder
				.<ConnectionSocketFactory> create()
				.register("http",
						PlainConnectionSocketFactory.getSocketFactory())
				.register(
						"https",
						ssl != null ? ssl : SSLConnectionSocketFactory
								.getSocketFactory()).build();

		connManager = new PoolingHttpClientConnectionManager(sfr);
		connManager.setDefaultMaxPerRoute(2000);
		connManager.setMaxTotal(5000);
		httpclient = HttpClientBuilder.create()
				.setHostnameVerifier(new AllowAllHostnameVerifier())
				.setConnectionManager(connManager).build();
	}

	public Response<HttpRsp<String>> exec(HttpReq req) {
		Response<HttpRsp<String>> resp = new Response<HttpRsp<String>>();
		resp.setEntity(new HttpRsp<String>());
		resp.setCode(-1);
		resp.getEntity().setHttpCode(-1);
		HttpUriRequest hreq = req.request();
		CloseableHttpResponse response = null;
		try {
			response = httpclient.execute(hreq);

			resp.setCode(Response.OK);
			resp.getEntity().setHttpCode(
					response.getStatusLine().getStatusCode());
			for (Header h : response.getAllHeaders()) {
				resp.getEntity().addHeader(h.getName(), h.getValue());
			}

			if (req.getDownloadFile() == null)
			{
				HttpEntity entity = response.getEntity();
				String str = entity == null ? "" : EntityUtils.toString(entity, "utf-8");
				resp.getEntity().setContent(str);
			}
			else
			{
				HttpEntity entity = response.getEntity();
				
				String file = req.getDownloadFile();
				File dfile = new File(file);
				if (dfile.getParentFile() != null)
				{
					dfile.getParentFile().mkdirs();
				}
				
				try (OutputStream out = new FileOutputStream(dfile);
					 InputStream in = entity.getContent();){
					
					byte [] buff = new byte[512];
					int len = 0;
					while((len = in.read(buff)) > 0)
					{
						out.write(buff, 0, len);
					}
				} catch (Throwable e) {
					resp.setCode(Response.IO_EXCEPTION);
					resp.getEntity().setHttpCode(1000);
					resp.setPhrase(e.getMessage());
				}
			}
		} catch (ClientProtocolException e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.getEntity().setHttpCode(1001);
			resp.setPhrase(e.getMessage());
		} catch (IOException e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.getEntity().setHttpCode(Response.IO_EXCEPTION);
			resp.setPhrase(e.getMessage());
		} finally {
			if (null != response) {
				try {
					response.close();
				} catch (IOException ignore) {
					LogUtil.error("HTTP exec : {}", ignore.getMessage());
				}
			}
		}

		return resp;
	}

}
