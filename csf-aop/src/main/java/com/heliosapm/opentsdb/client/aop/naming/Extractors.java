/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heliosapm.opentsdb.client.aop.naming;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import com.heliosapm.opentsdb.client.util.JSExpressionEvaluator;
import com.heliosapm.opentsdb.client.util.StringHelper;

//import com.heliosapm.shorthand.instrumentor.shorthand.ShorthandStaticInterceptor;

/**
 * <p>Title: Extractors</p>
 * <p>Description: Contains various {@link ValueExtractor} classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.Extractors</code></p>
 */
public class Extractors {
	
	/** A package name splitter */
	public static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	/** A white space cleaner */
	public static final Pattern WS_CLEANER = Pattern.compile("\\s+");
	/** Index and Index Range Expression */
	public static final Pattern INDEX_RANGE = Pattern.compile("\\[(\\d+).*?|(\\d+)\\-(\\d+).*?\\]");
	
	/** Empty int array const */
	public static final int[] EMPTY_INT_ARR = {};
	
	/**
	 * Attempts to extract an index range from the passed expression
	 * @param expression The range expression to parse
	 * @return an int array representing the range which will be zero length if no match was made
	 */
	public static int[] range(final CharSequence expression) {
		if(expression==null || expression.toString().trim().isEmpty()) return EMPTY_INT_ARR; 
		final Set<Integer> entries = new TreeSet<Integer>();
		final Matcher m = INDEX_RANGE.matcher(expression);
		while(m.find()) {
			String a = m.group(1), b = m.group(2), c = m.group(3);
			if(a!=null && (b==null && c==null)) entries.add(Integer.parseInt(a));
			else if(a==null && (b!=null && c!=null)) {
				int start = Integer.parseInt(b);
				int end = Integer.parseInt(c);
				if(start > end) throw new RuntimeException("Invalid range [" + m.group(0) + "] in expression [" + expression + "]. Range[0] must be <= Range[1]");
				for(int i = start; i <= end; i++) {
					entries.add(i);
				}				
			}
		}
		final int[] result = new int[entries.size()];
		int index = 0;
		for(Integer entry: entries) {
			result[index] = entry;
			index++;
		}
		return result;
	}
	
	
	/**
	 * Determines if there is a named attribute method (i.e. no params) with a non void return type in the passed class
	 * @param clazz The class to inspect
	 * @param methodName The name of the attribute method
	 * @return true if one was found, false otherwise
	 */
	public static boolean isMethod(Class<?> clazz, String methodName) {
		Method method = null;
		try {
			try {
				method = clazz.getDeclaredMethod(methodName);
			} catch (NoSuchMethodException e) {
				method = clazz.getMethod(methodName);
			}
			if(method==null) return false;
			return method.getReturnType()!=void.class && method.getReturnType()!=Void.class; 
		} catch (Exception ex) {
			return false;
		}
	}
	
	/**
	 * Returns the first annotation instance found on the method where the annotation's class name or simple class name equals the passed name.
	 * If one is not found, the the class is searched for the same
	 * @param name The name we're searching for
	 * @param member The method or constructor to inspect
	 * @return a [possibly null] annotation
	 */
	public static Annotation getAnnotation(String name, Member member) {
		Annotation annotation = null;
		AnnotatedElement annotatedElement = (AnnotatedElement)member;
		for(Annotation ann: annotatedElement.getAnnotations()) {
			if(ann.annotationType().getName().equals(name) || ann.annotationType().getSimpleName().equals(name)) {
				annotation = ann;
				break;
			}
		}
		if(annotation==null) {
			for(Annotation ann: member.getDeclaringClass().getAnnotations()) {
				if(ann.annotationType().getName().equals(name) || ann.annotationType().getSimpleName().equals(name)) {
					annotation = ann;
					break;
				}
			}			
		}
		return annotation;
	}
	
