package org.fastj.net.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.fastj.net.api.Oid;
import org.fastj.net.api.TableTag;
import org.snmp4j.smi.OID;

public class SnmpUtil {
	
	private static final HashMap<Class<?>, SnmpReqNode> MAP = new HashMap<Class<?>, SnmpReqNode>();
	private static final List<Class<?>> LOADMAP = new ArrayList<Class<?>>(19);
	
	public synchronized static SnmpReqNode getReqNode(Class<?> clazz)
	{
		SnmpReqNode sqn = MAP.get(clazz);
		
		if (sqn != null || LOADMAP.contains(clazz))
		{
			return sqn;
		}
		
		sqn = loadSCDefine(clazz);
		
		if (sqn != null)
		{
			MAP.put(clazz, sqn);
		}
		
		return MAP.get(clazz);
	}
	
	private static SnmpReqNode loadSCDefine(Class<?> clazz)
	{
		LOADMAP.add(clazz);
		Field [] fields = clazz.getDeclaredFields();
		
		SnmpReqNode sqn = new SnmpReqNode();
		List<OID> oids = new ArrayList<OID>(19);
		List<VarNode> vns = new ArrayList<SnmpUtil.VarNode>(19);
		for (Field fd : fields)
		{
			Oid prop = fd.getAnnotation(Oid.class);
			if (prop != null)
			{
				VarNode vn = new VarNode();
				vn.varOid = new OID(prop.value());
				vn.varSetMethod = getSetMethod(clazz, fd);
				if (vn.varSetMethod == null || !vn.varOid.isValid())
				{
					return null;
				}
				oids.add(vn.varOid);
				vns.add(vn);
			}
		}
		
		if (oids.size() == 0)
		{
			return null;
		}
		
		sqn.reqOids = oids;
		sqn.reqVars = vns;
		sqn.isTable = clazz.getAnnotation(TableTag.class) == null ? false : true;
		
		return sqn;
	}
	
	static class SnmpReqNode
	{
		boolean isTable;
		List<VarNode> reqVars;
		List<OID> reqOids;
	}
	
	protected static class VarNode
	{
		private Method varSetMethod;
		private OID varOid;
		
		public OID getVarOid() {
			return varOid;
		}

		public void setValue(Object obj, String value)
		{
			Class<?>[] cl = varSetMethod.getParameterTypes();
			
			try {
				varSetMethod.invoke(obj, getValue(value, cl[0]));
			} catch (Throwable e) {
				throw new IllegalArgumentException("Set Bean property fail. method=" + varSetMethod.getName() + ", value=" + value , e);
			} 
		}
		
	}
	
	private static Object getValue(String value, Class<?> type)
	{
		if (type == String.class)
		{
			return value;
		}
		else if (type == int.class || type == Integer.class)
		{
			return Integer.valueOf(value);
		}
		else if (type == long.class || type == Long.class)
		{
			return Long.valueOf(value);
		}
		else if(type == Boolean.TYPE || type == boolean.class)
		{
			return Boolean.valueOf(value);
		}
		else if (type == double.class || type == Double.class)
		{
			return Double.valueOf(value);
		}
		else if (type == float.class || type == Float.class)
		{
			return Float.valueOf(value);
		}
		else if (type == short.class || type == Short.class)
		{
			return Short.valueOf(value);
		}
		else if (type == byte.class || type == Byte.class)
		{
			return Byte.valueOf(value);
		}
		else if (type == byte[].class || type == Byte[].class)
		{
			return value.getBytes();
		}
		else if(type == Character.TYPE || type == char.class)
		{
			return new Character(value.charAt(0));
		}
		
		return value;
	}
	
	private static Method getSetMethod(Class<?> clazz, Field field)
	{
		Method[] ms = clazz.getDeclaredMethods();
		String name = "set" + field.getName();
		
		for (Method m : ms)
		{
			Class<?> pcs[] = m.getParameterTypes();
			if (m.getName().equalsIgnoreCase(name) && pcs.length == 1 
					&& pcs[0] == field.getType())
			{
				return m;
			}
		}
		
		return null;
	}
}
