package org.fastj.net.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.fastj.net.api.Response;
import org.fastj.net.api.SftpConnection;
import org.fastj.net.protocol.KeyModeParam;
import org.fastj.net.protocol.PCheckResult;
import org.fastj.net.protocol.Protocol;
import org.fastj.net.protocol.Proxy;
import org.fastj.net.protocol.UserModeParam;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.ProxySOCKS4;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;

public class SftpJSchImpl implements SftpConnection{

	public Response<String> open(Protocol param) {
		Response<String> currResp = new Response<String>();
		PCheckResult pcr = null;
		if ((pcr = param.check()) != null) {
			currResp.setCode(Response.INVALID_PARAM);
			currResp.setPhrase(pcr.toString());
			return currResp;
		}
		
		currResp.setCode(Response.OK);
		currResp.setEntity("Success.");

		if (param instanceof UserModeParam) {
			try {
				connect(param.getIpAddress(), param.getPort(),
						((UserModeParam) param).getUser(),
						((UserModeParam) param).getPassword(),
						param.getProxy());
			} catch (Throwable e) {
				currResp.setCode(Response.UNKOWN_ERROR);
				if ("Auth fail".equalsIgnoreCase(e.getMessage().trim()))
				{
					currResp.setCode(Response.AUTH_FAIL);
				}
				currResp.setPhrase("SFTP connect fail. " + e.getClass().getName() + ":" + e.getMessage());
				close();
				return currResp;
			}
			return currResp;
		} else if (param instanceof KeyModeParam) {
			try {
				connect(param.getIpAddress(), param.getPort(),
						((KeyModeParam) param).getUser(),
						((KeyModeParam) param).getPasskey(),
						((KeyModeParam) param).getPassphrase(),
						param.getProxy());
			} catch (Throwable e) {
				currResp.setCode(Response.UNKOWN_ERROR);
				if ("Auth fail".equalsIgnoreCase(e.getMessage().trim()))
				{
					currResp.setCode(Response.AUTH_FAIL);
				}
				currResp.setPhrase("SFTP connect fail. " + e.getClass().getName() + ":" + e.getMessage());
				close();
				return currResp;
			}
			return currResp;
		}

		currResp.setCode(Response.NOT_SUPPORT);
		currResp.setPhrase("Protocol not support. " + param.getKey());
		return currResp;
	}

	public boolean isConnected() {
		return connected;
	}

	private JSch jsch = null;
    private Session session = null;
	private ChannelSftp ftps = null;
	private boolean connected = false;
	
