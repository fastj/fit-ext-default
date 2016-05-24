package org.fastj.net.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import org.fastj.fit.log.LogUtil;
import org.fastj.net.api.NVar;
import org.fastj.net.api.Response;
import org.fastj.net.api.SshConnection;
import org.fastj.net.protocol.KeyModeParam;
import org.fastj.net.protocol.PCheckResult;
import org.fastj.net.protocol.Protocol;
import org.fastj.net.protocol.Proxy;
import org.fastj.net.protocol.UserModeParam;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.ProxySOCKS4;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class JSchImpl implements SshConnection {
	private static int defaultCmdWaitTimeOut = 15000;
	private Map<byte[], byte[]> continues = new HashMap<byte[], byte[]>();
	private int timeout = 3000;
	private List<byte[]> stdEnds = new ArrayList<byte[]>();

	private JSch jsch = null;
	private Session session = null;
	private ChannelShell shell = null;
	private PipOutputStream pipeOut = null;
	private FilterOutput out = new FilterOutput();
	private Timer hbeat;
	private ReentrantLock lock = new ReentrantLock();
	private String newLine = "\n";

	static {
		JSch.setConfig("MaxAuthTries", "1");
	}

	public JSchImpl() {
		stdEnds.add("# ".getBytes());
		stdEnds.add("> ".getBytes());
		stdEnds.add("$ ".getBytes());
	}

	public boolean isConnected() {
		return session != null && session.isConnected();
	}
	
	public SshConnection with(String ... ends) {
		List<byte[]> nlist = new ArrayList<byte[]>();
		nlist.addAll(stdEnds);
		if (ends != null)
		{
			for (String end : ends)
			{
				if (!nlist.contains(end))
				{
					nlist.add(end.getBytes());
				}
			}
		}
		
		stdEnds = nlist;
		
		return this;
	}
	
	public SshConnection clean()
	{
		List<byte[]> nlist = new ArrayList<byte[]>();
		nlist.add("# ".getBytes());
		nlist.add("> ".getBytes());
		nlist.add("$ ".getBytes());
		List<byte[]> tmp = stdEnds;
		stdEnds = nlist;
		tmp.clear();
		
		Map<byte[], byte[]> maps = continues;
		continues = new HashMap<byte[], byte[]>();
		maps.clear();
		
		return this;
	}

	public Response<String> open(Protocol param) {
		Response<String> currResp = new Response<String>();
		currResp.setCode(Response.OK);
		currResp.setEntity("Success.");
		
		if (isConnected())
		{
			return currResp;
		}
		
		PCheckResult pcr = null;
		if ((pcr = param.check()) != null) {
			currResp.setCode(Response.INVALID_PARAM);
			currResp.setPhrase(pcr.toString());
			return currResp;
		}
		
		currResp.setCode(Response.OK);
		currResp.setEntity("Success.");

		timeout = param.getTimeout() > timeout ? param.getTimeout() : timeout;

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
				currResp.setPhrase(e.getMessage());
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
				currResp.setPhrase(e.getMessage());
				close();
				return currResp;
			}
			return currResp;
		}

		currResp.setCode(Response.NOT_SUPPORT);
		currResp.setPhrase("Protocol not support. " + param.getKey());
		return currResp;
	}

	public Response<String> cmd(String command) {
		return cmd(defaultCmdWaitTimeOut, command);
	}
	
	public SshConnection with(NVar toContinues) {
		if (toContinues != null && toContinues.getSize() > 0)
		{
			Map<byte[], byte[]> maps = new HashMap<byte[], byte[]>();
			maps.putAll(continues);
			
			for (String key : toContinues.getKeys())
			{
				byte[] nkey = key.getBytes();
				if (!maps.containsKey(nkey))
				{
					String v = toContinues.get(key);
					if (!key.isEmpty() && v != null && !v.isEmpty())
					{
						if (v.endsWith("\\n"))
						{
							v = v.substring(0, v.length() - 2) + "\n" ;
						}
						else if (v.endsWith("\\r\\n"))
						{
							v = v.substring(0, v.length() - 4) + "\r\n" ;
						}
						maps.put(nkey, v.getBytes());
					}
				}
			}
			
			continues = maps;
		}
		return this;
	}

	public Response<String> exec(String command) {
		return exec(defaultCmdWaitTimeOut, command);
	}
	
	public Response<String> exec(int timeOut, String command) {
		Response<String> currResp = new Response<String>();
		if (!isConnected()) {
			currResp.setCode(Response.NOT_CONNECTED);
			currResp.setEntity("Not Connected.");
			return currResp;
		}

		if (null == command) {
			currResp.setCode(Response.NULL_PARAM);
			currResp.setEntity("No command.");
			return currResp;
		}

		try {
			initShell();
			if (currResp.getCode() != Response.OK) {
				return currResp;
			}
			
			out.resetAll();
			pipeOut.write((command + newLine).getBytes());
			pipeOut.flush();
		} 
		catch (JSchException e)
		{
			currResp.setCode(Response.INTERNAL_ERROR);
			currResp.setPhrase(e.getMessage());
			return currResp;
		}
		catch (IOException e) {
			currResp.setCode(Response.IO_EXCEPTION);
			currResp.setPhrase(e.getMessage());
			return currResp;
		} catch (Throwable e) {
			currResp.setCode(Response.UNKOWN_ERROR);
			currResp.setPhrase(e.getMessage());
			return currResp;
		}
		
		return waitComplete(timeOut);
	}

	public Response<String> cmd(int timeOut, String command) {
		Response<String> rlt = new Response<String>();
		if (!isConnected())
		{
			rlt.setCode(Response.NOT_CONNECTED);
			rlt.setPhrase("Not Connected.");
			return rlt;
		}
		
		FilterOutput fpout = new FilterOutput();
		
		PipInputStream pipeIn = new PipInputStream();
		PipOutputStream cmdOut = null;
		ChannelExec channelExec = null;
		try {
			channelExec = (ChannelExec) session.openChannel("exec");
			channelExec.setCommand(command);
			channelExec.setPty(true);
			cmdOut = new PipOutputStream(pipeIn);
			channelExec.setInputStream(pipeIn);
			channelExec.setOutputStream(fpout);
			channelExec.setErrStream(fpout);
			channelExec.connect(timeout);
			long start = System.currentTimeMillis();

			while (channelExec.isConnected() && !channelExec.isEOF() && (System.currentTimeMillis() - start) < timeOut) {
				
				synchronized (fpout) {
					int size = fpout.bao.size();
					try {
						if (!continues.isEmpty()) {
							byte[] key = fpout.bao.endsWith(continues.keySet());
							if (key != null) {
								byte[] sends = continues.get(key);
								cmdOut.write(sends);
								cmdOut.flush();
							}
						}
					} catch (IOException e) {
						close();
						rlt.setCode(Response.IO_EXCEPTION);
						rlt.setEntity(fpout.toString());
						rlt.setPhrase(e.getMessage());
						return rlt;
					}

					while (fpout.bao.size() == size && channelExec.isConnected() 
							&& !channelExec.isEOF() && (System.currentTimeMillis() - start) < timeOut) {
						try {
							fpout.wait(100L);
						} catch (InterruptedException e) {
						}
					}
					
				}//synchronized
			}//while
			
			channelExec.disconnect();
			rlt.setCode(channelExec.isEOF() ? channelExec.getExitStatus() : Response.TIMEOUT);
			rlt.setEntity(fpout.toString());
			rlt.setPhrase(rlt.getEntity());
		} catch (JSchException e) {
			rlt.setCode(Response.UNKOWN_ERROR);
			rlt.setPhrase(e.getMessage());
		} catch (Throwable e){
			rlt.setCode(Response.UNKOWN_ERROR);
			rlt.setPhrase(e.getMessage());
		}
		finally
		{
			close(cmdOut);
			if (channelExec != null)
			{
				channelExec.disconnect();
			}
		}
		
		return rlt;
	}

	/**
	 * 关闭所有连接
	 */
	public void close() {
		try {
			if (pipeOut != null)
				pipeOut.close();
			pipeOut = null;
		} catch (IOException e) {
		}
		if (shell != null) {
			shell.disconnect();
			shell = null;
		}
		if (session != null) {
			session.disconnect();
			session = null;
		}
		out.resetAll();
		if (hbeat != null)
		{
			hbeat.cancel();
		}
	}

	private void connect(String ip, int port, String user, String passwd, Proxy proxy)
			throws JSchException {
		jsch = new JSch();
		session = jsch.getSession(user, ip, port);
		session.setPassword(passwd);
		
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
		
		Properties conf = new Properties();
		conf.put("StrictHostKeyChecking", "no");
		session.setConfig(conf);
		synchronized (JSch.class) {
			session.connect(timeout);
		}
	}

	private void connect(String ip, int port, String user, String key, String passphrase, Proxy proxy) throws JSchException {
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
		
		session.setUserInfo(new MyUserInfo(null, passphrase));
		Properties conf = new Properties();
		conf.put("StrictHostKeyChecking", "no");
		session.setConfig(conf);
		synchronized (JSch.class) {
			session.connect(timeout);
		}
	}
	
	private final void initShell() throws JSchException, IOException {
		if (shell != null && shell.isConnected())
			return;

		closeShell();

		if (hbeat == null)
		{
			hbeat = new Timer(true);
			hbeat.schedule(new TimerTask() {
				public void run() {
					lock.lock();
					try {
						session.sendKeepAliveMsg();
					} catch (Exception e) {
						LogUtil.error("HeartBeat fail: ", e.getMessage());
					}
					finally{
						lock.unlock();
					}
				}
			}, 10000, 10000);
		}
		shell = (ChannelShell) session.openChannel("shell");
		shell.setOutputStream(out);
		PipInputStream pipeIn = new PipInputStream();
		pipeOut = new PipOutputStream(pipeIn);
		shell.setInputStream(pipeIn);
		shell.connect(timeout);
		Response<String> resp = waitComplete(defaultCmdWaitTimeOut);
		if (resp.getEntity().toLowerCase().contains("windows"))
		{
			newLine = "\r\n";
		}
	}

	private void closeShell() {
		if (pipeOut != null) {
			try {
				pipeOut.close();
			} catch (IOException e1) {
			}
		}
		if (shell != null) {
			shell.disconnect();
		}

		out.resetAll();
		pipeOut = null;
		shell = null;
	}

	private Response<String> waitComplete(int timeOut) {
		
		Response<String> rlt = new Response<String>();
		
		if (!isConnected())
		{
			rlt.setCode(Response.NOT_CONNECTED);
			rlt.setPhrase("connection not established.");
			return rlt;
		}
		
		timeOut = timeOut < timeout ? timeout : timeOut;
		
		long start = System.currentTimeMillis();
		
		while (isConnected() && (System.currentTimeMillis() - start) < timeOut && session != null && session.isConnected()) {
			
			synchronized (out) {
				if (out.bao.endsWith(stdEnds) != null || out.bao.lswComplete()) {
					rlt.setCode(Response.OK);
					rlt.setEntity(out.toString());
					return rlt;
				}

				try {
					if (!continues.isEmpty()) {
						byte[] key = out.bao.endsWith(continues.keySet());
						if (key != null) {
							lock.lock();
							try {
								byte[] sends = continues.get(key);
								pipeOut.write(sends);
								pipeOut.flush();
							} finally {
								lock.unlock();
							}
						}
					}
				} catch (IOException e) {
					close();
					rlt.setCode(Response.IO_EXCEPTION);
					rlt.setEntity(out.toString());
					rlt.setPhrase(e.getMessage());
					return rlt;
				}
				
				int size = out.bao.size();
				while (out.bao.size() == size 
						&& isConnected() && (System.currentTimeMillis() - start) < timeOut) {
					try {
						out.wait(100L);
					} catch (InterruptedException e) {
					}
				}
			}

		}// while
		
		int code = isConnected() ? Response.TIMEOUT : Response.IO_EXCEPTION;
		rlt.setCode(code);
		rlt.setEntity(out.toString());
		return rlt;
	}

	private static final void close(Closeable io)
	{
		if (io == null) return;
		try {
			io.close();
		} catch (IOException e) {
			LogUtil.error("Close IO fail : {}", e.getMessage());
		}
		io = null;
	}

	@SuppressWarnings("unused")
	private static class FilterOutput extends OutputStream
	{
		BufferedScreen bao = new BufferedScreen(1024);
		CMD cmd = new CMD();
		
		@Override
		public synchronized String toString()
		{
			try {
				return bao.toString("UTF-8");
			} catch (UnsupportedEncodingException e) {
				return bao.toString();
			}
		}
		
		public synchronized void clear()
		{
			bao.reset();
		}
		
		public synchronized void resetAll()
		{
			bao.reset();
			cmd.reset();
		}
		
		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			super.write(b, off, len);
			notifyAll();
		}
		
		@Override
		public void write(int b) throws IOException {
			if (cmd.inControl())
			{
				cmd.put(b);
				if (cmd.ends())
				{
					bao.vt100(cmd);
					cmd.reset();
					return;
				}
				return;
			}
			if (b == '\033')
			{
				cmd.put(b);
				return;
			}
			if (b >= '\000' && b <= '\037' || b == '\177')
			{
				if (b == '\r' || b == '\n')
				{
					bao.write(b);
				}
				return;
			}
			bao.write(b);
		}
		
	}
	
	@SuppressWarnings("unused")
	private static class CMD
	{
		static final byte[][] ends = new byte[][]{{'#','3'}, {'#','4'}, {'#','5'}, {'#','6'}, {'#','8'}
        											, {'(','0'}, {'(','1'}, {'(','2'}
        											, {')','0'}, {')','1'}, {')','2'}};
		
		byte [] buff = new byte[16];
		int count = 0;
		
		public void put(int b)
		{
			if (count >= 16) return;
			buff[count++] = (byte) b;
		}
		
		public int size()
		{
			return count;
		}
		
		public boolean inControl()
		{
			return count > 0;
		}
		
		public void reset()
		{
			count = 0;
		}
		
		public boolean ends()
		{
			return count == 0 || endsCtrl() || endsESC() || endsSpec();
		}
		
		boolean endsSpec()
		{
			if (count == 3)
			{
				for (byte[] b : ends)
				{
					if (buff[1] == b[0] && buff[2] == b[1])
					{
						return true;
					}
				}
			}
			return false;
		}
		
		boolean endsCtrl()
		{
			int idx = count - 1;
			return idx > 0 && ((buff[idx] >= 'a' && buff[idx] <= 'z') || (buff[idx] >= 'A' && buff[idx] <= 'Z'));
		}
		
		boolean endsESC()
		{
			byte b = buff[1];
			return count == 2 && (b == '\007' || b == '\010' || b == '=' || b == '<' || b == '>' || b == '(' || b == ')');
		}
		
		public String getCmdString()
		{
			return count == 0 ? "" : new String(buff, 0 ,count);
		}
	}
	
	@SuppressWarnings("unused")
	private static class BufferedScreen extends OutputStream
	{
	    protected byte buf[];
	    protected int count;
	    public BufferedScreen() {
	        this(32);
	    }

	    public BufferedScreen(int size) {
	        if (size < 0) {
	            throw new IllegalArgumentException("Negative initial size: " + size);
	        }
	        buf = new byte[size];
	    }

	    private void ensureCapacity(int minCapacity) {
	        if (minCapacity - buf.length > 0)
	            grow(minCapacity);
	    }

	    private void grow(int minCapacity) {
	        // overflow-conscious code
	        int oldCapacity = buf.length;
	        int newCapacity = oldCapacity << 1;
	        if (newCapacity - minCapacity < 0)
	            newCapacity = minCapacity;
	        if (newCapacity < 0) {
	            if (minCapacity < 0) // overflow
	                throw new OutOfMemoryError();
	            newCapacity = Integer.MAX_VALUE;
	        }
	        buf = Arrays.copyOf(buf, newCapacity);
	    }

	    public synchronized void write(int b) {
	        ensureCapacity(count + 1);
	        buf[count] = (byte) b;
	        count += 1;
	    }

	    public synchronized void write(byte b[], int off, int len) {
	        if ((off < 0) || (off > b.length) || (len < 0) ||
	            ((off + len) - b.length > 0)) {
	            throw new IndexOutOfBoundsException();
	        }
	        ensureCapacity(count + len);
	        System.arraycopy(b, off, buf, count, len);
	        count += len;
	    }

	    public synchronized void writeTo(OutputStream out) throws IOException {
	        out.write(buf, 0, count);
	    }

	    public synchronized void reset() {
	        count = 0;
	    }

	    public synchronized byte toByteArray()[] {
	        return Arrays.copyOf(buf, count);
	    }

		public synchronized int size() {
	        return count;
	    }

	    public synchronized String toString() {
	        return new String(buf, 0, count);
	    }

		public synchronized String toString(String charsetName)
	        throws UnsupportedEncodingException
	    {
	        return new String(buf, 0, count, charsetName);
	    }

	    public void close() throws IOException {
	    }
	    
	    private boolean lswComplete()
	    {
	    	int i = buf.length - 1;
	    	for (; i >= 0 && buf[i] == ' '; i--) ;
	    	if (buf[i] != '>' && buf[i] != ']') {
	    		return false;
	    	}
	    	for (; i >= 0 && buf[i] != '\n'; i--) ;
	    	if (buf[i + 1] == '<' || buf[i + 1] == '[') {
	    		return true;
	    	}
	    	
	    	return false;
	    }
	    
	    private byte[] endsWith(Iterable<byte[]> sbuffs)
	    {
	    	for (byte[] sbuff : sbuffs)
	    	{
	    		if (count < sbuff.length)
		    	{
		    		continue;
		    	}
	    		boolean r = true;
		    	for (int i = 0; i < sbuff.length; i++)
		    	{
		    		if (sbuff[sbuff.length - 1 - i] != buf[count - 1 - i])
		    		{
		    			r = false;
		    			break;
		    		}
		    	}
		    	if (r)
		    	{
		    		return sbuff;
		    	}
	    	}
	    	
	    	return null;
	    }
	    
	    void vt100(CMD cmd)
	    {
	    	if (cmd.endsCtrl())
	    	{
	    		switch (cmd.buff[cmd.count - 1])
	    		{
	    		case 'A':break;
	    		case 'B':break;
	    		case 'C':break;
	    		case 'D':
	    		{
	    			if (cmd.buff[0] == '\033' && cmd.buff[1] == '[')
	    			{
	    				try {
							int len = Integer.parseInt(new String(cmd.buff, 2, cmd.count - 3));
							moveLeft(len);
						} catch (NumberFormatException e) {
							LogUtil.error("Invalid VT100 MV: {}", new String(cmd.buff, 0, cmd.count));
						}
	    			}
	    		}
	    		default:
	    		}
	    	}
	    }
	    
	    void moveLeft(int len)
	    {
	    	int ml = 0;
	    	for (;count - 1 >= ml && buf[count - ml -1] != '\n' && ml < len; ml++);
	    	count -= ml;
	    }
		
	}

	private class MyUserInfo implements UserInfo {
		private String passphrase = null;
		private String password = null;

		public MyUserInfo(String passwd, String passphrase) {
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
