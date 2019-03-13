# RegionServer

## 主要变量

- [1. regionsInTransitionInRS](#1-regionsInTransitionInRS)
- [2. cacheFlusher](#2-cacheFlusher)
- [3. replicationSourceHandler&replicationSinkHandler](#3-replication)
- [4. compactSplitThread::Compaction](#4-Compaction)
- [5. compactSplitThread::RegionSplit](#5-RegionSplit)
- [6. compactSplitThread::onlineRegions](#6-onlineRegions)
- [7. recoveringRegions](#7-recoveringRegions)
- [8. leases](#8-leases)
- [9. walRoller](#9-walRoller)
- [10. splitLogWorker](#10-splitLogWorker)
- [11. rsQuotaManager](#11-rsQuotaManager)
- [12. tableLockManager](#11-tableLockManager)
- [13. 锁机制]

## 使用说明

### regionsInTransitionInRS
- RegionServer 保存当前正在 Open or Close 状态的 Regions

- 如果当前正在 Open (以 True 表示), 如果正在 Close 则以 False 表示

- OpenRegion时(设置为 True) (RSRpcServices::openRegion)
```
  public OpenRegionResponse openRegion(final RpcController controller,
      final OpenRegionRequest request) throws ServiceException {
      ...... // 如果已经在 Close 则标示为一个异常状态
          if (regionServer.serverName.equals(p.getSecond())) {
            Boolean closing = regionServer.regionsInTransitionInRS.get(region.getEncodedNameAsBytes());
            if (!Boolean.FALSE.equals(closing)
                && regionServer.getFromOnlineRegions(region.getEncodedName()) != null) {
              builder.addOpeningState(RegionOpeningState.ALREADY_OPENED);
              continue;
            }
      ...... //再次尝试，如果此时在 Close 则抛出异常
        final Boolean previous = regionServer.regionsInTransitionInRS.putIfAbsent(
          region.getEncodedNameAsBytes(), Boolean.TRUE);      
          
      ...... // 然后控制权交给 OpenRegionHandler ，在 Open Success 后，从 Map 中删除此 Region
      regionServer.service.submit(new OpenRegionHandler(regionServer, regionServer, region, htd, masterSystemTime, coordination, ord));
          
  }
```

- CloseRegion时(设置为 False) (HRegionServer::closeRegion) 
```
  protected boolean closeRegion(String encodedName, final boolean abort,
      CloseRegionCoordination.CloseRegionDetails crd, final ServerName sn)
      throws NotServingRegionException, RegionAlreadyInTransitionException {
      ......
    final Boolean previous = this.regionsInTransitionInRS.putIfAbsent(encodedName.getBytes(),
        Boolean.FALSE);
        
      ...... // 然后控制权交给 CloseRegionHandler ，在 Close Success 后，从 Map 中删除此 Region    
    CloseRegionHandler crh;
    final HRegionInfo hri = actualRegion.getRegionInfo();
    if (hri.isMetaRegion()) {
      crh = new CloseMetaHandler(this, this, hri, abort,
        csm.getCloseRegionCoordination(), crd);
    } else {
      crh = new CloseRegionHandler(this, this, hri, abort,
        csm.getCloseRegionCoordination(), crd, sn);
    }
      ......
  }

```

### cacheFlusher
MemcacheFlush 线程(有多个)，在 Start RegionServer 时启动多个 Flush 线程。
- Flush 触发时机(1) (HeapMemoryManager)

 HeapMemoryTunerChore 内存调优调度任务，在 TuneOn and not alarm 时触发
(如果 memStoreSize 和 blockCacheSize 之和超过 CLUSTER_MINIMUM_MEMORY_THRESHOLD)

```
    private void tune() {
        ......
        int gml = (int) (memstoreSize * CONVERT_TO_PERCENTAGE);
        int bcul = (int) ((blockCacheSize + l2BlockCachePercent) * CONVERT_TO_PERCENTAGE);
        if (CONVERT_TO_PERCENTAGE - (gml + bcul) < CLUSTER_MINIMUM_MEMORY_THRESHOLD) {
          ......
        } else {
          long newBlockCacheSize = (long) (maxHeapSize * blockCacheSize);
          long newMemstoreSize = (long) (maxHeapSize * memstoreSize);
          blockCachePercent = blockCacheSize;
          blockCache.setMaxSize(newBlockCacheSize);
          globalMemStorePercent = memstoreSize;
          memStoreFlusher.setGlobalMemstoreLimit(newMemstoreSize);
        }
        ......
    }
```

- Flush 触发时机(2) (LogRoller)
如果 logroll.period 到期或有 Roll 请求，就会强制 Roll 。

```
  public void run() {
  ......
      rollLock.lock(); // FindBugs UL_UNRELEASED_LOCK_EXCEPTION_PATH
      try {
        this.lastrolltime = now;
        for (Entry<WAL, Boolean> entry : walNeedsRoll.entrySet()) {
          final WAL wal = entry.getKey();
          final byte [][] regionsToFlush = wal.rollWriter(periodic ||
              entry.getValue().booleanValue());
          walNeedsRoll.put(wal, Boolean.FALSE);
          if (regionsToFlush != null) {
            for (byte [] r: regionsToFlush) scheduleFlush(r);
          }
        }
  ......  
  }
  
  private void scheduleFlush(final byte [] encodedRegionName) {
    boolean scheduled = false;
    Region r = this.services.getFromOnlineRegions(Bytes.toString(encodedRegionName));
    FlushRequester requester = null;
    if (r != null) {
      requester = this.services.getFlushRequester();
      if (requester != null) {
        // force flushing all stores to clean old logs
        requester.requestFlush(r, true);
        scheduled = true;
      }
    }
    if (!scheduled) {
      LOG.warn("Failed to schedule flush of " +
        Bytes.toString(encodedRegionName) + ", region=" + r + ", requester=" +
        requester);
    }
  }  
  
```

- Flush 触发时机(3) (HRegion put\append\doIncre)

在所有写操作时，都会触发 Flush 。与 LogRoll 的区别在于，LogRoll 的 ForceFlag = True，而 HRegion 写操作时，ForceFlag=False 

- Flush 触发时机(4) HRegionServer PeriodicMemstoreFlusher

定时调度任务，如果判断 Region 需要 Flush，则触发一个延迟 Flush 动作 `requester.requestDelayedFlush(r, randomDelay, false);` 。

### replicationSourceHandler & replicationSinkHandler
用于 Replication ，参考 Replication 源码分析

### compactSplitThread :: Compaction
- HBase Compaction

MemStoreFlusher 每次从 flushQueue 中拿一个 flush region 请求，会检查这个region是否有某个store包含的storefile个数超过配置 hbase.hstore.blockingStoreFiles (默认7)。
如果超过，说明storefile个数已经到了会影响读性能的地步：
(1) 那么就看这个 flush region 请求是否已经有 blockingWaitTime（hbase.hstore.blockingWaitTime,默认90s）没有执行了;
(2) 如果是，这时候需要立即执行 flush region，为了防止OOM;
(3) 如果没有超过 blockingWaitTime，那么先看看 region 是否需要分裂;
(4) 如果不需要，则向后台的 CompactionSplitThread 请求做一次 Compaction (从这里可以看出，split优先级比compaction高);
(5)然后重新将这个 flush region 加入 flushQueue，延后做 flush

- Stripe Compaction
- - RegionFile 的问题

(1) 过多Region会增大RS维护的开销，降低RS的读写性能。随着数据量的增大，在一定程度上增加Region个数，会提高系统的吞吐率。然而，RS上服务的Region个数增多，增加了RS下内存维护的开销，尤其每个Store下都配置有一个MemStore，从而会造成频率更高的Flush操作，影响系统的读写性能。因此，如果能够提出更轻量级的mini-Region，不仅能够降低服务多个Region的开销，而且能够提升读写数据的效率。
(2) Region Compaction容易”放大”。例如，Region区间为[1FFF，2FFF）,在该区间内仅有[1FFF，21FF)区间有大量的写操作(put、delete)，但是,在触及MajorCompaction条件时，却需要对所有的文件执行Major Compaction，从而引起大量的IO。
(3) Region Split操作代价较大。

- - Stripe Compaction 设计

(1) 对于Region下的rowkey区间进行二次切分，例如[1FFF,2FFF)，切分成[1FFF,24FF),[24FF,2FFF)两个区间，每个区间成为Stripe。
(2) Region下的数据文件分为Level-0和Level-1两层。其中Level-0主要用来存储临时的数据文件(例如使用bulkload或者执行mem flush操作之后的数据)， Level-1层的数据是按照Stripe的分区来区分。
(3) 支持两种方式的配置：Mini-regions的个数设置、或者以Size-based为大小触发因子的自动切分机制。
(4) 容错机制。如果在Stripes之间存在空洞。那么可以根据在Store当中的设置，将所有的处于Level-1层的文件回归到Level-0重新进行compaction。
(5) Get操作时，一个Row所涉及到文件有：MemStore、Level-0下所有文件、以及Level-1下对应Stripe区下的文件。根据Stack的意见，最终Level-0下的文件只是一个暂时的状态，大部分文件都位于Level-1 Stripe下，因此，这样随机读时，需要涉及到的文件更聚集。
(6) Scan操作时，需要定位startrow即可。在扫描过程中，会按照Stripe的row区间的排序，依次进行。
(7) Compaction，是Level-0上升到Level-1的过程，同时，在Level-1层次的数据，也会进行相关的合并。
(8) 在Split操作时，定位Rowkey区间的中心点，可以根据Stripe记录的位置，进一步查找，因此，使用预置的Stripe会有利于Split操作的进行，可以实现多数HFile文件直接拷贝到子Region目录，从而加快了Split操作的效率。

- Large compaction 和 Small compaction ( ThreadPoolExecutor(longCompactions & shortCompactions) )
- Compaction 触发机制(1) : HRegionServer CompactionChecker 周期调度任务
```
    protected void chore() {
      for (Region r : this.instance.onlineRegions.values()) {
        if (r == null)
          continue;
        for (Store s : r.getStores()) {
          try {
            long multiplier = s.getCompactionCheckMultiplier();
            assert multiplier > 0;
            if (iteration % multiplier != 0) continue;
            if (s.needsCompaction()) {
              // Queue a compaction. Will recognize if major is needed.
              this.instance.compactSplitThread.requestSystemCompaction(r, s, getName() + " requests compaction");
            } else if (s.isMajorCompaction()) {
              if (majorCompactPriority == DEFAULT_PRIORITY || majorCompactPriority > ((HRegion)r).getCompactPriority()) {
                this.instance.compactSplitThread.requestCompaction(r, s, getName()
                    + " requests major compaction; use default priority", null);
              } else {
                this.instance.compactSplitThread.requestCompaction(r, s, getName()
                    + " requests major compaction; use configured priority",
                  this.majorCompactPriority, null, null);
              }
            }
          } catch (IOException e) {
            LOG.warn("Failed major compaction check on " + r, e);
          }
        }
      }
      iteration = (iteration == Long.MAX_VALUE) ? 0 : (iteration + 1);
    }
```

- - needsCompaction 判断是否需要执行 Compaction, 执行Impl的有两个对象: DefaultStoreEngine, StripeStoreEngine
- - - DefaultStoreEngine 用到了 RatioBasedCompactionPolicy 
```
  public boolean needsCompaction(final Collection<StoreFile> storeFiles,
      final List<StoreFile> filesCompacting) {
    int numCandidates = storeFiles.size() - filesCompacting.size();
    return numCandidates >= comConf.getMinFilesToCompact();
  }
```

- - - StripeStoreEngine
```
  public boolean needsCompactions(StripeInformationProvider si, List<StoreFile> filesCompacting) {
    // Approximation on whether we need compaction.
    return filesCompacting.isEmpty()
        && (StoreUtils.hasReferences(si.getStorefiles())
          || (si.getLevel0Files().size() >= this.config.getLevel0MinFiles())
          || needsSingleStripeCompaction(si));
  }
```
- - 区分 Large 与 Small Compaction
```
  public boolean throttleCompaction(long compactionSize) {
    return compactionSize > comConf.getThrottlePoint();
  }
```
由"hbase.regionserver.thread.compaction.throttle"参数决定。

默认是=2 * "hbase.hstore.blockingStoreFiles" *  "hbase.hregion.memstore.flush.size"

如果全部取默认值=2*10*128MB = 2.5GB

- Compaction 触发机制(2) : MemStoreFlusher::flushRegion

在 MemStoreFlush 时，首先判断是否需要 Split (如果不需要 Split，则判断是否需要 Compation；都不需要时才 Flush，否则延迟 Flush).

分两种情况：

(1) ForceFlush, 此时首先会进行 Flush 然后再依次做 Split 或 Compation; 

(2) 非 ForceFlush ，此时会首先判断 Split 或 Compation ，无论是否 Split 或 Compaction ，都会进入 Delayed Flush Request 队列

```
  private boolean flushRegion(final FlushRegionEntry fqe) {
    Region region = fqe.region;
    if (!region.getRegionInfo().isMetaRegion() &&
        isTooManyStoreFiles(region)) {
      if (fqe.isMaximumWait(this.blockingWaitTime)) {
        
      } else {
        // If this is first time we've been put off, then emit a log message.
        if (fqe.getRequeueCount() <= 0) {
          // Note: We don't impose blockingStoreFiles constraint on meta regions
          if (!this.server.compactSplitThread.requestSplit(region)) {
            try {
              this.server.compactSplitThread.requestSystemCompaction(region, Thread.currentThread().getName());
            } catch (IOException e) {
              
            }
          }
        }
    ......
  }      
  
  private boolean flushRegion(final Region region, final boolean emergencyFlush, boolean forceFlushAllStores) {
      long startTime = 0;
      synchronized (this.regionsInQueue) {
        FlushRegionEntry fqe = this.regionsInQueue.remove(region);
        // Use the start time of the FlushRegionEntry if available
        if (fqe != null) {
          startTime = fqe.createTime;
        }
        if (fqe != null && emergencyFlush) {
          flushQueue.remove(fqe);
       }
      }
      if (startTime == 0) {
        startTime = EnvironmentEdgeManager.currentTime();
      }
      lock.readLock().lock();
      try {
        notifyFlushRequest(region, emergencyFlush);
        FlushResult flushResult = region.flush(forceFlushAllStores);
        boolean shouldCompact = flushResult.isCompactionNeeded();
        // We just want to check the size
        boolean shouldSplit = ((HRegion)region).checkSplit() != null;
        if (shouldSplit) {
          this.server.compactSplitThread.requestSplit(region);
        } else if (shouldCompact) {
          server.compactSplitThread.requestSystemCompaction(region, Thread.currentThread().getName());
        }
        if (flushResult.isFlushSucceeded()) {
          long endTime = EnvironmentEdgeManager.currentTime();
          server.metricsRegionServer.updateFlushTime(endTime - startTime);
        }
        
    ......
  }
```

### compactSplitThread :: RegionSplit

Region自动切分是HBase具有扩展性的最重要因素之一。

- SPLIT_POLICY & MAX_FILESIZE

Split Policy 以 Table 为粒度设置:`SPLIT_POLICY => 'org.apache.hadoop.hbase.regionserver.ConstantSizeRegionSplitPolicy', MAX_FILESIZE => '5000000000'`

- 策略: SteppingSplitPolicy (默认 Split 策略)

这种切分策略的切分阈值，相比 IncreasingToUpperBoundRegionSplitPolicy 简单一些。
SteppingSplitPolicy 切分策略和待分裂 Table 在当前 Regionserver 上的 Region 个数有关系，如果 Region 个数等于1，切分阈值为 Flush size * 2，否则为MaxRegionFileSize。
这种切分策略对于大集群中的大表、小表会比 IncreasingToUpperBoundRegionSplitPolicy 更加友好。小表不会再产生大量的小region。

- Split 触发机制(1) : HRegionServer CompactionChecker 周期调度任务, 同 Compaction

- Compaction 触发机制(2) : MemStoreFlusher::flushRegion，同 Compaction

### onlineRegions

RegionServer 当前服务的 Region 映射表，Key 是 Region Name。

- 增加一个 online region 的时机(1) : OpenRegionHandler

这是在 RegionServer Starup 之后，正常打开 Region ，则增加到 Online Regions

- 增加一个 online region 的时机(2) : RegionMergeTransactionImpl

Region Merge Transaction 成功之后，正常打开 MergedRegion ，则增加到 Online Regions

有几种情况：1) 正常 Merge 成功；2) Merge 失败，回滚已经 Offline 的两个原 Region, 重新增加到 Online Regions ;

- 增加一个 online region 的时机(3) : SplitTransactionImpl

Region Split Transaction 成功之后，正常打开两个 Daughter Region ，增加到 Online Regions

有几种情况：1) 正常 Split 成功；2) Split 失败，回滚已经 Offline 的原 Parent Region, 重新增加到 Online Regions ;

- 删除一个 online region 的时机(1) : CloseRegionHandler
- 删除一个 online region 的时机(2) : RegionMergeTransactionImpl

开始一个 MergeTransaction 之前，关闭并且 Offline 待 Merged Region

```
    Map<byte[], List<StoreFile>> hstoreFilesOfRegionA = closeAndOfflineRegion(
        services, this.region_a, true, testing);
    Map<byte[], List<StoreFile>> hstoreFilesOfRegionB = closeAndOfflineRegion(
        services, this.region_b, false, testing);
```

- 删除一个 online region 的时机(3) : SplitTransactionImpl

开始一个 SplitTransaction 之前，首先从当前 RegionServer Remove 此 Parent Region

```
    if (!testing) {
      services.removeFromOnlineRegions(this.parent, null);
    }

    transition(SplitTransactionPhase.OFFLINED_PARENT);
```

### recoveringRegions

保存处于 Receovering 状态的 Region 集合, 恢复状态的 Region 也属于 Online Region。 恢复流程在 Recover 部分说明。

### leases

在客户端开始 Scan 时 ，RegionServer 会 add Scanner，同时基于此 Scanner create Leases.Lease，以确保 Scan 在确定时间内完成(或 Lease 超时).

超时时间由 "hbase.client.scanner.timeout.period"(默认=60秒) 和 "hbase.rpc.timeout"(默认=60秒) 中最小值 / 2 确定。

### walRoller

是一个独立线程，定期(由 "hbase.regionserver.logroll.period" 决定周期)或 HMaster 强制触发 log roll。

WAL写操作由 FSHLog 执行, 在执行 Roll 动作时，首先创建一个新的 Writer，然后通知 Listener Log Roll 发生。并触发 MemStore 的 Flush。
```
        for (Entry<WAL, Boolean> entry : walNeedsRoll.entrySet()) {
          final WAL wal = entry.getKey();
          // Force the roll if the logroll.period is elapsed or if a roll was requested.
          // The returned value is an array of actual region names.
          final byte [][] regionsToFlush = wal.rollWriter(periodic ||
              entry.getValue().booleanValue());
          walNeedsRoll.put(wal, Boolean.FALSE);
          if (regionsToFlush != null) {
            for (byte [] r: regionsToFlush) scheduleFlush(r);
          }
        }
```

因为 WAL 在一个 RegionServer 的多个 Region 之间是共享的, 所以在执行 Roll 动作时需要(rollWriterLock)。
```
      try {
        Path oldPath = getOldPath();
        Path newPath = getNewPath();
        // Any exception from here on is catastrophic, non-recoverable so we currently abort.
        Writer nextWriter = this.createWriterInstance(newPath);
        FSDataOutputStream nextHdfsOut = null;
        if (nextWriter instanceof ProtobufLogWriter) {
          nextHdfsOut = ((ProtobufLogWriter)nextWriter).getStream();
          // If a ProtobufLogWriter, go ahead and try and sync to force setup of pipeline.
          // If this fails, we just keep going.... it is an optimization, not the end of the world.
          preemptiveSync((ProtobufLogWriter)nextWriter);
        }
        tellListenersAboutPreLogRoll(oldPath, newPath);
        // NewPath could be equal to oldPath if replaceWriter fails.
        newPath = replaceWriter(oldPath, newPath, nextWriter, nextHdfsOut);
        tellListenersAboutPostLogRoll(oldPath, newPath);
        // Can we delete any of the old log files?
        if (getNumRolledLogFiles() > 0) {
          cleanOldLogs();
          regionsToFlush = findRegionsToForceFlush();
        }
```

FSHLog 使用了 LMAX Disrutpor RingBuffer (无锁高并发队列，关于 Disruptor 的具体细节请参考 StormFake项目) 存储 WAL 数据块。

为了能高并发支持 WAL 写日志 FSHLog 在使用 RingBuffer 时，利用了异步 Append 机制，可以同时有多个数据块在做 WAL 的 Disk Flush。
直到这些 WAL 都已落盘成功，WAL 的 SequenceID 才会变化到最新的哪个数据块。

- WAL Writer 与 WAL Roll 的同步

(1) WAL Writer (Roll 实际执行过程)

```
public byte [][] rollWriter(boolean force) throws FailedLogCloseException, IOException {
    rollWriterLock.lock();
    //获取 DrainBarrier 对象的 begionOp 操作(通过 DrainBarrier 同步对象，判断 FSHLog 是否处于 Cache Flush / Roll 或 ShutDown 阶段)
    //rollWriterLock 锁只保证本次 rollWriter 是安全的，但是无法保证 Cache Flushing / Rolling 整个异步过程的事务性
    //通过使用 DrainBarrier 则可以做到这一点
    
    if (!closeBarrier.beginOp()) { return regionsToFlush; }
    
    //创建 NextWritter 准备替换 CurrentWritter
    Writer nextWriter = this.createWriterInstance(newPath);
    
    //真正的 Roll 过程在 replaceWriter 完成
    newPath = replaceWriter(oldPath, newPath, nextWriter, nextHdfsOut);
    
    closeBarrier.endOp();
    rollWriterLock.unlock();
}

Path replaceWriter(final Path oldPath, final Path newPath, Writer nextWriter,
      final FSDataOutputStream nextHdfsOut) {
     //获取安全点 (safe point) 同步对象; Safe Point 的作用在于，保证当前 Roll 的过程中，没有新的 WAL 发生
     //如果有，则等待 Roll 完成
     通过 safePointAttainedLatch(await) 这个 CountDownLatch 对象来获取 Safe Point 
     //Roll(Thread A) 线程会 hold 在这儿，直到 WAL 写文件线程(Thread B)调用 safePointAttained 函数通知 A 线程可以继续下一步
     close and clean Old Wal Writer
     使用 new writer and HDFS Data Out Stream
     //释放安全点(safe point) 同步对象; 通知 (Thread B) 可以继续干活了
     releaseSafePoint
}

```

(2) WAL 的 append 过程

WAL append 过程很简单，获取 RingBuffer 的 next position，取得 RingBufferTruck 存储对象；
生成新的 FSWALEntry 放入这个 RingBufferTruck 对象中；
然后发布 event 通知 RingBuffer 的消费者

(3) RingBufferEventHandler (WAL append 事件的消费者)

如果 truck.hasSyncFuturePayload() 为 True，说明是由 Roll 触发，需要完成当前 WAL 批量写文件步骤。

如果 truck.hasFSWALEntryPayload() 为 True，说明是由 append 触发，此时需要:1)调用 coprocessor 的 preWALWrite; 2)Write Edit; 3)coprocessor postWALWrite;

接着继续进行 Batch 的处理过程。
等待所有 SyncFuture 完成(isOutstandingSyncs 函数)。
然后通过 safePointAttained 通知 Roll 线程继续干活了。

一组 WAL edit 就批量 flush 到磁盘，接着 LogRoll 完成 Roll 动作，然后 WAL edit 继续同样的处理过程。

### splitLogWorker

启动 Split 线程，具体流程在 SplitLogWorker 和 ZkSplitLogWorkerCoordination 中完成。

### rsQuotaManager

Region Server Quota Manager，负责提供 quota 信息的访问，同时完成 RegionServer 上 用户/表 的操作配额校验(put, get, scan)。

### tableLockManager

Region Table 表锁，在 Split Region 时候使用，确保在 Split 的时候，不会发生 Table Schema 的修改更新。

具体锁的实现，是由 ZKTableLockManager 基于 zookeeper 节点操作功能实现。
lock 使用的 zookeeper 节点: /hbase/table-lock/tableName (参数："zookeeper.znode.tableLock" 配置 znode 名称)。

### 锁机制

HRegion有两种锁：lock、updatesLock，这两种锁均是ReentrantReadWriteLock类的实例，
基本上所有的region操作均需要获取lock的read共享锁，在获取了lock的read锁后，
如果是增加或者删除等影响数据内容的操作则还需要获取updatesLock的read锁。

#### lock
关闭region的doClose方法需要持有lock的write锁，
startBulkRegionOperation在进行跨列簇处理时也要求持有lock的write锁，其它均只需持有lock的read锁

startBulkRegionOperation是在使用工具LoadIncrementalHFiles装载通过HFileOutputFormat输出的HFile文件，
到一个已经存在的表时执行的方法，因此执行该操作最好是在该region空闲时执行

#### updatesLock
其中只有flush时执行的internalFlushcache方法需要持有updatesLock的write锁，
由此可见，flush方法会阻塞所有的可能改动memstore内容的操作。

put等需要改动memstore内容的操作均需要持有read和updatesLock read锁。

### memstore flush lock
snapshot和clearSnapshot方法要求持有lock的write锁。

这两个方法均是在flush memstore期间调用，这两个flush的子过程中会产生的write排它锁会影响到对memstore的所有读写操作，
而从hbase读取数据中的第一步就是读取memstore，可见flush memstore是一个比较重的过程，影响读写。

一个flush操作至少会flush整个region，在flush期间，整个region的服务性能均会下降，
因此有合适的flush次数和region的大小对性能提升会有所帮助。

- Compact 命令

- Merge 命令


