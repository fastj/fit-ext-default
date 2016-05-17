package org.fastj.net.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.fastj.net.api.CmdLine;
import org.fastj.net.api.Response;

public class CmdLineImpl implements CmdLine{
	
	private HashMap<String, String> envs = new HashMap<String, String>();
	private HashMap<String, String> autosend = new HashMap<String, String>();
	private String encoding = "utf-8";
	private int timeout = 15000;
	
	public Response<String> exec(int timeout, String... cmdparas) {
		return exec(timeout, envs, autosend, cmdparas);
	}

	public CmdLine env(String envName, String value) {
		envs.put(envName, value);
		return this;
	}
	
	public CmdLine autosend(String keyStr, String value) {
		autosend.put(keyStr, value);
		return this;
	}
	
	public Response<String> exec(String ... cmdparas) {
		return exec(timeout, cmdparas);
	}

	public Response<String> exec(long timeout, HashMap<String, String> envs, HashMap<String, String> inputs, String ... cmd){
		final Response<String> rlt = new Response<String>();
		Process p = null;
		IOReader ior = null;
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			Map<String, String> env = pb.environment();
			for (String key : envs.keySet())
			{
				env.put(key, envs.get(key));
			}
			pb.redirectErrorStream(true);
			p = pb.start();
			
			final InputStream is = p.getInputStream();
			OutputStream out = p.getOutputStream();
			ior = readInputStream(is, out);
			ior.setInputs(inputs);
			new Thread(ior).start();
			long end = System.currentTimeMillis() + timeout;
			while(!ior.isEof() && System.currentTimeMillis() < end){
				try {
					Thread.sleep(10);
				} catch (Exception e) {}
			}
			try {
				rlt.setCode(p.exitValue());
			} catch (Throwable e) {
				p.destroy();
				try {
					rlt.setCode(p.exitValue());
				} catch (Throwable e1) {
					rlt.setCode(Response.UNKOWN_ERROR);
					rlt.setPhrase("Process Error: " + e1.getMessage());
				}
			}
			ior.setEof();
			p = null;
			if (rlt.getCode() != Response.OK)
			{
				rlt.setPhrase(ior.getOutString());
			}
			else
			{
				rlt.setEntity(ior.getOutString());
			}
			return rlt;
		} catch (Throwable e) {
			if(ior != null) ior.setEof();
			rlt.setCode(Response.UNKOWN_ERROR);
			rlt.setPhrase("CmdLine Error: " + e.getClass().getName() + ":" + e.getMessage());
			
			if(p != null){
				p.destroy();
				p = null;
			}
		}
		
		return rlt;
	}
	
	public final IOReader readInputStream(InputStream is,OutputStream out){
		return readInputStream(is, out, encoding);
	}
	
	public final IOReader readInputStream(InputStream is,OutputStream out,String encoding){
		IOReader ior = new IOReader();
		ior.setInputStream(is);
		ior.setOutputStream(out);
		ior.setEncoding(encoding);
		return ior;
	}

}
