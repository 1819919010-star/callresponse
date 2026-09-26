package com.github.JumDa5he.callresponse.compat.outpost;

/** Small synced marker so client interactions can identify a Revenge Maid before TLM predicts taming. */
public interface OutpostMaidMarker {
    boolean callresponse$isOutpostMaid();

    void callresponse$setOutpostMaid(boolean outpostMaid);
}
