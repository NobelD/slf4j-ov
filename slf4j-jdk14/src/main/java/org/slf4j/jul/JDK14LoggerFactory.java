/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package org.slf4j.jul;

import org.slf4j.Logger;
import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.DecoratorResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JDK14LoggerFactory is an implementation of {@link ILoggerFactory} returning
 * the appropriately named {@link JDK14LoggerAdapter} instance.
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public class JDK14LoggerFactory implements ILoggerFactory {

    // key: name (String), value: a JDK14LoggerAdapter;
    ConcurrentMap<String, Logger> loggerMap;

    /**
     * the root logger is called "" in JUL
     */
    private static String JUL_ROOT_LOGGER_NAME = "";
    private static Map<String, DecoratorResolver> decorator = new HashMap<>();
    
    public JDK14LoggerFactory() {
        loggerMap = new ConcurrentHashMap<>();
        // ensure jul initialization. see SLF4J-359
        // note that call to java.util.logging.LogManager.getLogManager() fails on the Google App Engine platform. See
        // SLF4J-363
        java.util.logging.Logger.getLogger("");
    }


    /**
     * Registers a replacement/prefix for logger.<br>
     * Some examples:<br>
     * <br>
     * java.util.* - Java -> java.util.logging.Logger = Java.Logger<br>
     * java.util.*~ - Java -> java.util.logging.Logger = Java.logging.Logger<br>
     * java.util.logging.Logger - null -> java.util.logging.Level = java.util.logging.Level<br>
     * java.util.logging.Logger - null -> java.util.logging.Logger = Logger
     *
     * @param start the start of the class
     * @param replacement the replacement to use
     * @return true if no other value with the same existed
     */
    public static boolean registerDecorator(String start, String replacement) {
        return DecoratorResolver.registerTo(decorator, start, replacement);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.slf4j.ILoggerFactory#getLogger(java.lang.String)
     */
    public Logger getLogger(String name) {
        // the root logger is called "" in JUL
        if (name.equalsIgnoreCase(Logger.ROOT_LOGGER_NAME)) {
            name = JUL_ROOT_LOGGER_NAME;
        }

        name = DecoratorResolver.resolve(decorator, name);

        Logger slf4jLogger = loggerMap.get(name);
        if (slf4jLogger != null)
            return slf4jLogger;
        else {
            java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(name);
            Logger newInstance = new JDK14LoggerAdapter(julLogger);
            Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
            return oldInstance == null ? newInstance : oldInstance;
        }
    }
}
