# hbase集群启用Replication

## 1 配置peer

```
add_peer 'A', "server.name.com,server1.name.com,server2.name.com:2181:/hbaseslave"
set_peer_tableCFs 'A', 'table:cf'
list_peers
```

## 2 启用Replication

```
disable 'hbase1'
alter 'hbase1', {NAME => 'family_name', REPLICATION_SCOPE => '1'}
enable 'hbase1'
```

## 3 在Slave集群创建同名表
 如果slave是自定义实现的 peer，则不需要。

```
create 'hbase1','family_name'
```

## 4 replication实例（from RS日志）

```
regionserver.Replication: Normal source for cluster slave1: Total replicated edits: 1, current progress: 
walGroup [localhost%2C16201%2C1503624906608.default]: currently replicating from: hdfs://localhost:9000/hbase/WALs/localhost,16201,1503624906608/localhost%2C16201%2C1503624906608.default.1503624909552 at position: 3436
```



