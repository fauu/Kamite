const webkitPrefixPrefixes = ["mask"];

export const cssPrefixer = (k: string, v: string): string => {
  let res = `${k}: ${v};\n`;
  if (webkitPrefixPrefixes.some(p => k.startsWith(p))) {
    res += `-webkit-${k}: ${v};\n`;
  }
  return res;
};
