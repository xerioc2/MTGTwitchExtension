import assert from 'node:assert/strict';
import test from 'node:test';
import { runSerializedLoop } from './loop.js';

test('runSerializedLoop waits for each task before sleeping and starting the next', async () => {
  const order: string[] = [];
  let runs = 0;
  let activeTasks = 0;
  let maximumActiveTasks = 0;

  await runSerializedLoop(async () => {
    activeTasks += 1;
    maximumActiveTasks = Math.max(maximumActiveTasks, activeTasks);
    runs += 1;
    order.push(`task-${runs}`);
    await Promise.resolve();
    activeTasks -= 1;
  }, 5000, {
    sleep: async (delayMs) => {
      assert.equal(delayMs, 5000);
      order.push('sleep');
    },
    shouldContinue: () => runs < 3
  });

  assert.equal(maximumActiveTasks, 1);
  assert.deepEqual(order, ['task-1', 'sleep', 'task-2', 'sleep', 'task-3']);
});
