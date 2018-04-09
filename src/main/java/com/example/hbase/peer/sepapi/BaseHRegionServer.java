package com.example.hbase.peer.sepapi;

import com.google.protobuf.Message;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Server;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos;
import org.apache.hadoop.hbase.protobuf.generated.RPCProtos;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;

/**
 * Created by yilong on 2017/8/23.
 */
public class BaseHRegionServer implements AdminProtos.AdminService.BlockingInterface,
Server, org.apache.hadoop.hbase.ipc.PriorityFunction {
    public AdminProtos.GetRegionInfoResponse getRegionInfo(RpcController rpcController, AdminProtos.GetRegionInfoRequest getRegionInfoRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.GetStoreFileResponse getStoreFile(RpcController rpcController, AdminProtos.GetStoreFileRequest getStoreFileRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.GetOnlineRegionResponse getOnlineRegion(RpcController rpcController, AdminProtos.GetOnlineRegionRequest getOnlineRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.OpenRegionResponse openRegion(RpcController rpcController, AdminProtos.OpenRegionRequest openRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.WarmupRegionResponse warmupRegion(RpcController rpcController, AdminProtos.WarmupRegionRequest warmupRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.CloseRegionResponse closeRegion(RpcController rpcController, AdminProtos.CloseRegionRequest closeRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.UpdateFavoredNodesResponse updateFavoredNodes(RpcController controller, AdminProtos.UpdateFavoredNodesRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.FlushRegionResponse flushRegion(RpcController rpcController, AdminProtos.FlushRegionRequest flushRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.SplitRegionResponse splitRegion(RpcController rpcController, AdminProtos.SplitRegionRequest splitRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.CompactRegionResponse compactRegion(RpcController rpcController, AdminProtos.CompactRegionRequest compactRegionRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.MergeRegionsResponse mergeRegions(RpcController rpcController, AdminProtos.MergeRegionsRequest mergeRegionsRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.ReplicateWALEntryResponse replicateWALEntry(RpcController rpcController, AdminProtos.ReplicateWALEntryRequest replicateWALEntryRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.ReplicateWALEntryResponse replay(final RpcController controller, final AdminProtos.ReplicateWALEntryRequest request) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }
  /*
  @Override
  public org.apache.hadoop.hbase.protobuf.generated.ClientProtos.MultiResponse replay(
          com.google.protobuf.RpcController controller,
          org.apache.hadoop.hbase.protobuf.generated.ClientProtos.MultiRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }
  */

    public AdminProtos.RollWALWriterResponse rollWALWriter(RpcController rpcController, AdminProtos.RollWALWriterRequest rollWALWriterRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.GetServerInfoResponse getServerInfo(RpcController rpcController, AdminProtos.GetServerInfoRequest getServerInfoRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public AdminProtos.StopServerResponse stopServer(RpcController rpcController, AdminProtos.StopServerRequest stopServerRequest) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Configuration getConfiguration() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public ZooKeeperWatcher getZooKeeper() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public ServerName getServerName() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void abort(String s, Throwable throwable) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isAborted() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void stop(String s) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isStopped() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public int getPriority(org.apache.hadoop.hbase.protobuf.generated.RPCProtos.RequestHeader header,
                           com.google.protobuf.Message param) {
        return org.apache.hadoop.hbase.HConstants.NORMAL_QOS;
    }

    public int getPriority(RPCProtos.RequestHeader requestHeader, Message message, User user) {
        return org.apache.hadoop.hbase.HConstants.NORMAL_QOS;
    }

    public long getDeadline(org.apache.hadoop.hbase.protobuf.generated.RPCProtos.RequestHeader header, com.google.protobuf.Message param) {
        return 0;
    }

    public org.apache.hadoop.hbase.client.ClusterConnection getConnection() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public org.apache.hadoop.hbase.zookeeper.MetaTableLocator getMetaTableLocator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public org.apache.hadoop.hbase.CoordinatedStateManager getCoordinatedStateManager() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public org.apache.hadoop.hbase.ChoreService getChoreService() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public org.apache.hadoop.hbase.protobuf.generated.AdminProtos.UpdateConfigurationResponse updateConfiguration(
            com.google.protobuf.RpcController controller,
            org.apache.hadoop.hbase.protobuf.generated.AdminProtos.UpdateConfigurationRequest request)
            throws com.google.protobuf.ServiceException {
        throw new UnsupportedOperationException("Not implemented");
    }

}
