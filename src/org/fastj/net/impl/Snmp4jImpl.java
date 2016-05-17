package org.fastj.net.impl;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.fastj.net.api.*;
import org.fastj.net.impl.SnmpUtil.SnmpReqNode;
import org.fastj.net.impl.SnmpUtil.VarNode;
import org.fastj.net.protocol.SnmpPara;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.*;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.*;

public class Snmp4jImpl implements SnmpConnection, PDUFactory{

	private static final OID USM_ERROR_PREFIX = new OID(new int[]{1, 3, 6, 1, 6, 3, 15, 1, 1 });
	
	private Snmp connection;
	
	private int version;
	
	private boolean connected;
	
	private int timeout = 3000;
	
	private int retries = 0;
	
	private Target target;
	
	private SnmpPara param;
	
	private Throwable error;
	
	static
	{
		SecurityProtocols.getInstance().addDefaultProtocols();
	}
	
	public boolean open(SnmpPara param) {
		try {
			this.param = param;
			
			if (null != param.check())
			{
				return false;
			}
			
			version = "SNMPv3".equalsIgnoreCase(param.getKey()) ? SnmpConstants.version3 : 
				      "SNMPv2c".equalsIgnoreCase(param.getKey()) ? SnmpConstants.version2c : SnmpConstants.version1;
			
			MessageDispatcherImpl messageDispatcher = new MessageDispatcherImpl();
			connection = new Snmp(messageDispatcher, new DefaultUdpTransportMapping());
			messageDispatcher.addMessageProcessingModel(new MPv2c());
		    messageDispatcher.addMessageProcessingModel(new MPv1());
			
			if (version == SnmpConstants.version3)
			{
				USM usm = new USM(SecurityProtocols.getInstance(), new OctetString("SnmpCommunicator-Fonddream"), 0);
				MPv3 mpv3 = (MPv3) new MPv3(usm);
				messageDispatcher.addMessageProcessingModel(mpv3);
				
				UsmUser uu = new UsmUser(getOctStr(param.getSecurityName()), 
						getAuthPriv(param.getAuthProtocol()), 
						getOctStr(param.getAuthPassword()), 
						getAuthPriv(param.getPrivProtocol()), 
						getOctStr(param.getPrivPassword()), 
						getOctStr(param.getContextEngineId()));
				usm.addUser(uu);
				
				UserTarget tgt = new UserTarget();
				tgt.setSecurityName(getOctStr(param.getSecurityName()));
				tgt.setSecurityModel(USM.SECURITY_MODEL_USM);
				tgt.setSecurityLevel(uu.getAuthenticationProtocol() != null ? uu.getPrivacyProtocol() == null ? SecurityLevel.AUTH_NOPRIV : SecurityLevel.AUTH_PRIV : SecurityLevel.NOAUTH_NOPRIV);
				
				target = tgt;
			}
			else
			{
				target = new CommunityTarget();
			}
			
			target.setAddress(GenericAddress.parse("udp:" + param.getIpAddress() + "/" + param.getPort()));
			target.setVersion(version);
			
			
			timeout = param.getTimeout();
			retries = param.getRetries();
			
			connection.listen();
			connected = true;
			return true;
		} catch (Throwable e) {
			error = e;
			connected = false;
		}
		
		return false;
	}
	
	public Throwable getError()
	{
		return error;
	}

	public boolean isConnected() {
		return connected;
	}
	
	public void close()
	{
		connected = false;
		try {
			if (null != connection)
			{
				connection.close();
			}
		} catch (IOException e) {
		}
	}

	public void setParam(int timeout, int retries) {
		this.timeout = timeout;
		this.retries = retries;
	}

	public Response<NVar> get(String ... oids) {
		return op(createPDU(PDU.GET, oids));
	}

	public Response<NVar> getNext(String ... oids) {
		return op(createPDU(PDU.GETNEXT, oids));
	}

	public Response<NVar> set(String oid, String value, int type) {
		PDU pdu = createPDU(PDU.SET);
		pdu.add(new VariableBinding(new OID(oid), createVariable(value, type)));
		
		return op(pdu);
	}
	
	public <T> Response<Table<T>> table(int timeOut, Class<T> tableClazz) {
		SnmpReqNode sqn = SnmpUtil.getReqNode(tableClazz);
		if (sqn == null || !sqn.isTable)
		{
			throw new IllegalArgumentException("Not a valid SnmpTableReq class.");
		}
		
		return table(timeOut, tableClazz, sqn.reqOids.toArray(new OID[sqn.reqOids.size()]));
	}
	
	public <T> Response<Table<T>> table(Class<T> tableClazz) {
		return table(DEFAULT_TABLE_QUERY_TIMEOUT, tableClazz);
	}

