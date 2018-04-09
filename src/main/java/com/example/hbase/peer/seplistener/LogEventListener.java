package com.example.hbase.peer.seplistener;

import com.example.hbase.peer.sepapi.EventListener;
import com.example.hbase.peer.sepapi.RowData;
import com.example.hbase.peer.sepimpl.SepEvent;
import com.example.hbase.peer.sepimpl.SepEventRowData;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.Cell;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Created by yilong on 2017/8/24.
 */
public class LogEventListener implements EventListener{
    private final static Log log = LogFactory.getLog(LogEventListener.class);

    @Override
    public void processEvents(List<SepEvent> events) {
        List<RowData> rowDatas = Lists.transform(events, SepEventToRowDataFunction.INSTANCE);
        for (RowData rd : rowDatas) {
            log.info("*** LogEventListener(table) : "+new String(rd.getTable()));
            for (Cell cell : rd.getKeyValues()) {
                log.info(new String(cell.getQualifierArray()));
                log.info(new String(cell.getRowArray()));
            }
        }
    }

    private static class SepEventToRowDataFunction implements Function<SepEvent, RowData> {

        static final SepEventToRowDataFunction INSTANCE = new SepEventToRowDataFunction();

        @Override
        public RowData apply(@Nullable SepEvent input) {
            return new SepEventRowData(input);
        }

    }
}
