package com.example.hbase.peer.sepimpl;

/**
 * Created by yilong on 2017/8/23.
 */
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.hadoop.hbase.Cell;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains information about a single atomic mutation that has occurred on a row in HBase.
 */
public class SepEvent {

    private final byte[] table;
    private final byte[] row;
    private final List<Cell> keyValues;
    private final byte[] payload;

    /**
     * Single constructor.
     *
     * @param table The HBase table on which the event was triggered
     * @param row The row in the table where the event was triggered
     * @param keyValues The list of updates to the HBase row
     * @param payload Optional additional payload containing data about the data mutation(s)
     */
    public SepEvent(byte[] table, byte[] row, List<Cell> keyValues, byte[] payload) {
        this.table = table;
        this.row = row;
        this.payload = payload;
        this.keyValues = keyValues;
    }

    /**
     * Single constructor.
     *
     * @param table   The HBase table on which the event was triggered
     * @param row     The row in the table where the event was triggered
     * @param cells   The list of updates to the HBase row
     * @param payload Optional additional payload containing data about the data mutation(s)
     */
    public static SepEvent create(byte[] table, byte[] row, List<Cell> cells, byte[] payload) {
        List<Cell> keyValues = new ArrayList<Cell>(cells.size());
        for (Cell cell : cells) {
            keyValues.add(cell);
        }
        return new SepEvent(table, row, keyValues, payload);
    }

    /**
     * Retrieve the table where this event was triggered.
     *
     * @return name of the HBase table
     */
    public byte[] getTable() {
        return table;
    }

    /**
     * Retrieve the row key where this event was triggered.
     *
     * @return row key bytes
     */
    public byte[] getRow() {
        return row;
    }

    /**
     * Retrieve the payload bytes for this event. Can be null.
     *
     * @return payload bytes, or null if not set
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Retrieve all grouped KeyValues that are involved in this event.
     *
     * @return list of key values
     */
    public List<Cell> getKeyValues() {
        return keyValues;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        SepEvent rhs = (SepEvent)obj;
        return new EqualsBuilder().append(table, rhs.table).append(row, rhs.row).append(keyValues, rhs.keyValues).append(
                payload, rhs.payload).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(table).append(row).append(keyValues).append(payload).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append(table).append(row).append(keyValues).append(
                payload).toString();
    }

}