	public <T> Response<T> get(Class<T> clazz) {
		Response<T> resp = new Response<T>();
		SnmpReqNode sqn = SnmpUtil.getReqNode(clazz);
		if (sqn == null || sqn.isTable)
		{
			throw new IllegalArgumentException("Not a valid SnmpReq class.");
		}
		
		String oids[] = new String[sqn.reqOids.size()];
		int tag = 0;
		for (OID oid : sqn.reqOids)
		{
			oids[tag++] = oid.toString();
		}
		
		Response<NVar> sr = get(oids);
		if (sr.getCode() == Response.OK)
		{
			try {
				T t = clazz.newInstance();
				NVar pl = sr.getEntity();
				for (VarNode vn : sqn.reqVars)
				{
					String v = pl.get(vn.getVarOid().toString());
					vn.setValue(t, v);
				}
				resp.setCode(sr.getCode());
				resp.setPhrase(sr.getPhrase());
				resp.setEntity(t);
				return resp;
			} catch (Throwable e) {
				e.printStackTrace();
				//TODO
				resp.setCode(Response.UNKOWN_ERROR);
				resp.setPhrase(e.getMessage());
			} 
		}
		
		return resp;
	}

	private Response<NVar> op(PDU req)
	{
		target.setTimeout(timeout);
		target.setRetries(retries);
		
		if (version != SnmpConstants.version3)
		{
			CommunityTarget ct = (CommunityTarget) target;
			ct.setCommunity(req.getType() == PDU.SET ? 
					getOctStr(param.getWriteCommunity()) 
					: getOctStr(param.getReadCommunity()));
		}
		
		Response<NVar> sresp = new Response<NVar>();
		try {
			ResponseEvent re = connection.send(req, target);
			checkResponse(re, sresp);

			if (sresp.getCode() != Response.OK || req.getType() == PDU.SET)
			{
				return sresp;
			}
			
			PDU pdu = re.getResponse();
			
			NVar vbs = new NVar();
			for (VariableBinding vb : pdu.getVariableBindings())
			{
				String oid = vb.getOid().toDottedString();
				Variable v = vb.getVariable();
				String value = v == null ? null : v.toString();
				vbs.add(oid, value);
			}
			
			sresp.setEntity(vbs);
			return sresp;
		} catch (IOException e) {
			sresp.setCode(Response.IO_EXCEPTION);
			sresp.setPhrase(e.getMessage());
			e.printStackTrace();
		}
		catch (Throwable e) {
			sresp.setCode(Response.UNKOWN_ERROR);
			sresp.setPhrase(e.getMessage());
			e.printStackTrace();
		}
		
		return sresp;
	}

	public Response<Table<NVar>> table(String ... oid) {
		return table(15000, oid);
	}
	
	public Response<Table<NVar>> table(int timeOut, String ... oids) {
		OID[] columns = new OID[oids.length];
		for (int i = 0; i < columns.length; i++) {
			columns[i] = new OID(oids[i]);
		}
		return table(timeOut, NVar.class, columns);
	}

	private <T> Response<Table<T>> table(int timeOut,Class<T> clazz, OID ... columns) {

		Response<Table<T>> resp = new Response<Table<T>>();
		Table<T> respEntity = new Table<T>();
		resp.setEntity(respEntity);
		
		TableUtils tableUtils = new TableUtils(connection, this);
		tableUtils.setMaxNumRowsPerPDU(65535);
		tableUtils.setMaxNumColumnsPerPDU(columns.length);
		Counter32 counter = new Counter32();
		
		target.setTimeout(timeout);
		target.setRetries(retries);
		
		synchronized (counter) {
			ClassTableListener<T> listener = new ClassTableListener<T>();
			listener.table = respEntity;
			listener.clazz = clazz;
			tableUtils.getTable(target, columns, listener, counter, null, null);
			try {
				counter.wait(timeOut);
			} catch (InterruptedException ex) {
				resp.setCode(Response.UNKOWN_ERROR);
				resp.setPhrase("Unkown Error: " + ex.getMessage());
				Thread.currentThread().interrupt();
			}
		}

		return resp;
	}
	
	public PDU createPDU(Target target) {
	    return createPDU(PDU.GETNEXT);
	}

	public PDU createPDU(MessageProcessingModel messageProcessingModel) {
		return createPDU(PDU.GETNEXT);
	}

	private void checkResponse(ResponseEvent re, Response<?> sresp)
	{
		if (null == re || null == re.getResponse())
		{
			sresp.setCode(Response.TIMEOUT);
			sresp.setPhrase("No Response.");
			return ;
		}
		
		PDU pdu = re.getResponse();
		List<VariableBinding> errs = pdu.getBindingList(USM_ERROR_PREFIX);
		if (errs != null && !errs.isEmpty())
		{
			sresp.setCode(Response.AUTH_FAIL);
			sresp.setPhrase("AuthFail: " + errs.toString());
			return ;
		}
		
		sresp.setCode(Response.OK);
		sresp.setPhrase("success");
	}
	
