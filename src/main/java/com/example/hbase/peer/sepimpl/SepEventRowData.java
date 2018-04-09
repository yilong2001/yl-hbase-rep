package com.example.hbase.peer.sepimpl;

import com.example.hbase.peer.sepapi.RowData;
import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;

import java.util.Collections;
import java.util.List;

/**
 * Created by yilong on 2017/8/24.
 */
public class SepEventRowData implements RowData {
    private final SepEvent sepEvent;
    public SepEventRowData(SepEvent event) {
        this.sepEvent = event;
    }

    @Override
    public byte[] getRow() {
        return sepEvent.getRow();
    }

    @Override
    public byte[] getTable() {
        return sepEvent.getTable();
    }

    @Override
    public List<Cell> getKeyValues() {
        return sepEvent.getKeyValues();
    }

    /**
     * Makes a HBase Result object based on the KeyValue's from the SEP event. Usually, this will only be used in
     * situations where only new data is written (or updates are complete row updates), so we don't expect any
     * delete-type key-values, but just to be sure we filter them out.
     */
    @Override
    public Result toResult() {

        List<Cell> filteredKeyValues = Lists.newArrayListWithCapacity(sepEvent.getKeyValues().size());

        for (Cell kv : getKeyValues()) {
            if (!CellUtil.isDelete(kv) && !CellUtil.isDeleteFamily(kv)) {
                filteredKeyValues.add(kv);
            }
        }

        // A Result object requires that the KeyValues are sorted (e.g., it does binary search on them)
        Collections.sort(filteredKeyValues, KeyValue.COMPARATOR);
        return Result.create(filteredKeyValues);
    }
}
