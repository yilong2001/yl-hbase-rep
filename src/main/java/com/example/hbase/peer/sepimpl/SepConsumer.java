package com.example.hbase.peer.sepimpl;

import com.example.hbase.peer.sepapi.BaseHRegionServer;
import com.example.hbase.peer.sepapi.EventListener;
import com.example.hbase.peer.sepapi.PayloadExtractor;
import com.example.hbase.peer.sepapi.util.io.Closer;
import com.example.hbase.peer.sepapi.util.zk.ZooKeeperItf;
import com.example.hbase.peer.server.conf.ConfKeys;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.ipc.FifoRpcScheduler;
import org.apache.hadoop.hbase.ipc.PayloadCarryingRpcController;
import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.zookeeper.ZKUtil;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by yilong on 2017/8/23.
 */
public class SepConsumer extends BaseHRegionServer {
    private final String subscriptionId;
    private long subscriptionTimestamp;
    private EventListener listener;
    private final ZooKeeperItf zk;
    private final Configuration hbaseConf;
    private RpcServer rpcServer;
    private ServerName serverName;
    private ZooKeeperWatcher zkWatcher;
    //private SepMetrics sepMetrics;
    private final PayloadExtractor payloadExtractor;
    private String zkNodePath;
    private List<ThreadPoolExecutor> executors;
    boolean running = false;
    private Log log = LogFactory.getLog(getClass());


