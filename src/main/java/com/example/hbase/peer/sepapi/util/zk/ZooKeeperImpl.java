/*
 * Copyright 2012 NGDATA nv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.hbase.peer.sepapi.util.zk;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.*;


/**
 * Default implementation of {@link ZooKeeperItf}.
 *
 * <p>To wait until the ZK connection is established, use {@link ZkUtil#connect(String, int)}.
 *
 * <p>For a global ZK handle to be used by a ZK-dependent application, see rather
 * {@link org.lilyproject.util.zookeeper.StateWatchingZooKeeper}.
 */
public class ZooKeeperImpl implements ZooKeeperItf {
    protected ZooKeeper delegate;

    protected Set<Watcher> additionalDefaultWatchers = Collections.newSetFromMap(new IdentityHashMap<Watcher, Boolean>());

    protected boolean connected = false;

    protected volatile boolean stop = false;

    protected final Object connectedMonitor = new Object();

    protected Thread zkEventThread;

    private Log log = LogFactory.getLog(getClass());

    protected ZooKeeperImpl() {

    }

    protected void setDelegate(ZooKeeper delegate) {
        this.delegate = delegate;
    }

    public ZooKeeperImpl(String connectString, int sessionTimeout) throws IOException {
        this.delegate = new ZooKeeper(connectString, sessionTimeout, new MyWatcher());
    }

    @Override
    public void addDefaultWatcher(Watcher watcher) {
        additionalDefaultWatchers.add(watcher);
    }

    @Override
    public void removeDefaultWatcher(Watcher watcher) {
        additionalDefaultWatchers.remove(watcher);
    }

    public void shutdown() {
        this.stop = true;
        synchronized (connectedMonitor) {
            connectedMonitor.notifyAll();
        }
    }

    @Override
    public void waitForConnection() throws InterruptedException {
        if (isCurrentThreadEventThread()) {
            throw new RuntimeException("waitForConnection should not be called from within the ZooKeeper event thread.");
        }

        synchronized (connectedMonitor) {
            while (!connected && !stop) {
                connectedMonitor.wait();
            }
        }

        if (stop) {
            throw new InterruptedException("This ZooKeeper handle is shutting down.");
        }
    }

    @Override
    public boolean isCurrentThreadEventThread() {
        // Disclaimer: this way of detected wrong use of the event thread was inspired by the ZKClient library.
        return zkEventThread != null && zkEventThread == Thread.currentThread();
    }

