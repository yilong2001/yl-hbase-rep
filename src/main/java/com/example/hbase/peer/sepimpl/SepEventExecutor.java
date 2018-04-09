package com.example.hbase.peer.sepimpl;

import com.example.hbase.peer.sepapi.EventListener;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by yilong on 2017/8/28.
 */
public class SepEventExecutor {
    private Log log = LogFactory.getLog(getClass());
    private EventListener eventListener;
    private List<ThreadPoolExecutor> executors;
    private Multimap<Integer, SepEvent> eventBuffers;
    private List<Future<?>> futures;
    private HashFunction hashFunction = Hashing.murmur3_32();
    private boolean stopped = false;
    private int poolSize;
    private int batchSize;

    public SepEventExecutor(EventListener eventListener,
                            List<ThreadPoolExecutor> executors,
                            int batchSize) {
        this.eventListener = eventListener;
        this.executors = executors;
        this.poolSize = executors.size();
        this.batchSize = batchSize;
        eventBuffers = ArrayListMultimap.create(poolSize, batchSize);
        futures = Lists.newArrayList();
    }

    public void addSepEvent(SepEvent sepEvent) {
        if (stopped) {
            throw new IllegalStateException("This executor is stopped");
        }

        // We don't want messages of the same row to be processed concurrently, therefore choose
        // a thread based on the hash of the row key
        int partition = (hashFunction.hashBytes(sepEvent.getRow()).asInt() & Integer.MAX_VALUE) % poolSize;
        List<SepEvent> eventBuffer = (List<SepEvent>)eventBuffers.get(partition);
        eventBuffer.add(sepEvent);

        if (eventBuffer.size() == batchSize) {
            schedule(partition, eventBuffer);
            eventBuffers.removeAll(partition);
        }
    }

    private void schedule(int partition, final List<SepEvent> events) {
        Future<?> future = executors.get(partition).submit(new Runnable() {
            @Override
            public void run() {
                try {
                    long before = System.currentTimeMillis();
                    log.debug("Delivering message to listener");
                    eventListener.processEvents(events);
                    log.debug("process events duration : "+(System.currentTimeMillis() - before));
                } catch (RuntimeException e) {
                    log.error("Error while processing event", e);
                    throw e;
                }
            }
        });
        futures.add(future);
    }

    private void scheduleAll() {
        for (int i=0; i<poolSize; i++) {
            List<SepEvent> eventBuffer = (List<SepEvent>)eventBuffers.get(i);
            if (eventBuffer.size() == 0) {
                continue;
            }

            schedule(i, eventBuffer);
        }
        eventBuffers.clear();
    }

    public List<Future<?>> flush() {
        scheduleAll();
        return Lists.newArrayList(futures);
    }
}