	/**
	 * Determines if there is a named field in the passed class
	 * @param clazz The class to inspect
	 * @param fieldName The name of the field
	 * @return true if one was found, false otherwise
	 */
	public static boolean isField(Class<?> clazz, String fieldName) {
		Field field = null;
		try {
			try {
				field = clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {			 
				field = clazz.getField(fieldName);
			}
			return field!=null;
		} catch (Exception ex) {
			return false;
		}
	}
	
			
	
	/** Value extractor to return the {@link #toString()} of the "this" object or a field / attribute notation value. 
	 * Pattern is <b><code>[&nbsp;\\$\\{this\\}|\\$\\{this:(.*?)\\}&nbsp;]</code></b>  */
	public static final ValueExtractor THIS = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object...qualifiers) {
			String expr = WS_CLEANER.matcher(expression.toString().trim()).replaceAll("");
			Matcher matcher = MetricNamingToken.$THIS.pattern.matcher(expr);
			if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $THIS expression [" + expression + "]");
			String matchedPattern = matcher.group(0);
			String codePoint = matcher.group(1);
			String extract = null;
			if(matchedPattern.equals("${this}") || matchedPattern.equals("${this:}") ) {
				extract = "$0";
			} else {
				extract = codePoint;
			}
			validateCodePoint("$THIS", clazz, member, extract + ";");
			return toArray("%s", extract);	
		}
	};
	
	/**
	 * Attempts to compile a snippet to validate a code point
	 * @param name The extractor name
	 * @param clazz The class targetted for instrumentation
	 * @param member The method or constructor targetted for instrumentation
	 * @param codePoint The code point
	 */
	private static void validateCodePoint(String name, Class<?> clazz, Member member, String codePoint) {
		ClassPool cPool = new ClassPool(true);
		CtClass ctClass = null;
		cPool.appendClassPath(new ClassClassPath(clazz));
		try {
			ctClass = cPool.get(clazz.getName());
			CtMethod ctMethod = ctClass.getMethod(member.getName(), StringHelper.getMemberDescriptor(member));
			ctClass.removeMethod(ctMethod);
			ctMethod.insertAfter(codePoint);
			ctClass.addMethod(ctMethod);
			ctClass.writeFile(System.getProperty("java.io.tmpdir") + File.separator + "js");
			ctClass.toBytecode();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to validate " + name + "] codepoint for [" + clazz.getName() + "." + member.getName() + "]. Codepoint was [" + codePoint + "]", ex);
		} finally {
			if(ctClass!=null) ctClass.detach();
		}
	}
	
	/** Value extractor to return the {@link #toString()} of the indexed argument object or a field/attribute notation value 
	 * Pattern is <b><code>[&nbsp;\\$\\{arg\\[(\\d+)\\]\\}|\\$\\{arg:(.*?)\\}&nbsp;]</code></b>
	 * */
	public static final ValueExtractor ARG = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object...qualifiers) {
			String expr = WS_CLEANER.matcher(expression.toString().trim()).replaceAll("");
			Matcher matcher = MetricNamingToken.$ARG.pattern.matcher(expr);
			if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $ARG expression [" + expression + "]");
			String matchedIndex = matcher.group(1);
			String codePoint = matcher.group(2);
			int index = -1;
			String extract = null;
			if(codePoint==null || codePoint.trim().isEmpty()) {
				try { index = Integer.parseInt(matchedIndex); } catch (Exception x) {
					throw new RuntimeException("Invalid $ARG expression [" + expression + "]. Neither a code-point or an index were matched");
				}
				Class<?>[] paramTypes = null;
				if(member instanceof Constructor) {
					paramTypes = ((Constructor<?>)member).getParameterTypes(); 
				} else {
					paramTypes = ((Method)member).getParameterTypes();
				}
				Class<?> argType = paramTypes[index]; 
				index++;
				if(argType.isPrimitive()) {
					extract = String.format("(\"\" + $%s)", index);
				} else {
					extract = String.format("ShorthandStaticInterceptor.nvl($%s)", index, index );
				}
				
				
			} else {
				extract = codePoint;
			}			
			validateCodePoint("$ARG", clazz, member, extract + ";");
			return toArray("%s", extract);			
		}
	};
	
	/** Value extractor to return the {@link #toString()} of the invocation return value object or a field/attribute notation value 
	 *   Pattern is <b><code>[&nbsp;\\$\\{return(?::(.*))?\\}&nbsp;]</code></b>
	 * */
