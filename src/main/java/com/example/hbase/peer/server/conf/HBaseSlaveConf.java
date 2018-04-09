package com.example.hbase.peer.server.conf;

import org.apache.hadoop.conf.Configuration;

/**
 * Created by yilong on 2017/8/24.
 */
public class HBaseSlaveConf {

    private HBaseSlaveConf() {}

    public static Configuration create() {
        Configuration conf = new Configuration();
        return addHbaseIndexerResources(conf);
    }

    public static Configuration addHbaseIndexerResources(Configuration conf) {
        //conf.addResource("hbase-default.xml");
        conf.addResource("hbase-site.xml");
        //conf.addResource("hbase-indexer-default.xml");
        //conf.addResource("hbase-indexer-site.xml");

        checkDefaultsVersion(conf);
        return conf;
    }

    public static int getSessionTimeout(Configuration conf) {
        return conf.getInt(ConfKeys.ZK_SESSION_TIMEOUT, 30000);
    }

    private static void checkDefaultsVersion(Configuration conf) {
        if (conf.getBoolean(ConfKeys.VERSION_SKIP, Boolean.FALSE)) return;
        //String defaultsVersion = conf.get(ConfKeys.DEFAULTS_VERSION);
        //String thisVersion = VersionInfo.getVersion();
        //if (!thisVersion.equals(defaultsVersion)) {
        //    throw new RuntimeException(
        //            "hbase-indexer-default.xml file seems to be for and old version of HBase Indexer (" +
        //                    defaultsVersion + "), this version is " + thisVersion);
        //}
    }

    /**
     * For debugging. Dump configurations to system output as xml format.
     */
    public static void main(String[] args) throws Exception {
        HBaseSlaveConf.create().writeXml(System.out);
    }
}
