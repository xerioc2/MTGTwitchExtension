import { relayChannelIds, stableGameStateJson } from "./relay-state.ts";

function assertEquals(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `Expected ${JSON.stringify(expected)}, received ${
        JSON.stringify(actual)
      }.`,
    );
  }
}

Deno.test("numeric Twitch user id is the only default relay topic", () => {
  assertEquals(
    relayChannelIds(
      { twitch_user_id: " 12345 ", twitch_login: "Xerioc2" },
      false,
    ),
    ["12345"],
  );
});

Deno.test("legacy login topic is available only during an explicit rollback", () => {
  assertEquals(
    relayChannelIds({ twitch_user_id: "12345", twitch_login: "Xerioc2" }, true),
    ["12345", "xerioc2"],
  );
});

Deno.test("invalid numeric Twitch user id refuses all relay topics", () => {
  assertEquals(
    relayChannelIds(
      { twitch_user_id: "xerioc2", twitch_login: "xerioc2" },
      true,
    ),
    [],
  );
});

Deno.test("game-state hashing is stable and ignores only top-level updatedAt", () => {
  const first = stableGameStateJson({
    updatedAt: "2026-07-31T00:00:00Z",
    battlefield: ["Lightning Bolt"],
    gameId: 123,
  });
  const second = stableGameStateJson({
    gameId: 123,
    battlefield: ["Lightning Bolt"],
    updatedAt: "2026-07-31T00:00:01Z",
  });

  assertEquals(first, second);
});
