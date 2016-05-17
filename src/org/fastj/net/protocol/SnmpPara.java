package org.fastj.net.protocol;

public class SnmpPara extends Protocol{

	private String securityName;
	
	private String contextName;
	
	private String contextEngineId;
	
	private String authProtocol;
	
	private String authPassword;
	
	private String privProtocol;
	
	private String privPassword;
	
	private String readCommunity;
	
	private String writeCommunity;
	
	@Override
	public PCheckResult check() {
		return null;
	}

	public String getSecurityName() {
		return securityName;
	}

	public void setSecurityName(String securityName) {
		this.securityName = securityName;
	}

	public String getContextName() {
		return contextName;
	}

	public void setContextName(String contextName) {
		this.contextName = contextName;
	}

	public String getContextEngineId() {
		return contextEngineId;
	}

	public void setContextEngineId(String contextEngineId) {
		this.contextEngineId = contextEngineId;
	}

	public String getAuthProtocol() {
		return authProtocol;
	}

	public void setAuthProtocol(String authProtocol) {
		this.authProtocol = authProtocol;
	}

	public String getAuthPassword() {
		return authPassword;
	}

	public void setAuthPassword(String authPassword) {
		this.authPassword = authPassword;
	}

	public String getPrivProtocol() {
		return privProtocol;
	}

	public void setPrivProtocol(String privProtocol) {
		this.privProtocol = privProtocol;
	}

	public String getPrivPassword() {
		return privPassword;
	}

	public void setPrivPassword(String privPassword) {
		this.privPassword = privPassword;
	}

	public String getReadCommunity() {
		return readCommunity;
	}

	public void setReadCommunity(String readCommunity) {
		this.readCommunity = readCommunity;
	}

	public String getWriteCommunity() {
		return writeCommunity;
	}

	public void setWriteCommunity(String writeCommunity) {
		this.writeCommunity = writeCommunity;
	}

	public String toString()
	{
		return String.format("SNMP[ver=%s ip=%s, port=%s]", getKey(), getIpAddress(), getPort());
	}
}
