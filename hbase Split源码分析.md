# Split

## 预分区
在创建一张HBase表的时候，如果已有历史数据需要加载、或者对数据的规模、对 RowKey 的分布比较了解，就可以在创建表的时候预分区。

通过预分区，可以把 RowKey 分布在不同的区间范围内。

如果预分区的区间范围，与 RowKey 实际的分布情况不一致，就可能产生热点 Region 。

好在，除了预分区，hbase 还支持 auto split。可以自动对热点 Region 进行 Split。

## NORMALIZATION_ENABLED
hbase预分区并加载数据之后，有的分区数据多，有的数据很少，可能就需要重新划分分区。
在以前，解决办法只有重新创建建表，然后重新导数据。在数据量已经非常大的时候，这非常麻烦。
hbase为了解决这个问题，增加了Normalizer这个功能。

Region Normalizer使用表的所有region大致相同大小。
它通过找到一个粗略的平均值来做到这一点。大于这个平均值 [size] 的两倍的region将会被分割。
更小的region将会合并到相邻的region。

## disable split
```
alter 'myTable', {METADATA => {'SPLIT_POLICY' => 'org.apache.hadoop.hbase.regionserver.DisabledRegionSplitPolicy'}}

alter 'myTable', {normalization_enabled => 'false'}
```

## auto split

### Split Policy
在 create table 时可以指定 SPLIT_POLICY 和 HFile 的 MAX_FILESIZE:

`SPLIT_POLICY => 'org.apache.hadoop.hbase.regionserver.ConstantSizeRegionSplitPolicy', MAX_FILESIZE => '5000000000'`

- IncreasingToUpperBoundRegionSplitPolicy

算法：regioncount^3 * hbase.hregion.memstore.flush.size(default : 128M) * 2

如果使用默认配置，那么 Split 的结果为：

第一次 split：1^3 * 256 = 256MB 

第二次 split：2^3 * 256 = 2048MB 

第三次 split：3^3 * 256 = 6912MB 

第四次 split：4^3 * 256 = 16384MB > 10GB，因此取较小的值10GB 

后面每次split的size都是10GB了

- ConstantSizeRegionSplitPolicy

算法：当 region size 达到 hbase.hregion.max.filesize（default : 10G）大小后进行split

- DisabledRegionSplitPolicy

算法：禁止 auto split

- KeyPrefixRegionSplitPolicy

算法：根据rowKey的前缀对数据进行分组，这里是指定rowKey的前多少位作为前缀，
比如rowKey都是16位的，指定前5位是前缀，那么前5位相同的rowKey在进行region split的时候会分到相同的region中。

- DelimitedKeyPrefixRegionSplitPolicy

保证相同前缀的数据在同一个region中，例如rowKey的格式为：userid_eventtype_eventid，
指定的delimiter为 _ ，则split的的时候会确保userid相同的数据在同一个region中。

### 相关参数

- hbase.hstore.bulkload.verify (default = false)
- hbase.server.compactchecker.interval.multiplier (default = 1000)
- hbase.hstore.blockingStoreFiles (default = 7)
- hbase.hstore.blockingWaitTime (default = 90000)
- hbase.hregion.memstore.flush.size (default = 128M)
- hbase.hregion.max.filesize (default = 10G)
- hbase.regionserver.regionSplitLimit (default = 1000)

## force split

可以使用命令对 hbase table 强制执行 split

`split 'forced_table', 'b'`

## split 过程
- [ MemStoreFlusher::flushRegion ]
- FlushHandler中， 获取到 FlushRegionEntry 后执行 flushRegion
- 如果不是 MetaRegion ，并且 Region 有 TooManyStoreFiles ，则继续判断、是否执行 Split 和 Compaction
- 如果 FlushRegionEntry 已经等待了 hbase.hstore.blockingWaitTime 时间没有执行， 
则说明 Compaction 还在继续执行(因为 TooManyStoreFiles 仍然为 True)，此时会强制 Flush
- 如果 FlushRegionEntry 是第一次执行(不是 requeue 后再次执行)，
 RegionServer 的 Regions 数不超过最大值，并且 Compaction 优先级比较低， 调用 HRegion::checkSplit Split 获取 midKey
- [ HRegion::checkSplit ]
- 如果 isMetaTable 不能 Split
- 如果 isRecovering 不能 Split
- 如果 SplitPolicy::shouldSplit 返回 False ，不执行 Split
- 返回 SplitPolicy::getSplitPoint
- [ CompactSplitThread::requestSplit ]
- 使用 splits thread pool 调度 SplitRequest
- [ SplitRequest::doSplitting ]
- [ SplitTransactionImpl::prepare ]
- [ SplitTransactionImpl::execute]
- createDaughters
- - 设置当前阶段为: BEFORE_PRE_SPLIT_HOOK
- - 调用 CoProsssor 执行 preSplit
- - 设置当前阶段为: AFTER_PRE_SPLIT_HOOK
- - ZKSplitTransactionCoordination::startSplitTransaction
- - 在 PENDING_SPLIT 创建临时节点
- - [ HMaster监听zk节点， 触发执行 AssignmantManager::handleRegionSplitting ]
- - [ 处理 EventType.RS_ZK_REQUEST_REGION_SPLIT]
- - [ 把 PENDING_SPLIT 节点转换为 splitting 节点 ]
- - 设置当前阶段为: SET_SPLITTING
- - ZKSplitTransactionCoordination::waitForSplitTransaction，等待(PENDING_SPLIT 节点转换为 splitting 节点)
- - HRegionFileSystem::createSplitsDir
- - 设置当前阶段为: CREATE_SPLIT_DIR
- - Close Parent HRegion
- - 设置当前阶段为: OFFLINED_PARENT
- - 在 parent splits dir 创建子目录: daughter region dirs
- - 设置当前阶段为: STARTED_REGION_A_CREATION
- - createDaughterRegionFromSplits, 创建 Region A
- - 设置当前阶段为: STARTED_REGION_B_CREATION
- - createDaughterRegionFromSplits, 创建 Region B
- - [ 设置当前阶段为: PONR , 到了这个阶段，如果这个阶段失败，将不能再回滚，一旦失败发生，需要 shutdown RegionServer ]
- - [ 修改 MetaHTable (使用 Put): 设置 Parent offline/split ]
- - [ 三个Put : PutParent, PutA(location), PutB(location)，执行批量原子操作 ]
- - [ RPC调用: MultiRowMutationEndpoint::mutateRows ]
- - [ 打开子Region :: openDaughters ]
- - [ 增加 Region a & b 到 Online Regions ]
- - ZKSplitTransactionCoordination::completeSplitTransaction
- - [ 将节点状态从 SPLITTING 转变成 SPLIT ]
- - [ HMaster监听zk节点，触发执行: 设置 region a&b offline, remove parent region ]
- - [ HMaster监听zk节点，触发执行: Split Replica Region， 删除 zk 上的 split 节点 ]
- - ZKSplitTransactionCoordination::completeSplitTransaction，等待(HMaster 删除 Split 节点)
- - 设置当前阶段为: BEFORE_POST_SPLIT_HOOK
- - 调用 CoProsssor 执行 postSplit
- - 设置当前阶段为: AFTER_POST_SPLIT_HOOK
- - 设置当前阶段为: COMPLETED




