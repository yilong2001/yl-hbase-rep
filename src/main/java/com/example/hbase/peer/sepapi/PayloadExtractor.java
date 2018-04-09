package com.example.hbase.peer.sepapi;

import org.apache.hadoop.hbase.KeyValue;

/**
 * Created by yilong on 2017/8/23.
 */
public interface PayloadExtractor {

    /**
     * Extract the payload data from a KeyValue.
     * <p>
     * Data should only be extracted if it matches the configured table, column family, and column qualifiers. If no
     * payload data can be extracted, null should be returned.
     *
     * @param tableName table to which the {@code KeyValue} is being applied
     * @param keyValue contains a (partial) row mutation which may include payload data
     * @return the extracted payload data, or null if no payload data is included in the supplied {@code KeyValue}
     */
    public byte[] extractPayload(byte[] tableName, KeyValue keyValue);

}
