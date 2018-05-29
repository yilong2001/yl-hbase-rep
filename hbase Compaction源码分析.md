# Compaction

为什么要做Compaction?

HBase 做为一种LSM 存储引擎，利用了 HDFS 文件只能 append 不能 update/modify 的特性，也就是说只有写文件，不会更新或修改文件内容。

这样写效率很高，但是读效率就会差一些，因为对于同一个 key 的数据可能保存在多个文件(HFile)中，在查询的时候，需要从多个(HFile)中查询。
实际上, HBase 首先从 MemStore 查询、其次查 Cached Block、然后才会查 Region 下的多个 HFile 文件。

为减少 Region 下面过多 HFile 导致的性能下降，hbase 会定期或在 HFile 文件数量比较多的时候自动执行 HFile 的合并。

然而，合并 Compaction 也会对查询性能带来负面影响。
(1)首先是对文件IO带来的压力，因为需要生成新的 HFile；
(2)其次会带来网络压力，在HDFS上读写文件(包括3副本)，必然存在节点之间的传输，所以在数据量很大的情况下(Large Compatcion)，带来的网络压力也很大。

这就是需要设计不同 Compaction 策略的原因，以适应不同的场景。

Compaction 相关的参数：

- hbase.hregion.majorcompaction : default 1000*60*60*24*7
- hbase.hregion.majorcompaction.jitter : default 0.50F
- hbase.hstore.min.locality.to.skip.major.compact : default 0F

- hbase.hstore.compaction.throughput.higher.bound : default 20M
- hbase.hstore.compaction.throughput.lower.bound : default 10M

- hbase.server.thread.wakefrequency : default 10*1000
- hbase.hregion.memstore.flush.size : default 128M
- hbase.hstore.blockingStoreFiles ：默认=7，超过这个数量会触发 Compaction
- hbase.regionserver.thread.compaction.throttle ：默认=2 * hbase.hstore.blockingStoreFiles * hbase.hregion.memstore.flush.size，决定是否执行 Large or Small Compation

## FIFOCompactionPolicy

对于有明显冷热数据的场景，已经过期的数据就不再会使用，那么定期的清理策略就比较适合。

FIFOCompactionPolicy 就是这种策略，副作用比较小，不会实际地合并文件(写文件)，只是清理过期的 HFile，前提是数据必须有设置 TTL 。

```
  public boolean needsCompaction(Collection<StoreFile> storeFiles, 
      List<StoreFile> filesCompacting) {  
    boolean isAfterSplit = StoreUtils.hasReferences(storeFiles);
    if(isAfterSplit){
      LOG.info("Split detected, delegate to the parent policy.");
      return super.needsCompaction(storeFiles, filesCompacting);
    }
    return hasExpiredStores(storeFiles);
  }

  private  boolean hasExpiredStores(Collection<StoreFile> files) {
    long currentTime = EnvironmentEdgeManager.currentTime();
    for(StoreFile sf: files){
      // Check MIN_VERSIONS is in HStore removeUnneededFiles
      Long maxTs = sf.getReader().getMaxTimestamp();
      long maxTtl = storeConfigInfo.getStoreFileTtl();
      //通过 TTL 判断是否要清理整个 HFile 文件
      if(maxTs == null || maxTtl == Long.MAX_VALUE || (currentTime - maxTtl < maxTs)){
        continue; 
      } else{
        return true;
      }
    }
    return false;
  }

```

## RatioBasedCompactionPolicy

默认策略。其思想是按照时间顺序(old->new)，选择符合大小条件的 HFile 进行合并。其选择算法是：

f[start].size <= ratio * (f[start+1].size +.......+ f[end-1].size)

其中，会排除 bulk hfile ，然后再使用算法选择。

```
    if (!isTryingMajor && !isAfterSplit) {
      // We're are not compacting all files, let's see what files are applicable
      candidateSelection = filterBulk(candidateSelection);
      candidateSelection = applyCompactionPolicy(candidateSelection, mayUseOffPeak, mayBeStuck);
      candidateSelection = checkMinFilesCriteria(candidateSelection);
    }
```

## Stripe Compaction

Compaction在合并文件的过程中，会产生比较大的 Disk IO 和 Network IO 压力。
为减少 Compaction 的压力，参考 level db 的设计思想，在 Hbase 实现了 Strip Compaction。

一个 Region 可以分成多个 Strip，Strip 之间的 RowKey 是互相独立的，可以理解为 SubRegion，也就是 RowKey 范围更小的 Region。

同时， MemFlush 的 hfile 被称为 L0 hfile，与正常的 HFile 没有区别。
区别在于， L0 HFile 在 compaction (major or minor) 是，会合并到 Strip 中。
由于 Strip Rowkey 范围比较小，所以 strip compaction 的时间也会小很多。

## Compaction 限速

配置参数：

“hbase.hstore.compaction.throughput.higher.bound”(默认20MB/sec)

