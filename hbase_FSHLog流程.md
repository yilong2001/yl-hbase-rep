# 写 WAL ， LogRoll 的流程

## HRegion
1、当写入 WAL 的时候，会调用 FSHLog.append 
2、FSHLog.append 会调用 disruptor next 保存 WALEntry ，然后 publish 
3、此时， 即 publish 后， WALEntry 会进入 onEvent 处理

## LogRoller
1、定期调用 FSHLog.rollWrite 

## FSHLog

### append 处理流程：

1、FSHLog.append 会调用 disruptor next 保存 WALEntry ，然后 publish 
2、此时， 即 publish 后， WALEntry 会进入 onEvent 处理


### rollWrite 处理流程：

1、获取 Ring Buffer 的 next sequence
2、new SafePointZigZagLatch 
3、create SyncFuture （ 与 current sequence 绑定 ）， publish SyncFuture
4、此时，即 publish 后，  SyncFuture 进入 onEvent 处理
5、判断 SyncFuture 是否失败过，如果是，抛出异常
6、更新 writer ， hdfs_out
7、更新 old log file size
8、safePointReleasedLatch countDown
注：说明 onEvent 可以继续处理了
注：只要有一次 roll ， onEvent 就会有一次等待，等待 roll 完成，才能继续后续的事件处理（即：写入 WAL）
注：在非 roll 期间，由于 safePointReleasedLatch 已经 countDown ，
   而且 SafePointZigZagLatch 对象未发生变化，所以就不会再受 safePointReleasedLatch wait 的限制
9、如果 syncFuture 不为 null ， 此时会等待 syncFuture 完成， 在 walSyncTimeout 时间范围内
注：此时 SyncFuture 会在 onEvent 中进行处理，最终会在 SyncRunners 中被处理完成

### onEvent 处理流程：

1、如果有 SyncFuture ， 从 RingBufferTruck 中获取 SyncFuture
1.1、 将 SyncFuture 加入到 syncFutures 

2、否则，如果有 FSWALEntry ，则从 RingBufferTruck 中获取 FSWALEntry
2.1 获取 WAL Edit 的 SequenceId
注：此时 MultiVersionConcurrencyControl begin

2.2 调用 ProtobufLogWriter.append(entry) ;
2.3 update region new sequenceid as highest ： highestSyncedSequence

--> SyncRunner thread ：
2.3 highestSyncedSequence 在 SyncRunner.run 持续用于判断 ，直到 currentSequence 更新完成 
2.4 然后在 SyncRunner.run  ，调用 （ SyncFuture.done ） in releaseSyncFuture 

3、如果是 SyncFuture ，并且 SyncFutures num > 0
4、将 SyncFutures 放入 SyncRunners （线程池，运行 SyncFuture）
注：参考 2.3/2.4 ，实际上，在 SyncFuture 触发的时候，会等待在此之前的所有 FSWALEntry 处理完成，
即 CurrentSequence 更新为 highest

5、调用 attainSafePoint 
5.1 等待所有 SyncFutures 完成 ， 等待 SyncRunners 结束
5.2 调用 SafePointZigZagLatch.safePointAttained
5.2.1 safePointAttainedLatch countDown
5.2.2 safePointReleasedLatch wait 此时等待 SafePointRelease （等待 replaceWriter / rollWriter 完成）
即 ： replaceWriter 完成后会 safePointReleasedLatch countDown
意思是： 在 replaceWriter 期间，onEvent 会在结束函数之前等待直到 replaceWriter 完成









