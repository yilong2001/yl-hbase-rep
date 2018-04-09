# replication源码分析
[pic]

## 1 ReplicationSourceManager

- 管理 Replication Source：
- -- Normal sources are persistent and one per peer cluster；
- -- Old sources are recovered from a failed region server and our only goal is to finish replicating the WAL queue it had up in ZK；

如果一个region server挂掉, 这个对象作为一个观察者能够得到通知，这时它将尝试获取锁、以便于在`local old source`上面完成待同步的 WAL 文件。

## 2 ReplicationQueues／ReplicationQueuesZKImpl

- `ReplicationQueues`是接口
- `ReplicationQueuesZKImpl`是 `ReplicationQueues` 的实例
- `myQueuesZnode` : 这个Region Server上的所有等待同步的WAL文件的队列 zk 节点
- -- `myQueuesZnode`由Region Server名（包含：host、port、start code）组成）
- -- 例如：/hbase/replication/rs/hostname.example.org,6020,1234

## 3 WAL 待复制队列 (/hbase/replication/rs/hostname.example.org,6020,1234 子节点)

在这个znode里面，region server维护了一个WAL复制队列的集合。这些队列由child znode表示。例如：
- /hbase/replication/rs/hostname.example.org,6020,1234/1
- /hbase/replication/rs/hostname.example.org,6020,1234/2
- 其中 1、2表示队列ID，即待复制数据的目标Cluster的ID

## 4 WAL 待复制文件 (/hbase/replication/rs/hostname.example.org,6020,1234/1 的子节点)

这个子节点表示等待复制的WAL文件的位置。
- 每个Region Server只有一个WAL文件队列
- 可能是多个WAL文件待复制，但这些文件是有序的，可以通过一个唯一的文件位置来表示待复制的WAL文件信息
- 例如：`/hbase/replication/rs/hostname.example.org,6020,1234/1/23522342.23422 [VALUE: 254]`

## 5 ReplicationPeers／ReplicationPeersZKImpl

提供了一个管理 ReplicationPeers（待复制数据的目标Cluster）的具体实现。
- `peers znode`包含所有peer集群的列表，以及当前复制的状态；
- 每个`peer cluster`只有一个`peer znode`，`peer znode`以`cluster id`命名。
- `peer zoned`的示例如下：
- -- /hbase/replication/peers/1 [Value: zk1.host.com,zk2.host.com:2181:/hbase]
- -- /hbase/replication/peers/2 [Value: zk5.host.com,zk6.host.com:2181:/hbase]


- 每个peer znode有一个子节点指示是否开启／关闭replication
- 由（ReplicationPeer.PeerStateTracker）负责维护更新此状态。
- 例如：/hbase/replication/peers/1/peer-state [Value: ENABLED]

## 6 ReplicationPeer.TableCFsTracker

- 负责维护待 replication 表和列簇的信息
- /hbase/replication/peers/1/tableCFs [Value: "table1; table2:cf1,cf3; table3:cfx,cfy"]

## 7 ReplicationTracker／ReplicationTrackerZKImpl

- 处理定义在ReplicationListener中的replication事件。Watch的事件包括：
- -- OtherRegionServerWatcher , 用于处理local cluster 其他Region Server挂机的事件。如果它能获取到锁，将会完成已down RS的剩余WAL数据的replication。
- 问：为什么一个Region Server 能够替代 downed Region Server 传输WAL数据？ Region Server 不是与 WAL 一一对应的吗？
- 答：WAL文件都存储在HDFS上，所有 Region Server 都能读取所有 WAL，只不过一个Region Server只负责自己WAL文件的Replication。

## 8 PeersWatcher
- 负责通知 ReplicationListener 处理 peer clusters 的创建和删除事件。

## 9 ReplicationSourceInterface／ReplicationSource
- 处理 Replication 的 stream 过程。
- -- 当前只处理一个 `peer cluster`
- -- 对每个 `peer cluster`，它选择一个随机的 peer
- -- 使用 replication ratio 。例如，如果 replication ratio = 0.1、peer cluster 有100 region servers，将选择10个
- -- 如果 `peer cluster` 的 `region server` 超过55s不能连接的话, 一个流就会被认为 down 掉

## 10 replication同步过程 (replication function in HBaseInterClusterReplicationEndpoint)
 
