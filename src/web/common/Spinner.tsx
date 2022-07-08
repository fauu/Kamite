/*
 * Adapted with original modifications from css-loaders - https://github.com/lukehaas/css-loaders.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2014 Luke Haas
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of
 *  this software and associated documentation files (the "Software"), to deal in
 *  the Software without restriction, including without limitation the rights to
 *  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *  the Software, and to permit persons to whom the Software is furnished to do so,
 *  subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import { keyframes, styled } from "solid-styled-components";

const spinnerKeyframes = keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`;

interface SpinnerProps {
  size: string,
  fgColor: string,
  bgColor: string,
}

export const Spinner = styled("div")<SpinnerProps>`
  font-size: 10px;
  text-indent: -9999em;
  border-radius: 50%;
  position: relative;
  transform: translateZ(0);
  background: ${p => p.fgColor};
  background: linear-gradient(
    to right,
    ${p => p.fgColor} 10%,
    rgba(255, 255, 255, 0) 42%
  );
  animation: ${spinnerKeyframes} 1.4s infinite linear;
  width: ${p => p.size};
  height: ${p => p.size};

  &:before {
    width: 50%;
    height: 50%;
    border-radius: 100% 0 0 0;
    position: absolute;
    top: 0;
    left: 0;
    content: "";
    background: ${p => p.fgColor};
  }

  &:after {
    width: 75%;
    height: 75%;
    border-radius: 50%;
    content: "";
    margin: auto;
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    right: 0;
    background: ${p => p.bgColor};
  }
`;
