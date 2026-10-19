"use client";

import { useSyncExternalStore } from "react";
import type { ModeId } from "@/content";

// What the visitor has seen on this visit. Marks are only ever added: a stamp on the pass never clears.
type State = { seen: ReadonlySet<string>; open: ModeId };

const initial: State = { seen: new Set(), open: "hexed" };
let state = initial;
const listeners = new Set<() => void>();

function update(next: Partial<State>) {
  state = { ...state, ...next };
  listeners.forEach((listener) => listener());
}

export function markSeen(id: string) {
  if (!state.seen.has(id)) update({ seen: new Set(state.seen).add(id) });
}

export function openMode(id: ModeId) {
  update({ open: id });
  markSeen(`mode:${id}`);
}

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => void listeners.delete(listener);
};

export const usePassState = () => useSyncExternalStore(subscribe, () => state, () => initial);
