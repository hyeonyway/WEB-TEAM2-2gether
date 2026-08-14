import * as TooltipPrimitive from '@radix-ui/react-tooltip';
import type {ComponentProps} from 'react';

export const TooltipProvider=TooltipPrimitive.Provider;
export const Tooltip=TooltipPrimitive.Root;
export const TooltipTrigger=TooltipPrimitive.Trigger;

export function TooltipContent({className='',sideOffset=6,...props}:ComponentProps<typeof TooltipPrimitive.Content>){
  return <TooltipPrimitive.Portal><TooltipPrimitive.Content className={`tooltip-content ${className}`} sideOffset={sideOffset} {...props}/></TooltipPrimitive.Portal>;
}