- 连接到对端集群的 `Region Server` : 在ZK查找对端 `cluster` 的 RS 节点，使用 `protocolbuf RPC` 接口连接 peer RS
- 如果peer RS有100个，会根据 replication ratio 参数，随机选择其中一部分RS。比如，如果ratio=0.1，就会选择其中10个。
- `ReplicationSourceWALReader` 负责读取 WAL，放入`BlockingQueue<>`，类型是`WALEntryBatch`；
- `HBaseInterClusterReplicationEndpoint` 负责从 `ReplicationSourceWALReader` 获取这些 `WALEntryBatch` 类型的数据；
- -- 构造 `Replicator` 对象（继承`Callable`，`override call`方法），放入`ExecutorCompletionService`类型的线程池之中执行；
- -- `Replicator` 的 call 方法：
- -- 通过`ReplicationSinkManager`获取`SinkPeer`
- -- `SinkPeer`获取`Replication Region Server（RPC server）`的地址
- -- 调用`Replication Region Server`的`replicateWALEntry`方法，完成`WAL edits`数据的同步
- 调用 `ReplicationSinkManager reportSinkSuccess` 方法，确认同步成功

### 10.1 replication同步过程 : 初始化

1、使用`ReplicationFactory`获取`ReplicationQueuesZKImpl`；

2、使用`ReplicationFactory`获取`ReplicationPeersZKImpl`；

3、使用`ReplicationFactory`获取`ReplicationTrackerZKImpl`；

4、获取本`hbase cluster`的`UUID`；

5、构造`ReplicationSourceManager`，其中`ReplicationQueuesZKImpl`、`ReplicationPeersZKImpl`、`ReplicationTrackerZKImpl`、`UUID`、`HRegionServer`等作为初始化参数；

5.1、`ReplicationSourceManager`注册`this`作为`ReplicationTrackerZKImpl`的`ReplicationListener`；

5.2、`ReplicationQueuesZKImpl`从`ZK`读取所有`peer IDs`；

5.3、创建`ThreadPoolExecutor`，线程池用来调度`AdoptAbandonedQueuesWorker`（一个线程），并且在监测到`Region Failure`并且获取到`lock`的时候，启动`Transfer`线程（每个失败的Region启动一个）；

5.4、创建`ConnectionImplementation`，包括：

5.4.1、创建`ServerStatisticTracker`；

5.4.2、创建`PreemptiveFastFailInterceptor`，作为`RpcRetryingCallerFactory`的参数；

5.4.3、创建`RpcControllerFactory`；通过`RpcControllerFactory`能够得到`HBaseRpcControllerImpl`；通过`HBaseRpcControllerImpl`，执行`RpcCallback`的具体行为；

5.4.4、创建`RpcRetryingCallerFactory`；通过`RpcRetryingCallerFactory`能够得到`RpcRetryingCallerImpl`；通过`RpcRetryingCallerImpl`执行重试，并且在重试的过程中，通过`PreemptiveFastFailInterceptor`能够完成快速失败；

5.4.5、创建`ClientBackoffPolicy`；在发生失败之后，决定回退策略；

5.4.6、创建`AsyncProcess`，可以提交`submit`，并返回`AsyncRequestFuture`（`replication`过程未使用）；

5.4.7、创建`hadoop.hbase.client.ZooKeeperRegistry`；通过这个`Registry`接口，可以获取：`getMetaRegionLocation`、`getClusterId`、`getCurrentNrHRS`；

5.4.8、通过`RpcClientFactory`创建`NettyRpcClient`（or BlockingRpcClient）；


### 10.1 replication同步过程 : 启动 replication 服务
1、调用`ReplicationSourceManager`的`init`函数，启动`ReplicationSourceManager`；

1.1、调用`ReplicationQueuesZKImpl`的`getConnectedPeerIds`函数，获取所有`peer cluster id`；

1.2、遍历所有`peer id`，调用`ReplicationPeersZKImpl`的`getConnectedPeer`得到`ReplicationPeer`对象；

1.3、通过`ReplicationSourceFactory`，根据`peerId`和`conf`，得到`ReplicationSource`对象（每个`peer `对应一个`ReplicationSource`）；

1.4、读取最新的WAL文件，在peer对应的ZK节点，使用`ReplicationQueuesZKImpl`的`addLog`函数，增加这些WAL文件；以备同步；

1.5、调用`ReplicationSource`的`enqueueLog`，等待`replication`给`peer`；

1.6、调用`ReplicationSource`的`startup`，启动`ReplicationSource`线程；

2、创建`AdoptAbandonedQueuesWorker`线程对象，在`ThreadPoolExecutor`中进行调度；


### 10.1 replication同步过程 : ReplicationSource 的 enqueueLog

