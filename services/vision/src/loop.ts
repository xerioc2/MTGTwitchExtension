export type SerializedLoopOptions = {
  sleep?: (delayMs: number) => Promise<void>;
  shouldContinue?: () => boolean;
};

export async function runSerializedLoop(
  task: () => Promise<void>,
  intervalMs: number,
  opts: SerializedLoopOptions = {}
): Promise<void> {
  const sleep = opts.sleep ?? ((delayMs: number) => new Promise((resolve) => setTimeout(resolve, delayMs)));
  const shouldContinue = opts.shouldContinue ?? (() => true);

  while (true) {
    await task();
    if (!shouldContinue()) {
      return;
    }
    await sleep(Math.max(0, intervalMs));
  }
}
