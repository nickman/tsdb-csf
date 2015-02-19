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
package com.heliosapm.opentsdb.client.util;



import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.Adler32;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * <p>Title: URLHelper</p>
 * <p>Description:  Static URL and URI helper methods.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.URLHelper</code></p>
 */
public class URLHelper {

	/** Text line separator */
	public static final String EOL = System.getProperty("line.separator", "\n");
	/** A dot splitter pattern */
	public static final Pattern DOT_SPLITTER = Pattern.compile("\\.");

	
	/** The system property to retrieve the default client connect timeout in ms.  */
	public static final String DEFAULT_CONNECT_TO = "sun.net.client.defaultConnectTimeout";
	/** The system property to retrieve the default client read timeout in ms.  */
	public static final String DEFAULT_READ_TO = "sun.net.client.defaultReadTimeout";
	
	
	/**
	 * Returns the default URL connect timeout in ms,
	 * @return the connect timeout in ms,
	 */
	protected static int defaultConnectTimeout() {
		return Integer.parseInt(System.getProperty(DEFAULT_CONNECT_TO, "0"));
	}
	
	/**
	 * Returns the default URL read timeout in ms,
	 * @return the read timeout in ms,
	 */
	protected static int defaultReadTimeout() {
		return Integer.parseInt(System.getProperty(DEFAULT_READ_TO, "0"));
	}
	
	
	/**
	 * Tests the passed object for nullness. Throws an {@link IllegalArgumentException} if the object is null 
	 * @param t  The object to test
	 * @param message The message to associate with the exception, Ignored if null
	 * @return the object passed in if not null
	 */
	public static <T> T nvl(T t, String message) {
		if(t==null) throw new IllegalArgumentException(message!=null ? message : "Null parameter");
		return t;
	}
	
	/**
	 * Returns the unqualified file name for the passed URL
	 * @param url The URL to the file name for
	 * @return The file name
	 */
	public static String getFileName(final CharSequence url) {
		return getFileName(toURL(url));
	}
	
	
	/**
	 * Returns the unqualified file name for the passed URL
	 * @param url The URL to the file name for
	 * @return The file name
	 */
	public static String getFileName(final URL url) {
		if(url==null) throw new IllegalArgumentException("The passed URL was null");
		return new File(url.getFile()).getName();
	}
	
	
	/**
	 * Returns the unqualified file name, sans extension and subextension.
	 * Examples:<ul>  
	 * 		<li><b><code>http://abc.com/index.html</code></b> would be <b><code>index</code></b></li>
	 * 		<li><b><code>http://abc.com/index.abc.html</code></b> would be <b><code>index</code></b></li>
	 * 		<li><b><code>http://abc.com/index</code></b> would be <b><code>index</code></b></li>
	 * 		<li><b><code>http://abc.com/index.3.abc.html</code></b> would be <b><code>index.3</code></b></li>
	 * </ul> 
	 * @param url The URL to get the plain file name for
	 * @return the plain file name
	 */
	public static String getPlainFileName(final URL url) {
		if(url==null) throw new IllegalArgumentException("The passed URL was null");
		String fileName = new File(url.getFile()).getName();
		final String ext = getExtension(url);
		final String subext = getSubExtension(url, null);
		if(ext!=null) {
			fileName = fileName.replace("." + ext, "");
		}
		if(subext!=null) {
			fileName = fileName.replace("." + subext, "");
		}
		return fileName;
	}

	/**
	 * Returns the unqualified file name, sans extension and subextension.
	 * @param file The URL to get the plain file name for
	 * @return the plain file name
	 * @see {@link URLHelper#getPlainFileName(URL)} for details
	 */
	@SuppressWarnings("javadoc")
	public static String getPlainFileName(final File file) {
		return getPlainFileName(file.getName());
	}
	
