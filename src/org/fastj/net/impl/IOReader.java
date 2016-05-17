package org.fastj.net.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class IOReader implements Runnable {
	private HashMap<String, String> inputs = new HashMap<String, String>();
	private InputStream in;
	private OutputStream out;
	private ByteArrayOutputStream bouts = new ByteArrayOutputStream();
	private boolean eof = false;
	private String encoding = "utf-8";

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public boolean isEof() {
		return eof;
	}
	
	public void setEof(){
		eof = true;
	}

	public void setInputs(String ends,String input){
		synchronized (inputs) {
			if (inputs.containsKey(ends)) {
				inputs.remove(ends);
			}
			inputs.put(ends, input);
		}
	}
	
	public void setInputs(HashMap<String, String> input){
		if(input == null) return;
		for(String key : input.keySet()){
			setInputs(key, input.get(key));
		}
	}
	
	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	public void setInputStream(InputStream in) {
		this.in = in;
	}
	
	public String getOutString(){
		try {
			return bouts.toString(encoding);
		} catch (UnsupportedEncodingException e) {
			return bouts.toString();
		}
	}

	public void run() {
		byte[] buf = new byte[1024];
		do {
			try {
				int len = in.read(buf);
				if (len > 0) {
					bouts.write(buf, 0, len);
				} else {
					eof = true;
					break;
				}
				checkInputs();
			} catch (IOException e) {
				eof = true;
				break;
			}
		} while (!eof);
	}
	
	private final void checkInputs(){
		String s = bouts.toString();
		synchronized (inputs) {
			for(String key : inputs.keySet()){
				if(s.endsWith(key)){
					if(out != null){
						try {
							out.write(inputs.get(key).getBytes());
							out.flush();
						} catch (IOException e) {
							e.printStackTrace();
							eof = true;
						}
					}
					break;
				}
			}
		}
	}

}