	private PDU createPDU(int type, String ... oids)
	{
		PDU pdu = version == 3 ? new ScopedPDU() : new PDU();
		
		pdu.setType(type);
		
		if (oids != null)
		{
			for (String oid : oids)
			{
				pdu.add(new VariableBinding(new OID(oid)));
			}
		}
		
		if (version == SnmpConstants.version3) {
			ScopedPDU scopedPDU = (ScopedPDU) pdu;
			scopedPDU.setContextEngineID(getNoNullOctStr(param.getContextEngineId()));
			scopedPDU.setContextName(getNoNullOctStr(param.getContextEngineId()));
			scopedPDU.setMaxRepetitions(65535);
		}
		
		return pdu;
	}
	
	private Variable createVariable(String value, int type) {
		switch (type) {
		case SMIConstants.SYNTAX_COUNTER32:
			return new Counter32(Long.parseLong(value));
		case SMIConstants.SYNTAX_COUNTER64:
			return new Counter64(Long.parseLong(value));
		case SMIConstants.SYNTAX_GAUGE32:
			return new Gauge32(Long.parseLong(value));
		case SMIConstants.SYNTAX_INTEGER32:
			return new Integer32(Integer.parseInt(value));
		case SMIConstants.SYNTAX_IPADDRESS:
			return new IpAddress(value);
		case SMIConstants.SYNTAX_NULL:
			return new Null();
		case SMIConstants.SYNTAX_TIMETICKS:
			return new TimeTicks(Long.parseLong(value));
		case SMIConstants.SYNTAX_OPAQUE:
			return new Opaque(value.getBytes());
		case SMIConstants.SYNTAX_OBJECT_IDENTIFIER: 
	        return new OID();
		case SMIConstants.SYNTAX_OCTET_STRING:
		default:
			return new OctetString(value);
		}
	}
	
	private OID getAuthPriv(String key)
	{
		if (null == key)
		{
			return null;
		}
		
		if (key.toUpperCase(Locale.ENGLISH).contains("MD5"))
		{
			return AuthMD5.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).contains("SHA"))
		{
			return AuthSHA.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).equals("3DES"))
		{
			return Priv3DES.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).equals("DES"))
		{
			return PrivDES.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).equals("AES256"))
		{
			return PrivAES256.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).equals("AES192"))
		{
			return PrivAES192.ID;
		}
		else if (key.toUpperCase(Locale.ENGLISH).contains("AES"))
		{
			return PrivAES128.ID;
		}
		
		return null;
	}
	
	private OctetString getNoNullOctStr(String str)
	{
		return null == str ? new OctetString() : str.startsWith("0x") ? OctetString.fromHexString(str.substring(2)) : new OctetString(str);
	}
	
	private OctetString getOctStr(String str)
	{
		return null == str ? null : str.startsWith("0x") ? OctetString.fromHexString(str.substring(2)) : new OctetString(str);
	}
	
	private class ClassTableListener<T> implements TableListener {

	    private boolean finished;
	    private Table<T> table;
	    private Class<T> clazz;
	    
	    public void finished(TableEvent event) {
	      finished = true;
	      synchronized (event.getUserObject()) {
	        event.getUserObject().notify();
	      }
	    }

		@SuppressWarnings("unchecked")
		public boolean next(TableEvent event) {
			try {
				NVar tr = new NVar();
				
				for (int i = 0; i < event.getColumns().length; i++) {
					VariableBinding vb = event.getColumns()[i];
					OID rowId = new OID(vb.getOid().toIntArray());
					rowId.removeLast();
					tr.add(rowId.toString(), vb.getVariable().toString().trim());
				}
				
				if (clazz == NVar.class)
				{
					((Table<NVar>)table).addRow(tr);
					((Counter32) event.getUserObject()).increment();
					return true;
				}
				
				SnmpReqNode rn = SnmpUtil.getReqNode(clazz);
				T rowData = clazz.newInstance();
				for (VarNode vn : rn.reqVars)
				{
					String value = tr.get(vn.getVarOid().toString());
					if (value != null)
					{
						vn.setValue(rowData, value);
					}
				}
				
				table.addRow(rowData);
				((Counter32) event.getUserObject()).increment();
			} catch (Throwable e) {
				//TODO
				return false;//stop loop
			} 
			
			return true;
		}

	    public boolean isFinished() {
	      return finished;
	    }

	}//End of ClassTableListener
	
	
}
