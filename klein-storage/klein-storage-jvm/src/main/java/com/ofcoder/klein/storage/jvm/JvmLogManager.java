/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ofcoder.klein.storage.jvm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;
import com.ofcoder.klein.common.serialization.Hessian2Util;
import com.ofcoder.klein.common.util.StreamUtil;
import com.ofcoder.klein.spi.Join;
import com.ofcoder.klein.storage.facade.Instance;
import com.ofcoder.klein.storage.facade.LogManager;
import com.ofcoder.klein.storage.facade.Snap;
import com.ofcoder.klein.storage.facade.config.StorageProp;
import com.ofcoder.klein.storage.facade.exception.LockException;
import com.ofcoder.klein.storage.facade.exception.StorageException;

/**
 * @author 释慧利
 */
@Join
public class JvmLogManager implements LogManager {

    private ConcurrentMap<Long, Instance> runningInstances;
    private ConcurrentMap<Long, Instance> confirmedInstances;
    private ReentrantReadWriteLock lock;
    private MateData mateData;
    private static final String PATH = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "data";
    private static final String MATE = PATH + File.separator + "mate";

    @Override
    public void init(StorageProp op) {
        File file = new File(PATH);
        if (!file.exists()) {
            boolean mkdir = file.mkdir();
        }

        runningInstances = new ConcurrentHashMap<>();
        confirmedInstances = new ConcurrentHashMap<>();
        lock = new ReentrantReadWriteLock(true);
        loadMateData();
    }

    @Override
    public void shutdown() {
        runningInstances.clear();
        confirmedInstances.clear();
    }

    @Override
    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    @Override
    public Instance getInstance(long id) {
        if (runningInstances.containsKey(id)) {
            return runningInstances.get(id);
        }
        if (confirmedInstances.containsKey(id)) {
            return confirmedInstances.get(id);
        }
        return null;
    }

    @Override
    public List<Instance> getInstanceNoConfirm() {
        return Lists.newArrayList(runningInstances.values());
    }

    @Override
    public void updateInstance(Instance instance) {
        if (!lock.isWriteLockedByCurrentThread()) {
            throw new LockException("before calling this method: updateInstance, you need to obtain the lock");
        }
        this.mateData.setMaxProposalNo(Math.max(this.mateData.getMaxProposalNo(), instance.getProposalNo()));
        this.mateData.setMaxInstanceId(Math.max(this.mateData.getMaxInstanceId(), instance.getInstanceId()));
        if (instance.getState() == Instance.State.CONFIRMED) {
            confirmedInstances.put(instance.getInstanceId(), instance);
            runningInstances.remove(instance.getInstanceId());
            if (instance.getApplied().get()) {
                this.mateData.setMaxAppliedInstanceId(Math.max(this.mateData.getMaxAppliedInstanceId(), instance.getInstanceId()));
            }
        } else {
            runningInstances.put(instance.getInstanceId(), instance);
        }
    }

    @Override
    public long maxInstanceId() {
        return this.mateData.getMaxInstanceId();
    }

    @Override
    public long maxProposalNo() {
        return this.mateData.getMaxProposalNo();
    }

    @Override
    public long maxAppliedInstanceId() {
        return this.mateData.getMaxAppliedInstanceId();
    }

    @Override
    public void saveSnap(Snap snap) {
        File file = new File(PATH + File.separator + snap.getCheckpoint());
        if (file.exists()) {
            return;
        }
        this.mateData.setLastSnap(new MateData.SnapMate(snap.getCheckpoint(), file.getPath()));
        FileOutputStream snapOut = null;
        FileOutputStream lastOut = null;
        try {
            lastOut = new FileOutputStream(MATE);
            snapOut = new FileOutputStream(file);
            IOUtils.write(Hessian2Util.serialize(snap), snapOut);
            IOUtils.write(Hessian2Util.serialize(this.mateData), lastOut);
        } catch (IOException e) {
            throw new StorageException("save snap, " + e.getMessage(), e);
        } finally {
            StreamUtil.close(snapOut);
            StreamUtil.close(lastOut);
        }

        truncCheckpoint(snap.getCheckpoint());
    }

    private void truncCheckpoint(long checkpoint) {
        Set<Long> removeKeys = confirmedInstances.keySet().stream().filter(it -> it > checkpoint).collect(Collectors.toSet());
        removeKeys.forEach(confirmedInstances::remove);
    }

    @Override
    public Snap getLastSnap() {
        File file = new File(MATE);
        if (!file.exists()) {
            return null;
        }

        Snap lastSnap;
        FileInputStream lastIn = null;
        FileInputStream snapIn = null;
        try {
            lastIn = new FileInputStream(file);
            MateData deserialize = Hessian2Util.deserialize(IOUtils.toByteArray(lastIn));
            snapIn = new FileInputStream(deserialize.getLastSnap().getPath());
            lastSnap = Hessian2Util.deserialize(IOUtils.toByteArray(snapIn));
            return lastSnap;
        } catch (IOException e) {
            throw new StorageException("get last snap, " + e.getMessage(), e);
        } finally {
            StreamUtil.close(lastIn);
            StreamUtil.close(snapIn);
        }
    }

    private void loadMateData() {
        this.mateData = MateData.Builder.aMateData()
                .maxInstanceId(0)
                .maxProposalNo(0)
                .maxAppliedInstanceId(0)
                .lastSnap(null).build();
        File file = new File(MATE);
        if (!file.exists()) {
            return;
        }

        FileInputStream lastIn = null;
        try {
            lastIn = new FileInputStream(file);
            this.mateData = Hessian2Util.deserialize(IOUtils.toByteArray(lastIn));
        } catch (IOException e) {
            throw new StorageException("get checkpoint, " + e.getMessage(), e);
        } finally {
            StreamUtil.close(lastIn);
        }
    }


}
