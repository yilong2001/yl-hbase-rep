# hbase zk 节点

## 1 Operation节点

### 1.1 hbase (zookeeper.znode.parent)
 根节点包含所有被`HBase创建或使用`的节点
 
### 1.2 /hbase/hbaseid (zookeeper.znode.clusterId)
 `HBase Master`用UUID标示一个集群。这个`clusterId`也保存在HDFS上：hdfs:/<namenode>:<port>/hbase/hbase.id

### 1.3 /hbase/root-region-server (zookeeper.znode.rootserver)'
 `root region`的位置信息。客户端通过 这个节点获取 `Root RegionServer` 和 `META` 信息。

### 1.4 /hbase/rs (zookeeper.znode.rs)
 `RegionServer`在启动的时候，会创建一个子节点(例如：`/hbase/rs/m1.host`)，以标示`RegionServer`的在线状态。
 `Hbase Master`监控这个节点，以获取所有 `Online RegionServer`，用于`Assignment/Balancing`。
 
### 1.5 /hbase/unassigned (zookeeper.znode.unassigned)
 标示`unassigned region`的节点。
 `Assignment Manager`通过这个节点发现未分配的`Region`。
 
### 1.6 /hbase/master (zookeeper.znode.master)
 `Active Master`会在这个节点注册自己(启动时候)，通过这个节点标示哪个`Master`是`Active`的。

### 1.7 /hbase/backup-masters(zookeeper.znode.backup.masters)
 每个`Inactive Master`会在这个节点创建子节点，以标示自己为`Backup Master`。
 这个节点主要用于哪个节点有可能成为Master节点，以备不时之需。

### 1.8 /hbase/shutdown (zookeeper.znode.state)
 描述`HBase集群`状态，由`HBase Master StartUp`时创建、在`Master Shutdown`时删除。
 RegionServer会监控这个节点。 

### 1.9 /hbase/draining (zookeeper.znode.draining.rs)
 创建这个子节点，用于`停服务`一个或多个`RegionServer`，其形式为：serverName,port,startCode (例如: `/hbase/draining/m1.host,60020,1338936306752`)
 你可以`停服务`一个或多个`RegionServer`，而不用担心会有`Region`临时移动到这些即将被停服务的`RegionServer`。

### 1.10 /hbase/table(zookeeper.znode.masterTableEnableDisable)
 在`assignments (例如：disabling/enabling states)`期间，`Master`追踪 `table 状态`。

### 1.11/hbase/splitlog (zookeeper.znode.splitlog)
 用于`log splitter`时，追踪`pending`状态的log，以用于`重放`和`分配`。

## 2 Replication节点
![Replication_ZK_Nodes](https://github.com/yilong2001/yl-hbase-rep/blob/master/img/hbase_zk_node.png)

 Replication zondes不是描述一个临时状态，意味着replication 是可信的数据源，描述了每个节点的replication状态。

### 2.1 /hbase/replication (zookeeper.znode.replication)
 包含 HBase replication state information 的根节点

### 2.2 /hbase/replication/peers(zookeeper.znode.replication.peers)
 每个 peer 有一个子节点(例如：`/hbase/replication/peers/<ClusterID>`)，包含能够连接到 `peer` 的 `zookeeper` 地址。
 一个hbase 表可以同时replication到多个peer。

### 2.3 /hbase/replication/peers/<ClusterId>/peer-state(zookeeper.znode.replication.peers.state)
 每个子节点(`/hbase/replication/peer-state/<ClusterID>`)将追踪peer的使能、不使能状态。

### 2.4 /hbase/replication/rs (zookeeper.znode.replication.rs)
 包含主集群 `RegionServers` 列表 (`/hbase/replication/rs/<region server>`)。
 对每个 `RegionServer` 节点，都有它要 `replication` 数据过去的一个 `per peer` 子节点。
 在 `peer` 子节点内，`hlogs`等待被 `repication` ，以这个路径表示: (`/hbase/replication/rs/<region server>/<ClusterId>/<hlogName>`)

### other
