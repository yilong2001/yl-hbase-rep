package com.example.hbase.peer.server.conf;

/**
 * Created by yilong on 2017/8/24.
 */
public class ConfKeys {
    /** Default root ZooKeeper node */
    public static final String DEFAULT_ZK_ROOT_NODE = "/hbaseslave/slave1";

    public static final String ZK_SUBSCRIPTION_ID =  "zookeeper.znode.subscription.id";


    public static final String ZK_SESSION_TIMEOUT = "hbaseslave.zookeeper.session.timeout";

    public static final String ZK_CONNECT_STRING = "hbaseslave.zookeeper.connectstring";

    public static final String ZK_ROOT_NODE = "hbaseslave.zookeeper.znode.parent";

    /**
     * Name of the Ganglia server to report to. Metrics reporting to Ganglia is only enabled
     * if a value is present under this key in the configuration.
     */
    public static final String GANGLIA_SERVER = "hbaseslave.metrics.ganglia.server";

    /** Port to report to for Ganglia. */
    public static final String GANGLIA_PORT = "hbaseslave.metrics.ganglia.port";

    /** Ganglia reporting interval, in seconds. */
    public static final String GANGLIA_INTERVAL = "hbaseslave.metrics.ganglia.interval";


    public static final String VERSION_SKIP = "hbaseslave.defaults.for.version.skip";

    public static final String DEFAULTS_VERSION = "hbaseslave.defaults.for.version";
}
