# yl-hbase-rep
- hbase snapshot、replication 实现机制、源码解析
- replication peer 的实现代码
- bulkload code example

## 1 snapshot/replication 实现机制
- snapshot 实现机制、快照文件生成过程，请参考 snapshot 内容
- replication 实现机制，源码解析，请参考 replication 内容

## 2 replication peer 的实现
- 参考 lily 二级索引的实现机制，重新实现了一个简化版本的 replication peer
- replication peer server 从源 hbase 集群同步接收 WAL edits，并写入目标存储(例如：solr / kafka 等)
- 实际上 replication peer 类似于一个 region server 角色

## 3 bulkload example
- bulkload 方式从 hive import hbase 有现成的 sql 可以使用
- 如果输入是 CSV 文件 或 parquet 文件，当然也可以先建立一个 hive 外部表，再从hive import to hbase
- 也可以通过 coding 的方式，通过 bulkload 生成 hfile 然后再 merge 到 hbase

## 致谢
yl-hbase-rep 实现机制参考了 lily indexer 