    protected void setConnectedState(WatchedEvent event) {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            synchronized (connectedMonitor) {
                if (!connected) {
                    connected = true;
                    connectedMonitor.notifyAll();
                }
            }
        } else if (event.getState() == Watcher.Event.KeeperState.Disconnected ||
                event.getState() == Watcher.Event.KeeperState.Expired) {
            synchronized (connectedMonitor) {
                if (connected) {
                    connected = false;
                    connectedMonitor.notifyAll();
                }
            }
        }
    }

    @Override
    public <T> T retryOperation(ZooKeeperOperation<T> operation) throws InterruptedException, KeeperException {
        if (isCurrentThreadEventThread()) {
            throw new RuntimeException("retryOperation should not be called from within the ZooKeeper event thread.");
        }

        int tryCount = 0;

        while (true) {
            tryCount++;

            try {
                return operation.execute();
            } catch (KeeperException.ConnectionLossException e) {
                // ok
            }

            if (tryCount > 3) {
                log.warn("ZooKeeper operation attempt " + tryCount + " failed due to connection loss.");
            }

            waitForConnection();
        }
    }

    @Override
    public long getSessionId() {
        return delegate.getSessionId();
    }

    @Override
    public byte[] getSessionPasswd() {
        return delegate.getSessionPasswd();
    }

    @Override
    public int getSessionTimeout() {
        return delegate.getSessionTimeout();
    }

    @Override
    public void addAuthInfo(String scheme, byte[] auth) {
        delegate.addAuthInfo(scheme, auth);
    }

    @Override
    public void register(Watcher watcher) {
        delegate.register(watcher);
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException, InterruptedException {
        return delegate.create(path, data, acl, createMode);
    }

    @Override
    public void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, AsyncCallback.StringCallback cb, Object ctx) {
        delegate.create(path, data, acl, createMode, cb, ctx);
    }

    @Override
    public void delete(String path, int version) throws InterruptedException, KeeperException {
        delegate.delete(path, version);
    }

    @Override
    public void delete(String path, int version, AsyncCallback.VoidCallback cb, Object ctx) {
        delegate.delete(path, version, cb, ctx);
    }

    @Override
    public Stat exists(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return delegate.exists(path, watcher);
    }

    @Override
    public Stat exists(String path, boolean watch) throws KeeperException, InterruptedException {
        return delegate.exists(path, watch);
    }

    @Override
    public void exists(String path, Watcher watcher, AsyncCallback.StatCallback cb, Object ctx) {
        delegate.exists(path, watcher, cb, ctx);
    }

    @Override
    public void exists(String path, boolean watch, AsyncCallback.StatCallback cb, Object ctx) {
        delegate.exists(path, watch, cb, ctx);
    }

    @Override
    public byte[] getData(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        return delegate.getData(path, watcher, stat);
    }

    @Override
    public byte[] getData(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return delegate.getData(path, watch, stat);
    }

    @Override
    public void getData(String path, Watcher watcher, AsyncCallback.DataCallback cb, Object ctx) {
        delegate.getData(path, watcher, cb, ctx);
    }

    @Override
    public void getData(String path, boolean watch, AsyncCallback.DataCallback cb, Object ctx) {
        delegate.getData(path, watch, cb, ctx);
    }

    @Override
    public Stat setData(String path, byte[] data, int version) throws KeeperException, InterruptedException {
        return delegate.setData(path, data, version);
    }

    @Override
    public void setData(String path, byte[] data, int version, AsyncCallback.StatCallback cb, Object ctx) {
        delegate.setData(path, data, version, cb, ctx);
    }

    @Override
    public List<ACL> getACL(String path, Stat stat) throws KeeperException, InterruptedException {
        return delegate.getACL(path, stat);
    }

    @Override
    public void getACL(String path, Stat stat, AsyncCallback.ACLCallback cb, Object ctx) {
        delegate.getACL(path, stat, cb, ctx);
    }

    @Override
    public Stat setACL(String path, List<ACL> acl, int version) throws KeeperException, InterruptedException {
        return delegate.setACL(path, acl, version);
    }

    @Override
    public void setACL(String path, List<ACL> acl, int version, AsyncCallback.StatCallback cb, Object ctx) {
        delegate.setACL(path, acl, version, cb, ctx);
    }

    @Override
    public List<String> getChildren(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return delegate.getChildren(path, watcher);
    }

    @Override
    public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException {
        return delegate.getChildren(path, watch);
    }

    @Override
    public void getChildren(String path, Watcher watcher, AsyncCallback.ChildrenCallback cb, Object ctx) {
        delegate.getChildren(path, watcher, cb, ctx);
    }

    @Override
    public void getChildren(String path, boolean watch, AsyncCallback.ChildrenCallback cb, Object ctx) {
        delegate.getChildren(path, watch, cb, ctx);
    }

    @Override
    public List<String> getChildren(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        return delegate.getChildren(path, watcher, stat);
    }

    @Override
    public List<String> getChildren(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return delegate.getChildren(path, watch, stat);
    }

    @Override
    public void getChildren(String path, Watcher watcher, AsyncCallback.Children2Callback cb, Object ctx) {
        delegate.getChildren(path, watcher, cb, ctx);
    }

    @Override
    public void getChildren(String path, boolean watch, AsyncCallback.Children2Callback cb, Object ctx) {
        delegate.getChildren(path, watch, cb, ctx);
    }

    @Override
    public void sync(String path, AsyncCallback.VoidCallback cb, Object ctx) {
        delegate.sync(path, cb, ctx);
    }

    @Override
    public ZooKeeper.States getState() {
        return delegate.getState();
    }

    public class MyWatcher implements Watcher {
        private boolean printConnectMsg = false; // do not print connect msg on initial connect

        @Override
        public void process(WatchedEvent event) {
            zkEventThread = Thread.currentThread();

            if (event.getState() == Watcher.Event.KeeperState.Disconnected) {
                System.err.println("ZooKeeper disconnected at " + new Date());
                printConnectMsg = true;
            } else if (event.getState() == Event.KeeperState.Expired) {
                System.err.println("ZooKeeper session expired at " + new Date());
                printConnectMsg = true;
            } else if (event.getState() == Event.KeeperState.SyncConnected) {
                if (printConnectMsg) {
                    System.out.println("ZooKeeper connected at " + new Date());
                }
            }

            setConnectedState(event);

            for (Watcher watcher : additionalDefaultWatchers) {
                watcher.process(event);
            }
        }
    }

}
