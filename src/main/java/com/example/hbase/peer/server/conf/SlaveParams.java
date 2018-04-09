package com.example.hbase.peer.server.conf;

/**
 * Created by yilong on 2017/8/24.
 */
public class SlaveParams {
    final String subscriptionId;
    final long subscriptionTimestamp;

    public SlaveParams(String subscriptionId, long subscriptionTimestamp) {
        this.subscriptionId = subscriptionId;
        this.subscriptionTimestamp = subscriptionTimestamp;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public long getSubscriptionTimestamp() {
        return subscriptionTimestamp;
    }
}
