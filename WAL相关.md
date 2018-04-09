# WAL相关

## 1 WAL Rolling 对 Replication 的影响
当增加一个新的peer集群，新增peer集群仅仅从源cluster接收新的writes，WAL 的 roll时间早于新增peer集群时间的，将不会被Replication。

下图说明了增加和移除peer clusters的过程，这个过程中WAL rolling随机发生。

跟着时间线，注意 peer clusters 接收的 writes。如果 Writes 发生在 WAL roll 之前，不会反向追溯已经 rolled 的 WAL。

[pic]

## 2 WAL Splitting
一个Region Server有许多Regions。在一个Region Server上的Regions共享相同的Active WAL文件。

WAL文件中的每一个edit都有关于这个edit属于哪一个region的信息。当一个Region is opened，需要replay在WAL文件里的、属于这个regionedits。

因而，在WAL文件里的edits必须按region分组，以便于特定的sets能被重放以产生特定region的数据。

这个分组的过程，就是log splitting。如果一个region server失败，log splitting是恢复数据的一个关键过程。

当集群start的时候，Log splitting 由 HMaster 完成。当一个 Region Server shut down 的时候，由 ServerShutdownHandler 完成 splitting 。

因为要保证一致性，所以被影响的 region 将不可用，直到数据被恢复。

在这些regions变成可用之前，需要恢复和重放所有的WAL文件。因此，log splitting 影响到的 regions 是不可用的，直到这个过程完成。

## 3 WAL文件名
一个 Region Server 下面的所有 WAL 文件存储在一个公共的路径下，例如：

/hbase/.logs/srv.example.com,60020,1254173957298

一个 WAL 文件名的形式可能如下：

/hbase/.logs/srv.example.com,60020,1254173957298/srv.example.com,60020,1254173957298.1254173957495


