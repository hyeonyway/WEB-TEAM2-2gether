import {authenticatedRequest} from './authenticatedRequest';

export type StreamRecoveryStatusDto={
  pendingCount:number;
  errorCount:number;
  processedCount:number;
  firstIncompleteStreamId:string|null;
  firstFailureMessage:string|null;
  latestProcessedStreamId:string|null;
  latestProcessedAt:string|null;
};

export type StreamRecoveryEventDto={
  streamId:string;auctionId:number|null;eventType:string;projectionStatus:'PENDING'|'ERROR';attemptCount:number;
  occurredAt:string;lastAttemptAt:string|null;processedAt:string|null;failureMessage:string|null;
};

export type StreamRecoveryEventPageDto={content:StreamRecoveryEventDto[];page:number;totalPages:number;totalElements:number};
export type StreamRecoveryReplayDto={accepted:boolean;replayFromStreamId:string|null;pendingCount:number;message:string};

export function fetchStreamRecoveryStatus(){
  return authenticatedRequest<StreamRecoveryStatusDto>('/api/admin/auction-stream/recovery/status');
}

export function fetchStreamRecoveryEvents(page=0){
  return authenticatedRequest<StreamRecoveryEventPageDto>(`/api/admin/auction-stream/recovery/events?page=${page}`);
}

export function fetchStreamRecoveryProcessedEvents(page=0){
  return authenticatedRequest<StreamRecoveryEventPageDto>(`/api/admin/auction-stream/recovery/processed-events?page=${page}`);
}

export function replayStreamRecovery(){
  return authenticatedRequest<StreamRecoveryReplayDto>('/api/admin/auction-stream/recovery/replay',{method:'POST'});
}
