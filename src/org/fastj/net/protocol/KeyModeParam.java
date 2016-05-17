package org.fastj.net.protocol;

public class KeyModeParam extends Protocol {
	
	private String user;
	private String passkey;
	private String passphrase;
	
	@Override
	public PCheckResult check() {
		return null;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPasskey() {
		return passkey;
	}

	public void setPasskey(String passkey) {
		this.passkey = passkey;
	}

	public String getPassphrase() {
		return passphrase;
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}
	
	public String toString()
	{
		return String.format("KMP[ip=%s, port=%s, user=%s]", getIpAddress(), getPort(), getUser());
	}
	
}
