import type { VoidComponent } from "solid-js";
import { styled } from "solid-styled-components";

// A hack to keep irrelevant UI text out of the Sentence field when mining with Yomichan
export const YomichanSentenceDelimiter: VoidComponent = () =>
  <Root class="yomichan-sentence-delimiter">。</Root>;

const Root = styled.span`
  opacity: 0.01; /* The element is ignored if truly hidden */
  display: inline-block;
  height: 0;
  width: 0;
`;
