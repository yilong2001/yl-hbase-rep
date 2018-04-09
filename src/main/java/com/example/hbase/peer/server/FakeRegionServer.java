package com.example.hbase.peer.server;

import com.example.hbase.peer.sepapi.SepModel;
import com.example.hbase.peer.sepapi.util.io.Closer;
import com.example.hbase.peer.sepapi.util.zk.StateWatchingZooKeeper;
import com.example.hbase.peer.sepapi.util.zk.ZooKeeperItf;
import com.example.hbase.peer.sepimpl.SepConsumer;
import com.example.hbase.peer.sepimpl.SepModelImpl;
import com.example.hbase.peer.seplistener.LogEventListener;
import com.example.hbase.peer.server.conf.ConfKeys;
import com.example.hbase.peer.server.conf.HBaseSlaveConf;
import com.example.hbase.peer.server.conf.SlaveParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;

/**
 * Created by yilong on 2017/8/23.
 */
public class FakeRegionServer {
    private final static Log log = LogFactory.getLog(FakeRegionServer.class);

    private ZooKeeperItf zk;
    private SlaveParams slaveParams;
    private SepConsumer sepConsumer;
    private SepModel sepModel;

    public static void main(String[] args) throws Exception {
        new FakeRegionServer().run(args);
    }

    public void run(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHandler()));

        Configuration conf = HBaseSlaveConf.create();

        String subscriptionId = conf.get(ConfKeys.ZK_SUBSCRIPTION_ID);
        if (subscriptionId == null) {
            throw new RuntimeException("subscriptionId is null");
        }

        conf.set("hbase.regionserver.hostname", "localhost");
        if (args.length > 0) {
            conf.set("hbase.regionserver.hostname", args[0]);
        }

        slaveParams = new SlaveParams(subscriptionId, System.currentTimeMillis());

        startServices(conf);
    }

    public void startServices(Configuration conf) throws Exception {
        String hostname = conf.get("hbase.regionserver.hostname");

        log.debug("Using hostname " + hostname);

        String zkConnectString = conf.get(ConfKeys.ZK_CONNECT_STRING);
        log.info("*** zk conn : " + zkConnectString);

        int zkSessionTimeout = HBaseSlaveConf.getSessionTimeout(conf);
        zk = new StateWatchingZooKeeper(zkConnectString, zkSessionTimeout);

        sepModel = new SepModelImpl(zk, conf);
        sepModel.addSubscription(slaveParams.getSubscriptionId());

        String zkRoot = conf.get(ConfKeys.ZK_ROOT_NODE);

        int threads = conf.getInt("hbaseslave.indexer.threads", 10);
        sepConsumer = new SepConsumer(slaveParams.getSubscriptionId(),
                slaveParams.getSubscriptionTimestamp(),
                new LogEventListener(),
                threads,
                hostname,
                zk,
                conf,
                null);

        sepConsumer.start();

        Thread.sleep(Integer.MAX_VALUE);
    }

    public void stopServices() {
        try {
            sepModel.removeSubscription(slaveParams.getSubscriptionId());
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.debug("Stopping side-effect processor consumer");
        Closer.close(sepConsumer);

        log.debug("Stopping ZooKeeper connection");
        Closer.close(zk);
    }

    public class ShutdownHandler implements Runnable {
        @Override
        public void run() {
            stopServices();
        }
    }
}
