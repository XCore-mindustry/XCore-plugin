import { expect, test } from "bun:test";
import { parseOperationResult } from "../src/lib/server/accounts";

const operationId = "10000000-0000-4000-8000-000000000001";
const response = {
  messageType: "discord.account.operation.response",
  messageVersion: 1,
  server: "game",
  operationId,
  status: "success",
  linkedAt: 123,
};

test("an RPC response must match operation, server and canonical message identity", () => {
  for (const value of [
    null,
    {},
    { ...response, operationId: "other" },
    { ...response, server: "other" },
    { ...response, messageType: "other" },
    { ...response, messageVersion: 2 },
    { ...response, status: "maybe" },
  ]) {
    expect(() => parseOperationResult(value, operationId, "game")).toThrow(
      "invalid-operation-response",
    );
  }
});

test("all canonical outcomes stay distinct and extra response fields are stripped", () => {
  for (const status of ["success", "rejected", "failed", "unknown"] as const) {
    const value = parseOperationResult(
      {
        ...response,
        status,
        uuid: "private-uuid",
        password_hash: "private-hash",
        reason: "timeout",
      },
      operationId,
      "game",
    );
    expect(value.status).toBe(status);
    expect(value).toEqual({
      operationId,
      status,
      linkedAt: 123,
      reason: "timeout",
    });
  }
});
