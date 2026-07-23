import {mutationOptions} from '@tanstack/react-query';
import {createAuction,uploadSellImages} from '../api/sellApi';
import type {RegisterAuctionRequestDto} from '../dto/sellDto';

export const sellMutations={
  register:()=>mutationOptions({
    mutationKey:['sell','register'],
    mutationFn:async(request:RegisterAuctionRequestDto)=>{
      const photos=await uploadSellImages(request.photos);
      return createAuction({
        form:request.form,
        photos,
        psaCertification:request.psaCertification,
      });
    },
  }),
};