	/**
	 * Returns the unqualified file name, sans extension and subextension.
	 * @param name The URL to get the plain file name for
	 * @return the plain file name
	 * @see {@link URLHelper#getPlainFileName(URL)} for details
	 */
	@SuppressWarnings("javadoc")
	public static String getPlainFileName(final CharSequence name) {
		if(name==null) throw new IllegalArgumentException("The passed name was null");
		String fileName = name.toString();
		int index = fileName.lastIndexOf('/');
		if(index!=-1) fileName = fileName.substring(index);
		index = fileName.lastIndexOf(File.separatorChar);
		if(index!=-1) fileName = fileName.substring(index);
		final String ext = getExtension(name, null);
		final String subext = getSubExtension(name, null);
		if(ext!=null) {
			fileName = fileName.replace("." + ext, "");
		}
		if(subext!=null) {
			fileName = fileName.replace("." + subext, "");
		}
		return fileName;
	}
	
	
	/**
	 * Reads the content of a URL as text using the default connect and read timeouts.
	 * @param url The url to get the text from
	 * @return a string representing the text read from the passed URL
	 */
	public static String getTextFromURL(URL url) {
		return getTextFromURL(url, defaultConnectTimeout(), defaultReadTimeout());
	}
	
	/**
	 * Reads the content of a URL as text using the default connect and read timeouts.
	 * @param urlStr The url stringy to get the text from
	 * @return a string representing the text read from the passed URL
	 */
	public static String getTextFromURL(final CharSequence urlStr) {
		return getTextFromURL(toURL(urlStr), defaultConnectTimeout(), defaultReadTimeout());
	}
	
	/**
	 * Reads the content of a URL as a char array using the default connect and read timeouts.
	 * @param url The url to get the text from
	 * @return a char array representing the text read from the passed URL
	 */
	public static char[] getCharsFromURL(URL url) {
		return getTextFromURL(url, defaultConnectTimeout(), defaultReadTimeout()).toCharArray();
	}
	
	/**
	 * Reads the content of a URL as a char array using the default connect and read timeouts.
	 * @param urlStr The url stringy to get the text from
	 * @return a char array representing the text read from the passed URL
	 */
	public static char[] getCharsFromURL(final CharSequence urlStr) {
		return getCharsFromURL(toURL(urlStr));
	}
	
	
	/**
	 * Reads the content of a URL as text
	 * @param url The url to get the text from
	 * @param timeout The connect and read timeout in ms.
	 * @return a string representing the text read from the passed URL
	 */
	public static String getTextFromURL(URL url, int timeout) {
		return getTextFromURL(url, timeout, timeout);
	}
	
	/**
	 * Reads the content of a URL as text
	 * @param url The url to get the text from
	 * @param cTimeout The connect timeout in ms.
	 * @param rTimeout The read timeout in ms.
	 * @return a string representing the text read from the passed URL
	 */
	public static String getTextFromURL(URL url, int cTimeout, int rTimeout) {
		StringBuilder b = new StringBuilder();
		InputStreamReader isr = null;
		BufferedReader br = null;
		InputStream is = null;
		URLConnection connection = null;
		try {
			connection = url.openConnection();
			connection.setConnectTimeout(cTimeout);
			connection.setReadTimeout(rTimeout);
			connection.connect();
			is = connection.getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			String line = null;
			while((line=br.readLine())!=null) {
				b.append(line).append(EOL);
			}
			return b.toString();			
		} catch (Exception e) {
			throw new RuntimeException("Failed to read source of [" + url + "]", e);
		} finally {
			if(br!=null) try { br.close(); } catch (Exception e) {/* No Op */}
			if(isr!=null) try { isr.close(); } catch (Exception e) {/* No Op */}
			if(is!=null) try { is.close(); } catch (Exception e) {/* No Op */}
		}
	}
	
	
	/**
	 * Reads the first <b><code>n</code></b> lines from the resource pointed to by the passed URL
	 * @param url The URL to read the lines from
	 * @param linesToRead The number of lines to read
	 * @return an array of the read lines which will be at most <b><code>linesToRead</code></b> lines long
	 */
	public static String[] getLines(final URL url, final int linesToRead) {
		if(url==null) throw new IllegalArgumentException("The passed URL was null");
		if(linesToRead < 1) throw new IllegalArgumentException("Invalid number of lines [" + linesToRead + "]. Must be >= 1");
		InputStream is = null;
		BufferedReader br = null;
		InputStreamReader isr = null;
		URLConnection connection = null;		
		try {
			connection = url.openConnection();
			connection.setConnectTimeout(1000);
			connection.setReadTimeout(1000);
			connection.connect();
			is = connection.getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			String line = null;
			final List<String> strLines = new ArrayList<String>(linesToRead);
			int linesRead = 0;
			while((line=br.readLine())!=null && linesRead < linesToRead) {
				strLines.add(line);
				linesRead++;
			}
			return strLines.toArray(new String[strLines.size()]);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to read " + linesToRead + " from [" + url + "]");
		} finally {
			if(br!=null) try { br.close(); } catch (Exception x) {/* No Op */}
			if(isr!=null) try { isr.close(); } catch (Exception x) {/* No Op */}
			if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
		}
		
	}
	
	
	/**
	 * Returns the URL for the passed file
	 * @param file the file to get the URL for
	 * @return a URL for the passed file
	 */
	public static URL toURL(File file) {
		try {
			return nvl(file, "Passed file was null").toURI().toURL();
		} catch (Exception e) {
			throw new RuntimeException("Failed to get URL for file [" + file + "]", e);
		}
	}
	
