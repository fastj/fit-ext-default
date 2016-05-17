package org.fastj.net.protocol;

public class UserModeParam extends Protocol{
	
	private String user;
	private String password;
	
	public PCheckResult check() {
		return null;
	}

	public String getUser() {
		return user;
	}


	public void setUser(String user) {
		this.user = user;
	}


	public String getPassword() {
		return password;
	}


	public void setPassword(String password) {
		this.password = password;
	}
	
	public String toString()
	{
		return String.format("UMP[ip=%s, port=%s, user=%s]", getIpAddress(), getPort(), getUser());
	}
}
