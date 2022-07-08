/*
 * Adapted with original modifications from CodeImage - https://github.com/riccardoperra/codeimage/.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  MIT License
 *
 *  Copyright (c) 2022 Riccardo Perra
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import type {
  ComputePositionConfig, ComputePositionReturn, VirtualElement
} from "@floating-ui/core";
import { autoUpdate, computePosition, type ReferenceElement } from "@floating-ui/dom";
import { type Accessor, createEffect, createSignal, mergeProps, on } from "solid-js";
import { createStore } from "solid-js/store";

type Data = Omit<ComputePositionReturn, "x" | "y"> & {
  x: number | null,
  y: number | null,
};

export type Floating = Data & {
  update: () => void,
  setReference: (node: ReferenceElement | null) => void,
  setFloating: (node: ReferenceElement | null) => void,
  refs: {
    reference: Accessor<ReferenceElement | null>,
    floating: Accessor<ReferenceElement | null>,
  },
};

export type Options =
  Omit<Partial<ComputePositionConfig>, "platform"> & { runAutoUpdate?: boolean };

export function useFloating({
  middleware,
  placement,
  strategy,
  runAutoUpdate,
}: Options = {}): Floating {
  const [reference, setReference] = createSignal<Element | VirtualElement | null>(null);
  const [floating, setFloating] = createSignal<HTMLElement | null>(null);

  const [data, setData] = createStore<Data>({
    x: null,
    y: null,
    strategy: strategy ?? "absolute",
    placement: "bottom",
    middlewareData: {},
  });

  function update() {
    const referenceEl = reference() as HTMLElement | null;
    const floatingEl = floating();

    if (!referenceEl || !floatingEl) {
      return;
    }

    function updater() {
      if (!referenceEl || !floatingEl) {
        return;
      }

      void computePosition(referenceEl, floatingEl, {
        middleware,
        placement,
        strategy,
      }).then(data => setData(data));
    }

    if (runAutoUpdate) {
      autoUpdate(referenceEl, floatingEl, updater, { ancestorScroll: false });
    } else {
      updater();
    }
  }

  createEffect(on([reference, floating], update));

  return mergeProps(data, {
    update,
    setReference,
    setFloating,
    refs: {
      reference,
      floating,
    },
  }) as Floating;
}
