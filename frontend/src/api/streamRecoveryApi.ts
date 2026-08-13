import {authenticatedRequest} from './authenticatedRequest';

export type StreamRecoveryStatusDto={
  paused:boolean;
  pendingCount:number;
  errorCount:number;
  firstIncompleteStreamId:string|null;
  firstFailureMessage:string|null;
};

export function fetchStreamRecoveryStatus(){
  return authenticatedRequest<StreamRecoveryStatusDto>('/api/admin/auction-stream/recovery/status');
}
