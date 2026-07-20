package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.common.error.ConflictException;

public enum OpportunityStage {
  LEAD, OPPORTUNITY, POC, BIDDING, CONTRACT;

  public OpportunityStage next() {
    if (this == CONTRACT) {
      throw new ConflictException("合同阶段必须转交实施或丢单");
    }
    return values()[ordinal() + 1];
  }
}
