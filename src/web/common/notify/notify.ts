// eslint-disable-next-line @typescript-eslint/no-unused-vars
/* global Toastify:false */
declare const Toastify: any;

export type NotificationKind = "info" | "warning" | "error";

const DURATIONS_MS: Record<NotificationKind, number> = {
  "info": 5000,
  "warning": 5000,
  "error": 10000,
};

export function notify(kind: NotificationKind, text: string) {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-call
  const toast = new Toastify({
    className: kind.toString().toLowerCase(),
    duration: DURATIONS_MS[kind],
    text,
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-return
    onClick: () => toast.hideToast(),
  });
  // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
  toast.showToast();
}
