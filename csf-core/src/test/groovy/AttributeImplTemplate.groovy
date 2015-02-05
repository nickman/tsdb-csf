	/**
	 * <p>Title: ${name}Attribute</p>
	 * <p>Description: Attribute manager for the ${name} MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.${name}Attribute</code></p>
	 */	
	public static enum ${name}Attribute implements AttributeManager<${name}Attribute> {
		<%	println "\r";
			int last = attrs.size()-1;
			int cnt = 0;
			attrs.each() { attr, type ->
				print "\t\t/**  */\n";
				print "\t\t${uckey(attr)}(\"$attr\", ${type.isPrimitive() ? type.getName() : type.getSimpleName()}.class)${cnt==last ? ';' : ','}\n";
				cnt++;
			}
		%>
		
		private ${name}Attribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = Util.pow2Index(ordinal());
		/** The currently loaded class count */
		public final String attributeName;
		/** The total loaded class count */
		public final Class<?> type;
		/** The unloaded class count */
		public final boolean primitive;
		
		/**
		 * Returns all the attribute names
		 * @return all the attribute names
		 */
		public static String[] getAllAttributes() {
			return getAttributeNames(${name}Attribute.class);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
	}



/**   CODE GEN   
import java.util.concurrent.*;
import groovy.text.SimpleTemplateEngine;
import java.lang.management.*;
import javax.management.*;

ptypes = [
    "boolean" : boolean.class, "byte" : byte.class, "char" : char.class, "short" : short.class, "int" : int.class, "long" : long.class, "float" : float.class, "double" : double.class
]
text = new File("/home/nwhitehead/hprojects/tsdb-csf/csf-core/src/test/groovy/AttributeImplTemplate.groovy").getText();
def engine = new SimpleTemplateEngine();
mbs = ManagementFactory.getPlatformMBeanServer();
on = { name ->
    return new ObjectName(name);
}
attrs = { minfo ->
    def attrMap = [:];
    minfo.getAttributes().each() {
        def typeName = it.getType();
        def type = ptypes.containsKey(typeName) ? ptypes.get(typeName) : Class.forName(typeName);
        attrMap.put(it.getName(), type);
    }
    return attrMap;
}
uckey = { name ->
    def b = new StringBuilder();
    int cnt = 0;
    name.toCharArray().each() { c ->
        //println "${(int)c}";
        int x = (int)c;
        if(cnt!= 0 && x>=65 && x<=90) {
            b.append("_")
        } 
        b.append(c);  
        cnt++;      
    }
    return b.toString().toUpperCase();
}

defs = ["java.lang:type=ClassLoading", "java.lang:type=Compilation", "java.lang:type=Memory", "java.lang:type=GarbageCollector,name=PS MarkSweep", "java.lang:type=MemoryPool,name=Code Cache", "java.lang:type=OperatingSystem", "java.lang:type=Runtime", "java.lang:type=Threading"]; 
def binding = null;

defs.each() {
    binding = [:];
    objectName = on(it);
    minfo = mbs.getMBeanInfo(objectName);
    binding.put("uckey", uckey);
    binding.put("name", objectName.getKeyProperty("type"));
    binding.put("attrs", attrs(minfo));
    
    template = engine.createTemplate(text).make(binding);
    println template.toString();
}

*/







return null;