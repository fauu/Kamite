export function parseBackendConstant(constant: string): string {
  return constant.toLowerCase().replaceAll("_", "-");
}
