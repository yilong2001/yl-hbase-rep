package com.example.hbase.peer.sepapi;

/**
 * Created by yilong on 2017/8/23.
 */
import com.example.hbase.peer.sepimpl.SepEvent;

import java.util.List;

/**
 * Handles incoming Side-Effect Processor messages.
 */
public interface EventListener {

    /**
     * Process a list of events that have been delivered via the Side-Effect Processor (SEP).
     * <p>
     * If an exception is thrown while processing a batch of messages, all messages in the batch will be retried later
     * by the SEP. For this reason, message handling should be idempotent.
     *
     * @param events contains events representing the HBase update
     */
    void processEvents(List<SepEvent> events);
}