“hbase.hstore.compaction.throughput.lower.bound”(默认10MB/sec) 

为了降低 Compaction 期间对系统的影响，可以使用 Compaction 限速配置。
```
  protected boolean performCompaction(FileDetails fd, InternalScanner scanner, CellSink writer,
      long smallestReadPoint, boolean cleanSeqId,
      CompactionThroughputController throughputController, boolean major) throws IOException {
  
  throughputController.start(compactionName);
  ...
  throughputController.control(compactionName, len);
  ...
  throughputController.finish(compactionName);
  }
```

## NORMALIZATION_ENABLED


## CompactionChecker

定期调度任务，定时间隔由 hbase.server.thread.wakefrequency 确定。

- 根据 CompactionPolicy 判断是否需要 Compaction()
```
  public boolean FIFOCompactionPolicy::needsCompaction(Collection<StoreFile> storeFiles, 
      List<StoreFile> filesCompacting) {  
    boolean isAfterSplit = StoreUtils.hasReferences(storeFiles);
    if(isAfterSplit){
      LOG.info("Split detected, delegate to the parent policy.");
      return super.needsCompaction(storeFiles, filesCompacting);
    }
    return hasExpiredStores(storeFiles);
  }
  
  public boolean RatioBasedCompactionPolicy::needsCompaction(final Collection<StoreFile> storeFiles,
        final List<StoreFile> filesCompacting) {
      int numCandidates = storeFiles.size() - filesCompacting.size();
      return numCandidates >= comConf.getMinFilesToCompact();
  }
```

- 如果需要Compaction， 通过 CompactSplitThread 执行系统级 Compaction (requestSystemCompaction)
- 否则，判断是否 isMajorCompaction 
```
RatioBasedCompactionPolicy 的判断规则：
    距上一次 MajorCompaction 的时间间隔是否达到某个值，这个值(时间间隔)由下面公式得到：
    
    Math.round(hbase.hregion.majorcompaction * hbase.hregion.majorcompaction.jitter) 
      - Math.round(2L * hbase.hregion.majorcompaction.jitter * random(filesToCompact))
      + hbase.hregion.majorcompaction
    
```
- 如果是，则执行 Compaction (Large or Small Compaction 还需要再判断)
```
RatioBasedCompactionPolicy 判断规则：
 Compaction Priority >= majorCompactPriority 
 && CompactionFileSize >= hbase.regionserver.thread.compaction.throttle

```
- 否则，do nothing

## CompactSplitThread

ThreadPoolExecutor: longCompactions, shortCompactions, Split, mergePool

根据 CompactionRequest 类型选择 longCompactions or shortCompactions ThreadPool
```
    CompactionContext compaction = null;
    if (selectNow) {
      compaction = selectCompaction(r, s, priority, request, user);
      if (compaction == null) return null; // message logged inside
    }

    // We assume that most compactions are small. So, put system compactions into small
    // pool; we will do selection there, and move to large pool if necessary.
    ThreadPoolExecutor pool = (selectNow && s.throttleCompaction(compaction.getRequest().getSize()))
      ? longCompactions : shortCompactions;
    pool.execute(new CompactionRunner(s, r, compaction, pool, user));
```

## Compaction 过程
### HStore::requestCompaction
- 判断是否写 enable(如果否，就返回了)
- 移除过期文件(删除文件，更新 HStore 的 HFile 列表)
- Create CompactionContext
- coprocessor 优先选择需要 Compaction 的 HFiles
- 根据 CompactionPolicy(default: RatioBasedCompactionPolicy) 选择 HFile list
- build CompactionRequest 并生成 候选 HFile list
- build CompactionRunner 对象，并进入 ThreadPoll 调度

### CompactionRunner
- 首先判断 Table 是否开启 Compaction
- [ doCompaction ]
- 如果没有选择 CompactionFiles，再次调用 HStore::requestCompaction 创建 CompactionContext ; 
如果 store 的 Compaction 优先级比 当前优先级低，则将 store 的Compaction任务加入 parent thread pool;
如果 store 的 Compaction 是 longCompactions，则取消其他 Compaction ，只保留一个 Large Compaction;
- [ HRegion::Compact ]
- 如果 Compaction 成功完成，判断要不要继续进行Compaction 或者 需要进行 Split

### HRegion::Compact
- 申请 readLock (Compaction期间不能写)
- writestate compacting 计数器+1
- 执行 HStore.compact
- writestate compacting 计数器-1，如果<=0，通知等待 所有 compaction 完成的线程(etc, close region thread)
- 释放 readLock

### HStore::compact
- 判断所有待 Compaction 的 HFile 是存在当前 HStore 的(不会因为其他Compaction 或 Split 而删除)
- 执行 DefaultCompactor::compact

### DefaultCompactor::compact
- create StoreFile.Writer for new HFile
- create InternalScanner
- scan and append to new HFile
- throughput control and record progress
- appendMetadata maxSeqId to new HFile
- replace store files 
- completeCompaction


