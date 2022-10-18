package com.ofcoder.klein.consensus.facade;

import java.nio.ByteBuffer;

/**
 * @author: 释慧利
 */
public interface Consensus {

    Result propose(final ByteBuffer data);

}
