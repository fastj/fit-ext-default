package org.fastj.net.api;

import org.fastj.net.protocol.Protocol;

public interface SshConnection {
	
	/**
	 * 打开连接
	 * @param param  连接参数
	 * @return Response<String>
	 */
	Response<String> open(Protocol param);
	
	/**
	 * @return
	 */
	boolean isConnected();
	
	/**
	 * 如果登录时的结束符不在默认支持范围，则需要在open()之前使用此API接口进行设置
	 * 默认支持"# ","> ","$ "等三种结束标记，其他系统命令行结束标记首先使用此接口设置，否则可能导致登陆慢（超时返回）
	 * @param ends
	 * @return SshConnection
	 */
	SshConnection with(String ... ends);
	
	/**
	 * 设置SSH命令执行过程中需要的一些中间输入
	 * @param toContinues  命令执行过程中需要的中间输入，如密码，yes/no等 
	 * @return SshConnection
	 */
	SshConnection with(NVar toContinues);
	
	/**
	 * 清除由with()接口设置的参数
	 * @return SshConnection
	 */
	SshConnection clean();
	
	/**
	 * 交互式执行的shell命令
	 * 
	 * @param command  shell命令
	 * @return Response<String>
	 */
	Response<String> exec(String command);
	
	/**
	 * 交互式执行的shell命令
	 * @param command  shell命令
	 * @param timeOut  超时时间
	 * @return Response<String>
	 */
	Response<String> exec(int timeOut, String command);
	
	/**
	 * 非交互模式执行单条命令，可以进行中间输入（通过with()接口设置），默认超时时间15秒
	 * @param command  命令
	 * @return Response<String>
	 */
	Response<String> cmd(String command);
	
	/**
	 * 非交互模式执行单条命令，可以进行中间输入（通过with()接口设置）
	 * @param timeOut    超时时间（小于3秒时，重置为3秒）
	 * @param command    命令
	 * @return Response<String>
	 */
	Response<String> cmd(int timeOut, String command);
	
	/**
	 * 关闭连接
	 */
	void close();
}