//	public static final ValueExtractor RETURN = new ValueExtractor() {
//		/**
//		 * {@inheritDoc}
//		 * @see com.heliosapm.shorthand.instrumentor.shorthand.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Method, java.lang.Object[])
//		 */
//		@Override
//		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Method method, Object...qualifiers) {
//			String expr = WS_CLEANER.matcher(expression.toString().trim()).replaceAll("");
//			Class<?> returnType = method.getReturnType();
//			if(returnType==Void.class || returnType==void.class) {
//				throw new RuntimeException("Invalid use of $RETURN token since method [" + clazz.getName() + "." + method.getName() + "] has a void return type");
//			}
//			String extract = null;
//			if(expr.equals("${return}") || expr.equals("${return:}")) {
//				if(returnType.isPrimitive()) {
//					extract = "$_";
//				} else {
//					extract = "($_==null ? \"\" : $_.toString())";
//				}
//			} else {
//				Matcher matcher = MetricNamingToken.$RETURN.pattern.matcher(expr);
//				if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $RETURN expression [" + expression + "]");
//				extract = matcher.group(1);
//			}
//			validateCodePoint("$RETURN", clazz, method, extract + ";");
//			return toArray("%s", extract);			
//		}
//	};
	
	


	/** Value extractor to validate and return the code point of a freeform $JAVA naming token 
	 *   Pattern is <b><code>[&nbsp;\\$\\{java(?::(.*))?\\}&nbsp;]</code></b>
	 * */
	public static final ValueExtractor JAVA = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object...qualifiers) {
			String expr = WS_CLEANER.matcher(expression.toString().trim()).replaceAll("");
			Matcher matcher = MetricNamingToken.$JAVA.pattern.matcher(expr);
			if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $JAVAexpression [" + expression + "]");
			String extract = String.format("(\"\" + (%s))",  matcher.group(1));
			validateCodePoint("$JAVA", clazz, member, extract + ";");
			return toArray("%s", extract);			
		}
	};
	
	/** Value extractor to return the {@link #toString()} of the value of the annotation attribute on the method (or failing that, on the class) or a field/attribute notation value.
	  Pattern is <b><code>[&nbsp;\\$\\{(.*?)@\\((.*?)\\)(.*?)\\}&nbsp;]</code></b>  */
	public static final ValueExtractor ANNOTATION = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object...qualifiers) {
			String expr = WS_CLEANER.matcher(expression.toString().trim()).replaceAll("");
			Matcher matcher = MetricNamingToken.$ANNOTATION.pattern.matcher(expr);
			if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $ANNOTATION expression [" + expr + "]");
			String preAnnotationCode = matcher.group(1);
			String annotationName = matcher.group(2);
			String postAnnotationCode = matcher.group(3);
			
			Annotation ann = getAnnotation(annotationName, member);
			if(ann==null) {
				throw new RuntimeException("No annotation named [" + annotationName + "] found on  [" + clazz.getName() + "." + member.getName() + "] for expression [" + expr + "]");
			}
			
			if(preAnnotationCode==null) preAnnotationCode=""; else preAnnotationCode = preAnnotationCode.trim();
			if(postAnnotationCode==null) postAnnotationCode=""; else postAnnotationCode = postAnnotationCode.trim();
			
			Object result = null;
			try {
				result = JSExpressionEvaluator.getInstance().evaluate(String.format("%s##0##%s", preAnnotationCode, postAnnotationCode), ann);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to evaluate $ANNOTATION expression [" + expr + "]", ex);
			}
			
			if(result==null) throw new RuntimeException("$ANNOTATION expression [" + expr + "] returned null");
			
			return toArray(result.toString());
		}
			
	};
	
	/** A package name or member value extractor. Pattern is <b><code>[&nbsp;\\$\\{package(?:\\[(\\d+)\\])?\\}&nbsp;]</code></b> */
	public static final ValueExtractor PACKAGE = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object... args) {
			Matcher matcher = MetricNamingToken.$PACKAGE.pattern.matcher(expression);
			if(!matcher.matches()) throw new RuntimeException("Unexpected non-macthing $PACKAGE expression [" + expression + "]");
			String strIndex = matcher.group(1); 
			if(strIndex==null || strIndex.trim().isEmpty()) {
				return toArray(clazz.getPackage().getName());
			}
			int index = -1;
			try {
				index = Integer.parseInt(matcher.group(1));
			} catch (Exception ex) {
				throw new RuntimeException("Failed to extract index from  $PACKAGE expression [" + expression + "]", ex);
			}
			return toArray(DOT_SPLITTER.split(clazz.getPackage().getName())[index]);
		}
	};
	
	/** A simple class name value extractor */
	public static final ValueExtractor CLASS = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object... args) {
			return toArray(clazz.getSimpleName());
		}
	};
	
	/** A method name value extractor */
	public static final ValueExtractor METHOD = new ValueExtractor() {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.aop.naming.ValueExtractor#getStringReplacement(java.lang.CharSequence, java.lang.Class, java.lang.reflect.Member, java.lang.Object[])
		 */
		@Override
		public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object... args) {
			return toArray(member.getName());
		}		
	};
	
	/**
	 * Convenience method to return a varg as a first class array
	 * @param strings The strings to return as an array
	 * @return a string array
	 */
	public static String[] toArray(String...strings) {
		return strings;
	}
	
	/**
	 * Simple out formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}

	
}


/*
	Some Test Groovy for these patterns
*/

/*
==================================================================================
	$PACKAGE
==================================================================================
import java.util.regex.*;
p = Pattern.compile('\\$\\{package(?:\\[(\\d+)\\])?\\}');
v = 'foo.bar.snafu';
expr = '${package[0]}';

m = p.matcher(expr);
if(!m.matches()) {
    println "No Match";
} else {
    ind = m.group(1);
    if(ind==null || ind.trim().isEmpty()) {
        println "--->[${v.replace('.', '/')}]";
    } else {
        int index = Integer.parseInt(ind.trim());
        println "--->[${v.split("\\.")[index]}]";
        
    }
}



*/