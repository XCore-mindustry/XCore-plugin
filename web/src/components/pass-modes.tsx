"use client";

import { modes, type ModeId } from "@/content";
import { accent, Sprite } from "./kit";
import { openMode, usePassState } from "./pass-store";

/** The pass's mode row: a ghost cell per mode until the visitor opens it on the board below. */
export function PassModes({ names }: { names: Record<ModeId, string> }) {
  const { seen } = usePassState();

  return (
    <ul className="mode-cells">
      {modes.map((m) => {
        const sprite = m.sprites[m.sprites.length - 1];
        return (
          <li key={m.id}>
            <a href="#modes" className="mode-cell" data-on={seen.has(`mode:${m.id}`)} style={accent(m.accent)} onClick={() => openMode(m.id)}>
              <Sprite src={sprite.src} size={sprite.size} />
              <span>{names[m.id]}</span>
            </a>
          </li>
        );
      })}
    </ul>
  );
}
