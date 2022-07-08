import { getClientHeight } from "~/common";

export type NotebookHeight = {
  px: number,
  percent: number,
};

export function notebookHeightFromPercent(percent: number) {
  return {
    px: Math.round(percent * getClientHeight()),
    percent,
  };
}

export function notebookHeightFromPx(px: number) {
  return {
    px,
    percent: px / getClientHeight(),
  };
}
