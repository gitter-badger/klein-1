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
package com.ofcoder.klein.consensus.paxos.core;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.ofcoder.klein.common.Lifecycle;
import com.ofcoder.klein.common.util.ThreadExecutor;
import com.ofcoder.klein.consensus.facade.SM;
import com.ofcoder.klein.consensus.facade.config.ConsensusProp;
import com.ofcoder.klein.consensus.paxos.PaxosNode;
import com.ofcoder.klein.storage.facade.Instance;
import com.ofcoder.klein.storage.facade.LogManager;
import com.ofcoder.klein.storage.facade.StorageEngine;

/**
 * @author 释慧利
 */
public class Learner implements Lifecycle<ConsensusProp> {
    private static final Logger LOG = LoggerFactory.getLogger(Learner.class);
    private final PaxosNode self;
    private LogManager logManager;
    private SM sm;
    private PriorityQueue<ApplyEntry> applyQueue = new  PriorityQueue<>(Comparator.comparingLong(ApplyEntry::getInstanceId));
    public Learner(PaxosNode self) {
        this.self = self;
    }

    @Override
    public void init(ConsensusProp op) {
        logManager = StorageEngine.getLogManager();
    }

    @Override
    public void shutdown() {
        CountDownLatch latch = new CountDownLatch(1);
        ThreadExecutor.submit(() -> {
            try {
                sm.makeImage();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
        }

    }

    public void loadSM(SM sm) {
        this.sm = sm;
    }

    public void learn(long instanceId) {
        LOG.info("start learn, instanceId: {}", instanceId);
        ProposeContext ctxt = new ProposeContext(instanceId, Lists.newArrayList(Noop.DEFAULT));
        RoleAccessor.getProposer().forcePrepare(ctxt, new PhaseCallback() {
            @Override
            public void granted(ProposeContext context) {
//                RoleAccessor.getProposer().accept
            }

            @Override
            public void confirmed(ProposeContext context) {
//                confirm();
            }

            @Override
            public void refused(ProposeContext context) {

            }
        });
    }



    private void apply(long instanceId, List<Object> datas) {
        if (instanceId <= logManager.maxConfirmInstanceId()) {
            // the Instance has been confirmed.
            return;
        }
        long exceptConfirmId = logManager.maxConfirmInstanceId() + 1;
        if (instanceId > exceptConfirmId) {
            learn(exceptConfirmId);
        }

        datas.forEach(it -> sm.apply(it));
        // update log to applied.

    }


    public void confirm(long instanceId, List<Object> datas) {
        LOG.info("start confirm phase, instanceId: {}", instanceId);
        try {
            logManager.getLock().writeLock().lock();

            Instance localInstance = logManager.getInstance(instanceId);
            if (localInstance == null) {
                // the prepare message is not received, but the confirm message is received
                learn(instanceId);
                return;
            }
            localInstance.setState(Instance.State.CONFIRMED);
            localInstance.setGrantedValue(datas);
            logManager.updateInstance(localInstance);

            // apply statemachine
            applyQueue.offer(new ApplyEntry(instanceId, datas));
        } finally {
            logManager.getLock().writeLock().unlock();
        }
    }

    private static class ApplyEntry  {
        private long instanceId;
        private List<Object> datas;

        public ApplyEntry(long instanceId, List<Object> datas) {
            this.instanceId = instanceId;
            this.datas = datas;
        }

        public long getInstanceId() {
            return instanceId;
        }
    }


}