	/**
	 * Determines if the passed stringy represents an existing file name
	 * @param urlStr The stringy to test
	 * @return true if the passed stringy represents an existing file name, false otherwise
	 */
	public static boolean isFile(final CharSequence urlStr) {
		if(urlStr==null || urlStr.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed URL stringy was null or empty");
		return new File(urlStr.toString().trim()).exists();
	}
	
	/**
	 * Determines if the passed stringy looks like a URL by checking for URL like symbols like <b><code>:/</code></b>
	 * @param urlStr The stringy to test
	 * @return true if it looks like, false otherwise
	 */
	public static boolean looksLikeUrl(final CharSequence urlStr) {
		if(urlStr==null || urlStr.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed URL stringy was null or empty");
		return urlStr.toString().indexOf(":/") != -1;
	}
	
	/**
	 * Creates a URL from the passed string 
	 * @param urlStr A char sequence containing a URL representation
	 * @return a URL
	 */
	public static URL toURL(final CharSequence urlStr) {
		if(urlStr==null || urlStr.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed URL stringy was null or empty");
		try {
			if(isFile(urlStr)) return toURL(new File(urlStr.toString()).getAbsoluteFile());
			return new URL(nvl(urlStr, "Passed string was null").toString());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create URL from string [" + urlStr + "]", e);
		}
	}
	
	/**
	 * Creates a URI from the passed string 
	 * @param uriStr A char sequence containing a URI representation
	 * @return a URI
	 */
	public static URI toURI(CharSequence uriStr) {
		try {
			return new URI(nvl(uriStr, "Passed string was null").toString());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create URL from string [" + uriStr + "]", e);
		}
	}
	
	
	
	/**
	 * Reads the content of a URL as a byte array
	 * @param url The url to get the bytes from
	 * @return a byte array representing the text read from the passed URL
	 */
	public static byte[] getBytesFromURL(URL url) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
		InputStream is = null;
		try {
			is = url.openStream();
			int bytesRead = 0;
			byte[] buffer = new byte[8092]; 
			while((bytesRead=is.read(buffer))!=-1) {
				baos.write(buffer, 0, bytesRead);
			}
			return baos.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Failed to read source of [" + url + "]", e);
		} finally {
			if(is!=null) try { is.close(); } catch (Exception e) {/* No Op */}
		}
	}
	
	/**
	 * Returns the last modified time stamp for the passed URL
	 * @param url The URL to get the timestamp for
	 * @return the last modified time stamp for the passed URL
	 */
	public static long getLastModified(final URL url) {
		URLConnection conn = null;
		try {
			conn = nvl(url, "Passed URL was null").openConnection();
			return conn.getLastModified();
		} catch (Exception e) {
			throw new RuntimeException("Failed to get LastModified for [" + url + "]", e);
		} 
	}
	
	/**
	 * Returns the last modified time stamp for the passed file
	 * @param file The file to get the timestamp for
	 * @return the last modified time stamp for the passed file
	 */
	public static long getLastModified(final File file) {
		if(file==null) throw new IllegalArgumentException("The passed file was null");
		if(!file.exists()) throw new IllegalArgumentException("The passed file [" + file + "] does not exist");
		return file.lastModified();
	}
	
	/**
	 * Determines if the passed string is a valid URL
	 * @param urlStr The URL string to test
	 * @return true if is valid, false if invalid or null
	 */
	@SuppressWarnings("unused")
	public static boolean isValidURL(CharSequence urlStr) {
		if(urlStr==null) return false;
		try {
			new URL(urlStr.toString());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/** A host verifier that always returns true */
	public static final HostnameVerifier YESMAN_HOSTVERIFIER = new HostnameVerifier() {
		@Override
		public boolean verify(String hostName, SSLSession sslSession) {
			return true;
		}
	};
	
	
	/** A trust manager that trusts all certs */
	public static final TrustManager[] YESMAN_TRUSTMANAGER = new TrustManager[] {
		new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
			public void checkClientTrusted(X509Certificate[] certs, String authType) {		
				/* No Op */
			}
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
				/* No Op */
			}
		}
	};	
	
	/** An SSL Context that uses the YESMAN_TRUSTMANAGER */
	public static final SSLContext YESMAN_SSLCONTEXT; 
	
	static {
		SSLContext ctx = null;
		try {
			ctx = SSLContext.getInstance("SSL");
			ctx.init(null, YESMAN_TRUSTMANAGER, new java.security.SecureRandom());
		} catch (Throwable t) {
			ctx = null;
		}
		YESMAN_SSLCONTEXT = ctx;
	}
	
	/**
	 * Determines if the passed URL resolves
	 * @param url The URL to test
	 * @return true if is resolves, false otherwise
	 */
	public static boolean resolves(final URL url) {
		if(url==null) return false;
		InputStream is = null;
		HttpsURLConnection httpsConn = null;
		try {
			if("https".equals(url.getProtocol().toLowerCase())) {
				httpsConn = (HttpsURLConnection)url.openConnection();
				httpsConn.setHostnameVerifier(YESMAN_HOSTVERIFIER);
				httpsConn.setSSLSocketFactory(YESMAN_SSLCONTEXT.getSocketFactory());
				is = httpsConn.getInputStream();
				return true;
			}
			is = url.openStream();
			return true;
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return false;
		} finally {			
			if(is!=null) try { is.close(); } catch (Exception e) {/* No Op */}
			if(httpsConn!=null) try { httpsConn.disconnect(); } catch (Exception e) {/* No Op */}
		}
	}
	
	/**
	 * Loads and returns a {@link Properties} from the passed URL
	 * @param url The URL to read from
	 * @return the read properties which will be empty if no properties were read, or
	 * any error occurred while reading.
	 */
	public static Properties readProperties(final URL url) {
		Properties p = new Properties();
		InputStream is = null;
		try {
			is = url.openStream();
			if("XML".equalsIgnoreCase(getExtension(url))) {
				p.loadFromXML(is);
			} else {
				p.load(is);
			}
		} catch (Exception e) {/* No Op */
		} finally {
			if(is!=null) try { is.close(); } catch (Exception e) {/* No Op */}
		}		
		return p;
	}
	
	/**
	 * Determines if this URL represents a writable resource.
	 * For now, only <b><code>file:</code></b> protocol will return true 
	 * (if the file exists and is writable). 
	 * @param url The URL to test for writability
	 * @return true if this URL represents a writable resource, false otherwise.
	 */
	public static boolean isWritable(CharSequence url) {
		return isWritable(toURL(url));
	}
	
	/**
	 * Determines if this URL represents a writable resource.
	 * For now, only <b><code>file:</code></b> protocol will return true 
	 * (if the file exists and is writable). 
	 * @param url The URL to test for writability
	 * @return true if this URL represents a writable resource, false otherwise.
	 */
	public static boolean isWritable(URL url) {
		if(url==null) return false;
		if("file".equals(url.getProtocol())) {
			File file = new File(url.getFile());
			return file.exists() && file.isFile() && file.canWrite();
		}
		return false;
	}
	
	/**
	 * Writes the passed byte content to the URL origin.
	 * @param url The URL to write to
	 * @param content The content to write
	 * @param append true to append, false to replace
	 */
	public static void writeToURL(URL url, byte[] content, boolean append) {
		if(!isWritable(url)) throw new RuntimeException("The url [" + url + "] is not writable", new Throwable());
		if(content==null) throw new RuntimeException("The passed content was null", new Throwable());
		File file = new File(url.getFile());
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file, append);
			fos.write(content);
			fos.flush();
			fos.close();
			fos = null;			
		} catch (IOException ioe) {
			throw new RuntimeException("Failed to write to the url [" + url + "].", ioe);
		} finally {
			if(fos!=null) {
				if(fos!=null) { 
					try { fos.flush(); } catch (Exception ex) {/* No Op */}
					try { fos.close(); } catch (Exception ex) {/* No Op */}
				}
			}
		}
	}
	
