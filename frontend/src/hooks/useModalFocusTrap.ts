import {useEffect, useRef} from 'react';

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

export function useModalFocusTrap(
  onClose: () => void,
  closeDisabled = false,
) {
  const dialogRef = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  const closeDisabledRef = useRef(closeDisabled);

  useEffect(() => {
    onCloseRef.current = onClose;
    closeDisabledRef.current = closeDisabled;
  });

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const dialog = dialogRef.current;
    const initialFocus = dialog?.querySelector<HTMLElement>('input:not([disabled])');
    (initialFocus ?? dialog)?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!closeDisabledRef.current) onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;

      const currentDialog = dialogRef.current;
      if (!currentDialog) return;
      const focusableElements = Array.from(
        currentDialog.querySelectorAll<HTMLElement>(focusableSelector),
      );
      const first = focusableElements[0];
      const last = focusableElements.at(-1);
      if (!first || !last) {
        event.preventDefault();
        currentDialog.focus();
        return;
      }
      if (event.shiftKey && (
        document.activeElement === first
        || !currentDialog.contains(document.activeElement)
      )) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (
        document.activeElement === last
        || !currentDialog.contains(document.activeElement)
      )) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, []);

  return dialogRef;
}
