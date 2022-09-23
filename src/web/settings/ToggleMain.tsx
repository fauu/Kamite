import { type VoidComponent } from "solid-js";
import { css, styled } from "solid-styled-components";

import { ChromeClass } from "~/style";

import type { SettingsFieldMainProps } from "./FieldMainProps";
import type { ToggleSettingMain } from "./Setting";

export const SettingsToggleMain:
  VoidComponent<SettingsFieldMainProps<ToggleSettingMain, HTMLInputElement>> = (props) =>
    <>
      <Input
        type="checkbox"
        id={props.setting.id}
        checked={props.setting.value}
        onChange={props.onChange}
      />
      <span class={SettingsToggleMainClass}>
        <span class={SwitchClass} />
      </span>
    </>;

export const SettingsToggleMainClass = "toggle-main";

const SwitchClass = "switch";

/*
 * Adapted with original modifications from Toggle Switchy -
 * https://github.com/adamculpepper/toggle-switchy/.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  MIT License
 *
 *  Copyright (c) 2020 Adam Culpepper
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

export const SettingsToggleClass = css`
  color: var(--color-fg);
  display: inline-flex;
  align-items: center;
  user-select: none;
  position: relative;
  vertical-align: middle;
  margin-bottom: 0;
`;

const Input = styled.input`
  position: absolute;
  opacity: 0;

  &:hover {
    cursor: pointer;
  }

  &:checked {
    + .${SettingsToggleMainClass} {
      background: var(--color-accB2);

      &:before {
        opacity: 1;
      }

      &:after {
        opacity: 0;
      }

      .${SwitchClass} {
        border: 3px solid var(--color-accB2);
      }
    }
  }

  &:not(:checked) {
    + .${SettingsToggleMainClass} {
      background: var(--color-bg3);
      border: 1px solid var(--color-bg3-hl);

      .${SwitchClass} {
        border: 3px solid var(--color-bg3);
        right: calc(100% - 30px);
      }
    }
  }

  + .${SettingsToggleMainClass} {
    width: 70px;
    height: var(--form-control-height);
    cursor: pointer;
    border: 1px solid var(--color-accB2-hl);
    border-radius: var(--border-radius-default);
    align-items: center;
    position: relative;
    overflow: hidden;
    flex-shrink: 0;

    &:before,
    &:after {
      display: flex;
      align-items: center;
      position: absolute;
      z-index: 2;
      height: 100%;
      font-size: 0.9rem;
      font-weight: 500;

      .${ChromeClass} & {
        padding-top: 1px;
      }
    }

    &:before {
      content: "ON";
      right: 55%;
      opacity: 0;
    }

    &:after {
      content: "OFF";
      left: 50%;
    }

    .${SwitchClass} {
      border-radius: 4px;
      display: block;
      width: 30px;
      height: 100%;
      position: absolute;
      right: 0;
      z-index: 3;
      box-sizing: border-box;
      background: var(--color-fg);
    }

    + label {
      margin-left: 10px;
    }
  }

  &[disabled] {
    + .${SettingsToggleMainClass} {
      opacity: 0.5;

      &:hover {
        cursor: not-allowed;
      }
    }
  }
`;
