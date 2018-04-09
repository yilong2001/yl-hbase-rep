package com.example.hbase.peer.sepapi;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;

import java.util.List;

/**
 * Created by yilong on 2017/8/24.
 */
public interface RowData {
    byte[] getRow();

    byte[] getTable();

    List<Cell> getKeyValues();

    Result toResult();
}
