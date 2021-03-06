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
package com.heliosapm.utils.classload.memurl;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * <p>Title: MemoryURLStreamHandlerFactory</p>
 * <p>Description: A {@link URLStreamHandlerFactory} implementation to support URL representing in memory buffers. Copied from <a href="http://tika.apache.org/">Apache Tika</a></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.memurl.MemoryURLStreamHandlerFactory</code></p>
 */

public class MemoryURLStreamHandlerFactory implements URLStreamHandlerFactory {

    /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandlerFactory#createURLStreamHandler(java.lang.String)
     */
    @Override
	public URLStreamHandler createURLStreamHandler(final String protocol) {
        if ("mem".equals(protocol)) {
            return new Handler();
        }
		return null;
    }


}
