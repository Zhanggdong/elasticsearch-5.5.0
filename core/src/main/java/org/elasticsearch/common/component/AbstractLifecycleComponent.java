/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.component;

import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
public abstract class AbstractLifecycleComponent extends AbstractComponent implements LifecycleComponent {

    protected final Lifecycle lifecycle = new Lifecycle();

    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    protected AbstractLifecycleComponent(Settings settings) {
        super(settings);
    }

    protected AbstractLifecycleComponent(Settings settings, Class customClass) {
        super(settings, customClass);
    }

    @Override
    public Lifecycle.State lifecycleState() {
        return this.lifecycle.state();
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void start() {
        // ES的生命周期zhuangt:INITIALIZED -&gt; STARTED, STOPPED, CLOSED
        // 如果不可以启动，直接返回
        if (!lifecycle.canMoveToStarted()) {
            return;
        }
        // 启动之前循环监听
        for (LifecycleListener listener : listeners) {
            listener.beforeStart();
        }
        // 调用ZenDiscovery的doStart()方法对一些变量进行初始化工作
        doStart();
        // 处理状态
        lifecycle.moveToStarted();
        // 启动之后的监听
        for (LifecycleListener listener : listeners) {
            listener.afterStart();
        }
    }

    protected abstract void doStart();

    @SuppressWarnings({"unchecked"})
    @Override
    public void stop() {
        if (!lifecycle.canMoveToStopped()) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.beforeStop();
        }
        lifecycle.moveToStopped();
        doStop();
        for (LifecycleListener listener : listeners) {
            listener.afterStop();
        }
    }

    protected abstract void doStop();

    @Override
    public void close() {
        if (lifecycle.started()) {
            stop();
        }
        if (!lifecycle.canMoveToClosed()) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.beforeClose();
        }
        lifecycle.moveToClosed();
        try {
            doClose();
        } catch (IOException e) {
            // TODO: we need to separate out closing (ie shutting down) services, vs releasing runtime transient
            // structures. Shutting down services should use IOUtils.close
            logger.warn("failed to close " + getClass().getName(), e);
        }
        for (LifecycleListener listener : listeners) {
            listener.afterClose();
        }
    }

    protected abstract void doClose() throws IOException;
}
