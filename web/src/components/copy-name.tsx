"use client";

import { useState } from "react";
import { glyph } from "@/content";
import { Glyph } from "./kit";

/** The last step of the route: copies the group's name for the server list's search field. */
export function CopyName({ name, label, copied, failed }: { name: string; label: string; copied: string; failed: string }) {
  const [status, setStatus] = useState<"idle" | "ok" | "fail">("idle");

  async function copy() {
    try {
      await navigator.clipboard.writeText(name);
      setStatus("ok");
    } catch {
      setStatus("fail");
    }
  }

  return (
    <div className="copy">
      <button type="button" className="btn-target" onClick={copy}>
        <span className="btn-target-name">{name}</span>
        <span className="btn-target-hint">
          <Glyph c={status === "ok" ? glyph.ok : glyph.copy} /> {label}
        </span>
      </button>
      <p role="status" className="feedback" data-state={status}>
        {status === "ok" ? copied : status === "fail" ? failed : ""}
      </p>
    </div>
  );
}