	public Response<String> connect(String ip,int port,String user,String passwd, Proxy proxy){
		Response<String> resp = new Response<String>();
		
		try {
			jsch = new JSch();
			session = jsch.getSession(user, ip, port);
			
			if (proxy != null)
			{
				if ("HTTP".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxyHTTP(proxy.getProxyIp(), proxy.getProxyPort()));
				}
				else if ("SOCKS5".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxySOCKS5(proxy.getProxyIp(), proxy.getProxyPort()));
				}
				else if ("SOCKS4".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxySOCKS4(proxy.getProxyIp(), proxy.getProxyPort()));
				}
			}
			
			session.setPassword(passwd);
			Properties conf = new Properties();
			conf.put("StrictHostKeyChecking", "no");
			session.setConfig(conf);
			session.connect();
			ftps = (ChannelSftp) session.openChannel("sftp");
			ftps.connect();
			
			resp.setCode(Response.OK);
			connected = true;
		} catch (JSchException e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.setPhrase("SftpException: " + e.getMessage());
		}
		return resp;
	}
	
	public Response<String> connect(String ip,int port,String user,String key,String passphrase, Proxy proxy) {
		Response<String> resp = new Response<String>();
		try {
			jsch = new JSch();
			jsch.addIdentity(key);
			session = jsch.getSession(user, ip, port);
			
			if (proxy != null)
			{
				if ("HTTP".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxyHTTP(proxy.getProxyIp(), proxy.getProxyPort()));
				}
				else if ("SOCKS5".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxySOCKS5(proxy.getProxyIp(), proxy.getProxyPort()));
				}
				else if ("SOCKS4".equalsIgnoreCase(proxy.getType()))
				{
					session.setProxy(new ProxySOCKS4(proxy.getProxyIp(), proxy.getProxyPort()));
				}
			}
			
			session.setUserInfo(new MyUserInfo(null,passphrase));
			Properties conf = new Properties();
			conf.put("StrictHostKeyChecking", "no");
			session.setConfig(conf);
			session.connect();
			ftps = (ChannelSftp) session.openChannel("sftp");
			ftps.connect();
			connected = true;
			
			resp.setCode(Response.OK);
		} catch (JSchException e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.setPhrase("SftpException: " + e.getMessage());
		}
		return resp;
	}
	
	public Response<String> upload(String dir, String rfile, String lfile){
		try {
			return upload(dir, new FileInputStream(lfile), rfile);
		} catch (FileNotFoundException e) {
			Response<String> resp = new Response<String>();
			resp.setCode(Response.INVALID_PARAM);
			resp.setPhrase("File not found: " + lfile);
			return resp;
		}
	}
	
	public Response<String> upload(String rdir,InputStream ins,String fileName){
		Response<String> resp = new Response<String>();
		if (!isConnected()){
			resp.setCode(Response.NOT_CONNECTED);
			resp.setPhrase("NOT_CONNECTED");
		}
		else
		try {
			ftps.cd(rdir);
			ftps.put(ins, fileName);
			resp.setCode(Response.OK);
		} catch (SftpException e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.setPhrase("SftpException: " + e.getMessage());
		}
		
		return resp;
	}
	
	public Response<String> download(String dir, String rfile, String lfile){
		try {
			return download(dir, rfile, new FileOutputStream(lfile));
		} catch (FileNotFoundException e) {
			Response<String> resp = new Response<String>();
			return resp;
		}
	}
	
	public Response<String> download(String dir,String rfile,OutputStream out){
		Response<String> resp = new Response<String>();
		if (!isConnected()){
			resp.setCode(Response.NOT_CONNECTED);
			resp.setPhrase("NOT_CONNECTED");
		}
		else
		try {
			ftps.cd(dir);
			ftps.get(rfile, out);
			resp.setCode(Response.OK);
		} catch (Throwable e) {
			resp.setCode(Response.IO_EXCEPTION);
			resp.setPhrase("SFTP Download Exception: " + e.getClass() + ":" + e.getMessage());
		}
		return resp;
	}
	
	public long downloadTest(String dir,String rfile){
		VOut out = new VOut();
		Response<String> resp = download(dir, rfile, out);
		if(resp.getCode() != Response.OK){
			return -1l;
		}
		return out.ioLen;
	}
	
	public void close(){
		if(ftps != null){
			ftps.disconnect();
			ftps = null;
		}
		if(session != null){
			session.disconnect();
			session = null;
		}
		jsch = null;
	}
	
	public static class VOut extends OutputStream{

		public long ioLen = 0;
		
		@Override
		public void write(int b) throws IOException {
			ioLen++;
		}
	}
	
	public static class MyUserInfo implements UserInfo {
		private String passphrase = null;
		private String password = null;

		public MyUserInfo(String passwd,String passphrase) {
			this.passphrase = passphrase;
			this.password = passwd;
		}

		public String getPassphrase() {
			return passphrase;
		}

		public String getPassword() {
			return password;
		}

		public boolean promptPassphrase(String pass) {
			return passphrase != null;
		}

		public boolean promptPassword(String pass) {
			return password != null;
		}

		public boolean promptYesNo(String arg0) {
			return true;
		}

		public void showMessage(String m) {
			
		}
	}
	
}
