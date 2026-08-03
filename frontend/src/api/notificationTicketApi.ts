import {authenticatedRequest} from './authenticatedRequest';

export type SseTicketDto={
  ticket:string;
  expiresInSeconds:number;
};

export async function issueSseTicket():Promise<SseTicketDto>{
  return authenticatedRequest<SseTicketDto>('/api/sse/tickets',{method:'POST'});
}
