// eslint-disable-next-line @typescript-eslint/no-unused-vars
/* global Toastify:false */
declare const Toastify: any;

export type NotificationKind = "info" | "warning" | "error";

export function notify(kind: NotificationKind, text: string) {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-call
  const toast = new Toastify({
    className: kind.toString().toLowerCase(),
    text,
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    onClick: () => { toast.hideToast(); },
  });
  // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
  toast.showToast();
}
