import {authenticatedRequest} from './authenticatedRequest';

export type StreamRecoveryStatusDto={
  paused:boolean;
  pendingCount:number;
  errorCount:number;
  firstIncompleteStreamId:string|null;
  firstFailureMessage:string|null;
};

export type StreamRecoveryEventDto={
  streamId:string;auctionId:number|null;eventType:string;projectionStatus:'PENDING'|'ERROR';attemptCount:number;
  occurredAt:string;lastAttemptAt:string|null;failureMessage:string|null;
};

export type StreamRecoveryEventPageDto={content:StreamRecoveryEventDto[];page:number;totalPages:number;totalElements:number};

export function fetchStreamRecoveryStatus(){
  return authenticatedRequest<StreamRecoveryStatusDto>('/api/admin/auction-stream/recovery/status');
}

export function fetchStreamRecoveryEvents(page=0){
  return authenticatedRequest<StreamRecoveryEventPageDto>(`/api/admin/auction-stream/recovery/events?page=${page}`);
}