	/**
	 * Returns the extension of the passed URL's file
	 * @param url The URL to get the extension of
	 * @return the file extension, or null if the file has no extension
	 */
	public static String getExtension(URL url) {
		return getExtension(url, null);
	}
	
	/**
	 * Returns the extension of the passed file
	 * @param file  The file to get the extension of
	 * @return the file extension, or null if the file has no extension
	 */
	public static String getExtension(final File file) {
		return getExtension(toURL(file), null);
	}
	
	
	/**
	 * Returns the extension of the passed URL's file
	 * @param url The URL to get the extension of
	 * @param defaultValue The default value to return if there is no extension
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getExtension(URL url, String defaultValue) {
		if(url==null) throw new RuntimeException("The passed url was null", new Throwable());
		String file = url.getFile();
		if(file.lastIndexOf(".")==-1) {
			return defaultValue;
		}
		return file.substring(file.lastIndexOf(".")+1);
	}
	
	/**
	 * Returns the extension of the passed name
	 * @param name The name to get the extension of
	 * @param defaultValue The default value to return if there is no extension
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getExtension(final CharSequence name, final String defaultValue) {
		if(name==null || name.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty", new Throwable());
		String file = name.toString().trim();
		if(file.lastIndexOf(".")==-1) {
			return defaultValue;
		}
		return file.substring(file.lastIndexOf(".")+1);		
	}
	
	/**
	 * Returns the extension of the passed name, returning null if there is no extension
	 * @param name The name to get the extension of
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getExtension(final CharSequence name) {
		return getExtension(name, null);
	}

	/**
	 * Returns the extension of the passed file
	 * @param file The file to get the extension of
	 * @param defaultValue The default value to return if there is no extension
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getExtension(final File file, final String defaultValue) {
		return getExtension(toURL(file), defaultValue);
	}
	
	/**
	 * Returns the subextension of the passed URL which is the dot segment prior to the extension,
	 * so if the {@link URL#getFile()} is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>.
	 * @param url The URL to get the subextension for
	 * @param defaultValue The default value if there is no subextension or it is empty
	 * (i.e. the {@link URL#getFile()} was, implausibly, <b><code>foo/bar..ext</code></b>. 
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final URL url, final String defaultValue) {
		if(url==null) throw new IllegalArgumentException("The passed url was null", new Throwable());
		String file = url.getFile();
		String[] segments = DOT_SPLITTER.split(file);
		if(segments.length <= 2) return defaultValue;
		return segments[segments.length-2];
	}
	
	/**
	 * Returns the subextension of the passed URL which is the dot segment prior to the extension,
	 * so if the {@link URL#getFile()} is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>. Returns null if there is no subextension
	 * @param url The URL to get the subextension for
	 * (i.e. the {@link URL#getFile()} was, implausibly, <b><code>foo/bar..ext</code></b>. 
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final URL url) {
		return getSubExtension(url, null);
	}	
	
	/**
	 * Returns the subextension of the passed file which is the dot segment prior to the extension,
	 * so if the {@link File#getName()} is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>.
	 * @param file The File to get the subextension for
	 * @param defaultValue The default value if there is no subextension or it is empty
	 * (i.e. the {@link File#getName()} was, implausibly, <b><code>foo/bar..ext</code></b>. 
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final File file, final String defaultValue) {
		return getSubExtension(toURL(file), defaultValue);
	}
	
	/**
	 * Returns the subextension of the passed file which is the dot segment prior to the extension,
	 * so if the {@link File#getName()} is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>. Returns null if there is no subextension
	 * @param file The File to get the subextension for
	 * (i.e. the {@link File#getName()} was, implausibly, <b><code>foo/bar..ext</code></b>. 
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final File file) {
		return getSubExtension(file, null);
	}
	

	/**
	 * Returns the subextension of the passed name which is the dot segment prior to the extension,
	 * so if the name is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>.
	 * @param name The name to get the subextension for
	 * @param defaultValue The default value if there is no subextension or it is empty
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final CharSequence name, final String defaultValue) {
		if(name==null || name.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty", new Throwable());
		String file = name.toString().trim();
		String[] segments = DOT_SPLITTER.split(file);
		if(segments.length <= 2) return defaultValue;
		return segments[segments.length-2];
	}
	
	/**
	 * Returns the subextension of the passed name which is the dot segment prior to the extension,
	 * so if the name is <b><code>foo/bar.sub.ext</code></b>, the subextension
	 * would be <b><code>sub</code></b>. Returns null if there is no subextension
	 * @param name The name to get the subextension for
	 * @return the subextension or the default if there was none or it was empty
	 */
	public static String getSubExtension(final CharSequence name) {
		return getSubExtension(name, null);
	}
	

	
	/**
	 * Returns the extension of the passed file
	 * @param f The file to get the extension of
	 * @return the file extension, or null if the file has no extension
	 */
	public static String getFileExtension(final File f) {
		return getFileExtension(f, null);
	}
	
	
	/**
	 * Returns the extension of the passed file
	 * @param f The file to get the extension of
	 * @param defaultValue The default value to return if there is no extension
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getFileExtension(File f, String defaultValue) {
		if(f==null) throw new RuntimeException("The passed file was null", new Throwable());
		return getExtension(toURL(f), defaultValue);		
	}
	
	/**
	 * Returns the extension of the passed file name
	 * @param f The file name to get the extension of
	 * @return the file extension, or null if the file has no extension
	 */
	public static String getFileExtension(String f) {
		return getFileExtension(f, null);
	}
	