1、获取`logPrefix`，例如`WAL filename`可能是如下：
- /hbase/.logs/srv.example.com,60020,1254173957298/srv.example.com,60020,1254173957298.1254173957495
- logPrefix : /hbase/.logs/srv.example.com,60020,1254173957298/srv.example.com,60020,1254173957298

2、判断对应`logPrefix`前缀的`PriorityBlockingQueue<Path>`是否存在，如果存在，说明已经为该`Region Server`上的`WAL files`启动了同步线程；否则，创建`ReplicationSourceShipper`线程对象，构造一个`ReplicationSourceWALReader`线程对象作为`WAL edits`数据的来源线程；

3、`ReplicationSourceShipper`和`ReplicationSourceWALReader`之间，通过`PriorityBlockingQueue`传输日志数据；

4、将`WAL log`文件放入`PriorityBlockingQueue`；

5、更新`WAL log`统计数据；

### 10.1 replication同步过程 : ReplicationSource 的 run
1、调用`HBaseInterClusterReplicationEndpoint`的`startAsync`函数（实际调用的是`AbstractService`的`startAsync`）；

1.1、调用`hbase.shaded.com.google.common.util.concurrent.Monitor`对象的`enterIf`接口（参数是 `AbstractService.IsStartableGuard`类型对象），如果条件满足（`IsStartableGuard`为`true`：`AbstractService.this.state() == State.NEW`）则获取`ReentrantLock`；

1.2、否则，如果`AbstractService.IsStartableGuard`不是`true`，则抛出异常；说明`ReplicationSource`线程已经启动过了（此时，`AbstractService的state`肯定不是`NEW`）；

1.3、使用`AbstractService.StateSnapshot`保存`state`为`State.STARTING`；

1.4、调用`ListenerCallQueue<Listener>`对象的`enqueue`函数，增加`STARTING_EVENT`（`Event< Listener >`对象）；

1.5、调用`HBaseReplicationEndpoint`的`doStart`函数；

1.5.1、构造`ZooKeeperWatcher`对象；

1.5.2、调用`ZooKeeperWatcher`的`registerListener`注册`PeerRegionServerListener`对象；这个`PeerRegionServerListener`对象，在`peer`的`Region Server`发生变化的时候，可以更新本地保存的`peer Region Server list`；

1.5.3、调用`AbstractService` 的`notifyStarted`函数；
（1）获取重入锁`ReentrantLock`；
（2）如果不是`State.STARTING`状态，则跑出异常（有可能线程异常终止）；
（3）`StateSnapshot的shutdownWhenStartupFinishes`为`true`，则停止线程；
（4）否则更新`AbstractService.StateSnapshot`为`State.RUNNING`；
（5）调用`ListenerCallQueue<Listener>`对象的`enqueue`函数，增加`RUNNING_EVENT`事件；
（6）释放`ReentrantLock`；

1.6、返回`HBaseInterClusterReplicationEndpoint`对象本身（此时对象的状态已经成为`State.RUNNING`）；

2、调用`HBaseInterClusterReplicationEndpoint`（`AbstractService`） 的`awaitRunning`接口，等待`HBaseInterClusterReplicationEndpoint`确实进入`RUNNING`状态（`10s`超时）；

3、调用`HBaseInterClusterReplicationEndpoint`（`AbstractService`） 的`getPeerUUID`获取`UUID`；

4、调用`HBaseInterClusterReplicationEndpoint`（`BaseReplicationEndpoint`）的`getWALEntryfilter`接口函数，获取`WAL entry` 的`filter`（可忽略掉的`edits`）；

5、增加`ClusterMarkingEntryFilter`；

6、使用`filters`创建`ChainWALEntryFilter`；这些`filtes`在创建`WAL Reader`对象`ReplicationSourceWALReader`的时候会作为初始化参数；在实际读取`WAL` 文件的时候，会首先进行`filter`操作；

7、根据`walGroupId`以及关联的`WAL files`队列`PriorityBlockingQueue<Path>`，创建`ReplicationSourceShipper`对象；

8、启动`ReplicationSourceShipper`线程；

9、设置与`ReplicationSourceShipper`关联的`ReplicationSourceWALReader`对象；

10、保存新创建的`ReplicationSourceShipper`线程对象；


## 11 replication 线程类型

1、`ReplicationStatisticsThread`，线程池定时调度

2、每peer有一个`ReplicationSource`线程

3、每peer有一个`ReplicationSourceShipper`线程

4、每peer有一个`ReplicationSourceWALReader`线程

5、`AdoptAbandonedQueuesWorker`线程 + 多个`NodeFailoverWorker`线程

