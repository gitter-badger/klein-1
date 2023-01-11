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
package com.ofcoder.klein.consensus.paxos.rpc.vo;

import java.io.Serializable;

/**
 * NodeState.
 *
 * @author 释慧利
 */
public class NodeState implements Serializable {
    private String nodeId;
    private long maxInstanceId;
    private long lastAppliedInstanceId;
    private long lastCheckpoint;

    public String getNodeId() {
        return nodeId;
    }

    public long getMaxInstanceId() {
        return maxInstanceId;
    }

    public long getLastAppliedInstanceId() {
        return lastAppliedInstanceId;
    }

    public long getLastCheckpoint() {
        return lastCheckpoint;
    }

    public static final class Builder {
        private String nodeId;
        private long maxInstanceId;
        private long lastAppliedInstanceId;
        private long lastCheckpoint;

        private Builder() {
        }

        /**
         * aNodeState.
         *
         * @return Builder
         */
        public static Builder aNodeState() {
            return new Builder();
        }

        /**
         * nodeId.
         *
         * @param nodeId nodeId
         * @return Builder
         */
        public Builder nodeId(final String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * maxInstanceId.
         *
         * @param maxInstanceId maxInstanceId
         * @return Builder
         */
        public Builder maxInstanceId(final long maxInstanceId) {
            this.maxInstanceId = maxInstanceId;
            return this;
        }

        /**
         * lastAppliedInstanceId.
         *
         * @param lastAppliedInstanceId lastAppliedInstanceId
         * @return Builder
         */
        public Builder lastAppliedInstanceId(final long lastAppliedInstanceId) {
            this.lastAppliedInstanceId = lastAppliedInstanceId;
            return this;
        }

        /**
         * lastCheckpoint.
         *
         * @param lastCheckpoint lastCheckpoint
         * @return Builder
         */
        public Builder lastCheckpoint(final long lastCheckpoint) {
            this.lastCheckpoint = lastCheckpoint;
            return this;
        }

        /**
         * build.
         *
         * @return NodeState
         */
        public NodeState build() {
            NodeState nodeState = new NodeState();
            nodeState.nodeId = this.nodeId;
            nodeState.lastCheckpoint = this.lastCheckpoint;
            nodeState.lastAppliedInstanceId = this.lastAppliedInstanceId;
            nodeState.maxInstanceId = this.maxInstanceId;
            return nodeState;
        }
    }
}
