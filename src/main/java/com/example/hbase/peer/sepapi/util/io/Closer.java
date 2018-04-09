package com.example.hbase.peer.sepapi.util.io;

/**
 * Created by yilong on 2017/8/23.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.lang.reflect.Method;


public class Closer {
    /**
     * Closes anything {@link Closeable}, catches any throwable that might occur during closing and logs it as an error.
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable t) {
                Log log = LogFactory.getLog(Closer.class);
                log.error("Error closing object of type " + closeable.getClass().getName(), t);
            }
        }
    }

    public static void close(Object object) {
        if (object != null) {
            try {
                Method closeMethod = null;
                Method[] methods = object.getClass().getMethods();
                for (Method method : methods) {
                    if (method.getParameterTypes().length == 0) {
                        if (method.getName().equals("close")) {
                            closeMethod = method;
                            break;
                        } else if (method.getName().equals("shutdown")) {
                            closeMethod = method;
                        } else if (method.getName().equals("stop")) {
                            closeMethod = method;
                        }
                    }
                }

                if (closeMethod != null) {
                    closeMethod.invoke(object);
                } else {
                    Log log = LogFactory.getLog(Closer.class);
                    log.error("Do not know how to close object of type " + object.getClass().getName());
                }
            } catch (Throwable t) {
                Log log = LogFactory.getLog(Closer.class);
                log.error("Error closing object of type " + object.getClass().getName(), t);
            }
        }
    }
}
