# hbase snapshot 分析

- [1. snapshot与ZK节点](#1-Online)
- [2. hbase表与hbase快照](#2-Table&Snapshot)
- [3. Snapshot 生成过程](#3-Snapshot生成过程)
- [4. Snapshot Archiving](#4-快照存储)

## 1 snapshot与ZK节点
快照和CopyTable/ExportTable的主要区别，是快照仅仅写元数据。生成快照的时候，没有大批量数据的拷贝操作。

HBase设计的主要原则之一，就是一旦一个文件写了之后，就不再修改了。

使用不可变文件意味着快照只需要追踪在生成快照那一刻的`hfile`文件即可，在compaction期间，快照操作只是通知hbase进程，不要删除而是代替已有的`hfile`。

相同的原则应用于`Clone和Restore`操作。因此，一个新表只是连接到这些组成快照的`hfile文件集`。

`Export快照`是唯一要求数据拷贝的操作，因为其他集群没有这些数据文件。

### 1.1 /hbase/online-snapshot/acquired
 `acquired`节点记录 `snapshot`的第一步。`hbase master`会为本次快照创建一个子节点`(/hbase/online-snapshot/acquired/<snapshot name>)`。
 
 `Master` 会通知所有`region server`这个子节点已经创建完成，`region server`开始准备`snapshot`。
 
 当某个 `region server`完成快照，会在这个子节点下面增加一个子节点(`/hbase/online-snapshot/acquired/<snapshot name>/m1.host`)，表示已经完成快照的准备。

### 1.2 /hbase/online-snapshot/reached
 一旦所有`region server`都已经加入`acquired`节点，`hbase master`会创建`reached`子节点`(/hbase/online-snapshot/reached/<snapshot name>)`，开始`snapshot`的commit过程。
 
 通用，每个`region server`在完成之后，在这个子节点下面增加一个子节点(`/hbase/online-snapshot/reached/<snapshot name>/m1.host`)，表示工作完成。

### 1.3 /hbase/online-snapshot/abort 
 在`acquired`或`reached`阶段，一旦有失败发生，将开始`abort`过程。`hbase master`会创建一个子节点`(/hbase/online-snapshot/abort/<snapshot name>)`。
 
 通过这个子节点，告知所有`region server`终止`snapshot`，并回滚资源。


## 2 Table vs Snapshot

### 2.1 Hbase Table的组成
一个HBase表，由元数据信息集合和key/value对集合组成。

（1）Table Info: 描述表配置信息的manifest文件，包括像： column families, compression and encoding codecs, bloom filter types等等.

（2）Regions: 表分区被称为regions. 每个分区负责处理一个连续的key／value集合，由一个start key和end key所定义区间范围。

（3）WALs/MemStore: 写disk之前，数据首先写入Write Ahead Log (WAL) ，然后存储在in-memory ，直到内存压力触发disk flush。WAL 提供了一个很容易恢复哪些因失败而未能flush到disk的数据的方式。

（4）HFiles: 在某个时间点，所有数据flush到disk。一个HFile是包含key／values内存的hbase文件格式。HFiles不可修改，但能够在compaction或者region deletion时，被删除。

### 2.2 Snapshot的组成
（1）快照是元数据信息的一个集合，允许管理员回滚到一个hbase 表的某种以前状态。

（2）快照不是一个表的拷贝。最简单的考虑这个问题的方式是，它是一个追踪元数据（table info 和 regions）和数据（HFiles，memstore，WALs）的集合。在快照的生成过程中，没有数据拷贝。

## 3 Snapshot 生成过程
![Snapshot](https://github.com/yilong2001/yl-hbase-rep/blob/master/img/snapshot_progress.png)

`HBase Master`收到`online快照`请求，会基于zookeeper、经过一个`两阶段提交`过程，最终完成快照。具体过程如下：


### 3.1 准备阶段
（1）`Master`在ZK创建一个节点：标识为`prepare the snapshot`；

（2）每个 `Region Server`接收到这个事件，会准备在该 RS上的相关Table的元数据信息，如果准备完成，`Region Server`会在`prepare the snapshot`节点下面增加一个子节点`I’m done`，表示该RS处理完成；

（3）所有RS都处理完成；

### 3.2 commit阶段
（1）Master收到所有RS处理快照请求完成的status，然后会重新创建一个 节点，标识为：`Commit snapshot`；

（2）每个RS完成快照元数据信息的处理、并且在该ZK节点下面增加子节点，表示其完成处理；

（3）Master收到所有RS完成处理的消息，生成快照文件，并且标志此次快照请求成功；

（4）完成快照；

### 3.3 回滚
（1）不管是`Phase I`还是 `Phase II`失败，`Master`都会创建一个新的ZK节点，表示生成快照失败；

（2）RS收到失败消息，做回滚处理；

## 4 Snapshot 存储和备份
![Archiving](https://github.com/yilong2001/yl-hbase-rep/blob/master/img/table_snapshot_data.png)
 `HFile`不可变，但是在`hbase compaction`期间，`HFile`会做`合并`，原有的`HFile`就会被`删除`。
 
 但是，如果某一个`HFile`属于一个快照，那么这个`HFile`就不会被删除，而是被移入`Archive`目录。
 
 如果快照被`删除`了，那么与这个快照关联的`HFile（移入Archive目录下的）`，也会被`删除`。