    public SepConsumer(String subscriptionId, long subscriptionTimestamp,
                       EventListener listener, int threadCnt,
                       String hostName, ZooKeeperItf zk,
                       Configuration hbaseConf,
                       PayloadExtractor payloadExtractor) throws IOException, InterruptedException {
        Preconditions.checkArgument(threadCnt > 0, "Thread count must be > 0");
        this.subscriptionId = SepModelImpl.toInternalSubscriptionName(subscriptionId);
        this.subscriptionTimestamp = subscriptionTimestamp;
        this.listener = listener;
        this.zk = zk;
        this.hbaseConf = hbaseConf;
        //this.sepMetrics = new SepMetrics(subscriptionId);
        this.payloadExtractor = payloadExtractor;
        this.executors = Lists.newArrayListWithCapacity(threadCnt);

        InetSocketAddress initialIsa = new InetSocketAddress(hostName, 0);
        if (initialIsa.getAddress() == null) {
            throw new IllegalArgumentException("Failed resolve of " + initialIsa);
        }
        String name = "regionserver/" + initialIsa.toString();
        this.rpcServer = new RpcServer(this, name, getServices(),
        /*HBaseRPCErrorHandler.class, OnlineRegions.class},*/
                initialIsa, // BindAddress is IP we got for this server.
                //hbaseConf.getInt("hbase.regionserver.handler.count", 10),
                //hbaseConf.getInt("hbase.regionserver.metahandler.count", 10),
                hbaseConf,
                new FifoRpcScheduler(hbaseConf, hbaseConf.getInt("hbase.regionserver.handler.count", 10)));
          /*
          new SimpleRpcScheduler(
            hbaseConf,
            hbaseConf.getInt(HConstants.REGION_SERVER_HANDLER_COUNT, HConstants.DEFAULT_REGION_SERVER_HANDLER_COUNT),
            hbaseConf.getInt("hbase.regionserver.metahandler.count", 10),
            hbaseConf.getInt("hbase.regionserver.handler.count", 10),
            this,
            HConstants.QOS_THRESHOLD)
          );
          */
        log.info("*** RPC server address : " + rpcServer.getListenerAddress().getPort());
        this.serverName = ServerName.valueOf(hostName, rpcServer.getListenerAddress().getPort(), System.currentTimeMillis());

        log.info("*** RpcServer Name : " + this.serverName.toString());
        log.info("*** RegionServer Name : " + this.serverName.toString());

        this.zkWatcher = new ZooKeeperWatcher(hbaseConf, this.serverName.toString(), null);

        // login the zookeeper client principal (if using security)
        ZKUtil.loginClient(hbaseConf, "hbase.zookeeper.client.keytab.file",
                "hbase.zookeeper.client.kerberos.principal", hostName);

        // login the server principal (if using secure Hadoop)
        User.login(hbaseConf, "hbase.regionserver.keytab.file",
                "hbase.regionserver.kerberos.principal", hostName);

        for (int i = 0; i < threadCnt; i++) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(100));
            //executor.setRejectedExecutionHandler(new WaitPolicy());
            executors.add(executor);
        }
    }

    public void start() throws IOException, InterruptedException, KeeperException {

        rpcServer.start();

        // Publish our existence in ZooKeeper

        zkNodePath = hbaseConf.get(ConfKeys.ZK_ROOT_NODE, ConfKeys.DEFAULT_ZK_ROOT_NODE)
                + "/" + subscriptionId + "/rs/" + serverName.getServerName();
        zk.create(zkNodePath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        this.running = true;
    }

    private List<RpcServer.BlockingServiceAndInterface> getServices() {
        List<RpcServer.BlockingServiceAndInterface> bssi = new ArrayList<RpcServer.BlockingServiceAndInterface>(1);
        bssi.add(new RpcServer.BlockingServiceAndInterface(
                AdminProtos.AdminService.newReflectiveBlockingService(this),
                AdminProtos.AdminService.BlockingInterface.class));
        return bssi;
    }

    public void stop() {
        Closer.close(zkWatcher);
        if (running) {
            running = false;
            Closer.close(rpcServer);
            try {
                // This ZK node will likely already be gone if the index has been removed
                // from ZK, but we'll try to remove it here to be sure
                zk.delete(zkNodePath, -1);
            } catch (Exception e) {
                log.debug("Exception while removing zookeeper node", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        //sepMetrics.shutdown();
        for (ThreadPoolExecutor executor : executors) {
            executor.shutdown();
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public AdminProtos.ReplicateWALEntryResponse replicateWALEntry(final RpcController controller,
                                                                   final AdminProtos.ReplicateWALEntryRequest request) throws ServiceException {
        try {
            List<AdminProtos.WALEntry> entries = request.getEntryList();
            CellScanner cells = ((PayloadCarryingRpcController) controller).cellScanner();

            SepEventExecutor eventExecutor = new SepEventExecutor(listener, executors, 100);

            for (final AdminProtos.WALEntry entry : entries) {
                TableName tableName = (entry.getKey().getWriteTime() < subscriptionTimestamp) ? null :
                        TableName.valueOf(entry.getKey().getTableName().toByteArray());
                log.info("*** replicateWALEntry(tableName):"+tableName);

                if (tableName == null) {
                    continue;
                }

                Multimap<ByteBuffer, Cell> keyValuesPerRowKey = ArrayListMultimap.create();
                final Map<ByteBuffer, byte[]> payloadPerRowKey = Maps.newHashMap();
                int count = entry.getAssociatedCellCount();

                for (int i = 0; i < count; i++) {
                    if (!cells.advance()) {
                        throw new ArrayIndexOutOfBoundsException("Expected=" + count + ", index=" + i);
                    }

                    // this signals to us that we simply need to skip over count of cells
                    if (tableName == null) {
                        continue;
                    }

                    Cell cell = cells.current();
                    ByteBuffer rowKey = ByteBuffer.wrap(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength());
                    log.info("*** replicateWALEntry rowkey:"+ new String(rowKey.array()));
                    KeyValue kv = KeyValueUtil.ensureKeyValue(cell);
                    keyValuesPerRowKey.put(rowKey, kv);
                }

                for (final ByteBuffer rowKeyBuffer : keyValuesPerRowKey.keySet()) {
                    final List<Cell> keyValues = (List<Cell>) keyValuesPerRowKey.get(rowKeyBuffer);

                    final SepEvent sepEvent = new SepEvent(tableName.toBytes(), CellUtil.cloneRow(keyValues.get(0)), keyValues,
                            payloadPerRowKey.get(rowKeyBuffer));
                    eventExecutor.addSepEvent(sepEvent);
                }

                List<Future<?>> futures = eventExecutor.flush();
                waitOnSepEventCompletion(futures);
            }
        }catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return AdminProtos.ReplicateWALEntryResponse.newBuilder().build();
    }

    private void waitOnSepEventCompletion(List<Future<?>> futures) throws IOException {
        // We should wait for all operations to finish before returning, because otherwise HBase might
        // deliver a next batch from the same HLog to a different server. This becomes even more important
        // if an exception has been thrown in the batch, as waiting for all futures increases the back-off that
        // occurs before the next attempt
        List<Exception> exceptionsThrown = Lists.newArrayList();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted in processing events.", e);
            } catch (Exception e) {
                // While we throw the error higher up, to HBase, where it will also be logged, apparently the
                // nested exceptions don't survive somewhere, therefore log it client-side as well.
                log.warn("Error processing a batch of SEP events, the error will be forwarded to HBase for retry", e);
                exceptionsThrown.add(e);
            }
        }

        if (!exceptionsThrown.isEmpty()) {
            log.error("Encountered exceptions on " + exceptionsThrown.size() + " batches (out of " + futures.size()
                    + " total batches)");
            throw new RuntimeException(exceptionsThrown.get(0));
        }
    }
}
