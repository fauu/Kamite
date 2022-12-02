import {
  createEffect, For, Match, Show, Switch, type Accessor, type JSX, type VoidComponent
} from "solid-js";
import { css, styled, useTheme, type DefaultTheme } from "solid-styled-components";

import { concealUnlessHovered } from "~/directives";
const [_] = [concealUnlessHovered];

import { lookupTargetFillURL } from "~/backend";
import { scrollToBottom } from "~/common";
import { useGlobalTooltip } from "~/GlobalTooltip";
import { themeLayoutFlipped } from "~/theme";

import { NotebookHeightHud } from "./HeightHud";
import type { NotebookState } from "./State";
import { NotebookTab as NotebookTabView, NotebookTabDisplayClass } from "./Tab";
import { notebookTabIsEmbeddedLookup, type NotebookTab } from "./tabs";

interface NotebookProps {
  state: NotebookState,
  lookupText: string,
  chunkPicker: JSX.Element,
  chunkHistory: JSX.Element,
  settings: JSX.Element,
  debug: JSX.Element,
  focusMode: Accessor<boolean>,
}

export const Notebook: VoidComponent<NotebookProps> = (props) => {
  const theme = useTheme();

  let tabBarEl!: HTMLDivElement;
  let pageViewEl!: HTMLDivElement;
  const pageEls: { [page: string]: HTMLDivElement } = {};

  const doConcealUnlessHovered = () => !props.state.resizing() && props.focusMode();

  const effectiveLookupText = () => props.state.lookupOverride()?.text || props.lookupText;

  createEffect(() => props.state.activeTab() && switchPage(props.state.activeTab()));

  createEffect(() => {
    const maxHeight = props.state.collapsed()
      ? 0
      : props.state.height().px - tabBarEl.offsetHeight;
    pageViewEl.style.maxHeight = `${maxHeight}px`;
  });

  createEffect(() => {
    const lookupOverride = props.state.lookupOverride();
    if (lookupOverride) {
      const tab = props.state.getLookupTabs().find(t => t.lookup?.symbol === lookupOverride.symbol);
      tab && handleTabClick(tab);
    }
  });

  const tooltip = useGlobalTooltip()!;

  const lookupIframeSyncSrc = (tab: NotebookTab) => {
    const text = effectiveLookupText();
    const url = lookupTargetFillURL(tab.lookup!, text);
    (pageEls[tab.id]!.children[0] as HTMLIFrameElement).src = url;
  };

  const switchPage = (tab: NotebookTab) => {
    Object.entries(pageEls).forEach(([page, el]) => {
      if (tab.id === page) {
        el.style.display = "flex";
        if (tab.keepScrolled) {
          scrollToBottom(el);
        }
      } else {
        el.style.display = "none";
      }
    });
  };

  const handleExpanderMouseOver = () => props.state.setCollapsed(false);

  const handleTabClick = (tab: NotebookTab) => {
    if (tab.lookup?.newTab) {
      window.open(lookupTargetFillURL(tab.lookup, effectiveLookupText()));
    } else {
      if (notebookTabIsEmbeddedLookup(tab)) {
        lookupIframeSyncSrc(tab);
      }
      props.state.activateTab(tab.id);
    }
    props.state.setCollapsed(false);
  };

  return <div
    classList={{
      [RootClass({ theme })]: true,
      [NotebookCollapsedClass]: props.state.collapsed(),
    }}
    use:concealUnlessHovered={{ enabled: doConcealUnlessHovered }}
  >
    <Show when={props.state.resizing()}>
      <NotebookHeightHud configKey="ui.notebook.height" height={props.state.height()}/>
    </Show>
    <TabBar ref={tabBarEl} id="notebook-tabbar">
      <Expander onMouseOver={handleExpanderMouseOver} />
      <For each={props.state.groupedTabs()}>{group =>
        <TabBarGroup class="notebook-tab-group">
          <For each={group}>{tab =>
            <Show when={!tab.hidden}>
              <NotebookTabView
                tab={tab}
                active={props.state.isTabActive(tab.id)}
                tooltip={tooltip}
                onClick={() => handleTabClick(tab)}
              />
            </Show>
          }</For>
        </TabBarGroup>
      }</For>
    </TabBar>
    <PageView ref={pageViewEl} id="notebook-page-view">
      <For each={props.state.tabs}>{tab =>
        <PageContainer ref={pageEls[tab.id]}>
          <Switch>
            <Match when={notebookTabIsEmbeddedLookup(tab)}>
              <iframe src="about:blank"></iframe>
            </Match>
            <Match when={tab.id === "chunk-picker"}>
              {props.chunkPicker}
            </Match>
            <Match when={tab.id === "chunk-history"}>
              {props.chunkHistory}
            </Match>
            <Match when={tab.id === "settings"}>
              {props.settings}
            </Match>
            <Match when={tab.id === "debug"}>
              {props.debug}
            </Match>
          </Switch>
        </PageContainer>
      }</For>
    </PageView>
  </div>;
};

const NotebookCollapsedClass = css("");

const RootClass = (p: { theme?: DefaultTheme }) => css`
  position: relative;
  display: flex;
  flex-grow: 1;
  flex-direction: ${!themeLayoutFlipped(p.theme) ? "column" : "column-reverse"};

  &:before,
  &:after {
    content: "";
    height: 6px;
    width: 100%;
    position: absolute;
    z-index: 10;
    opacity: 0;
  }

  &:not(.${NotebookCollapsedClass}) {
    &:before,
    &:after {
      cursor: row-resize;
    }
  }

  &:before {
    top: -3px;
    display: ${!themeLayoutFlipped(p.theme) ? "block" : "none"};
  }

  &:after {
    bottom: -3px;
    display: ${!themeLayoutFlipped(p.theme) ? "none" : "block"};
  }
`;

const TabBar = styled.div`
  font-size: 0.9rem;
  font-weight: 500;
  background: var(--color-bg-hl);
  color: var(--color-fg);
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--color-bg2);
  border-bottom: 1px solid var(--color-bg2);
  z-index: 5;
  box-shadow: 0px ${p => !themeLayoutFlipped(p.theme) ? "-" : ""}10px 20px rgba(0, 0, 0, 0.2), 0px 0px 6px rgba(0, 0, 0, 0.15);
`;

const Expander = styled.div`
  flex: 1;
`;

const TabBarGroup = styled.div`
  --side-border: 1px solid var(--color-bg);
  display: flex;

  &:nth-child(2) {
    .${NotebookTabDisplayClass} {
      border-right: var(--side-border);
    }
    order: -1;
  }

  &:last-child {
    .${NotebookTabDisplayClass} {
      border-left: var(--side-border);
    }
  }
`;

const PageView = styled.div`
  display: flex;
  flex: 1;
  background: var(--color-bg);
  color: var(--color-fg);
  overflow-y: initial;

  & > div {
    display: none;
    flex: 1;
    padding: 0.8rem 0.75rem 1rem 0.75rem;
    overflow-y: auto;
  }

  iframe {
    flex: 1;
    border: none;
    overflow: hidden;
  }
`;

const PageContainer = styled.div`
  & > * {
    width: 100%;
  }
`;