	/**
	 * Returns the extension of the passed file name
	 * @param f The file name to get the extension of
	 * @param defaultValue The default value to return if there is no extension
	 * @return the file extension, or the default value if the file has no extension
	 */
	public static String getFileExtension(String f, String defaultValue) {
		if(f==null) throw new RuntimeException("The passed file was null", new Throwable());
		return getExtension(toURL(new File(f)), defaultValue);		
	}
	
	/**
	 * Sets the last modified time on the file underlying the passed URL.
	 * Ignored if the file does not exist or if the protocol of the URL is not <b><code>file</code></b>.
	 * @param url The URL to touch the file for
	 * @param touchTime The time to set
	 */
	public static void touch(final URL url, final long touchTime) {
		if(url==null) throw new IllegalArgumentException("The passed url was null");
		if("file".equals(url.getProtocol())) {
			final File f = new File(url.getFile());
			if(f.exists()) {
				f.setLastModified(touchTime);
			}
		}
	}

	/**
	 * Sets the last modified time on the file underlying the passed URL to current.
	 * Ignored if the file does not exist or if the protocol of the URL is not <b><code>file</code></b>.
	 * @param url The URL to touch the file for
	 */
	public static void touch(final URL url) {
		touch(url, System.currentTimeMillis());
	}
	
	/**
	 * Returns an {@link Adler32} checksum of the content from the passed URL
	 * @param url The URL to read the content from
	 * @return the checksum of the content
	 */
	public static long adler32(final URL url) {
		if(url==null) throw new IllegalArgumentException("The passed url was null");		
		final Adler32 ad32 = new Adler32();
		ad32.update(URLHelper.getBytesFromURL(url));
		return ad32.getValue();
	}
	
	/**
	 * Returns an {@link Adler32} checksum of the content from the passed file
	 * @param file The file to read the content from
	 * @return the checksum of the content
	 */
	public static long adler32(final File file) {
		return adler32(toURL(file));
	}


}
