/*
 *  Copyright 2024 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
import { expect, Locator, Page } from '@playwright/test';
import { descriptionBox, removeLandingBanner } from './common';
import { TaskDetails } from './task';

export const REACTION_EMOJIS = ['🚀', '😕', '👀', '❤️', '🎉', '😄', '👎', '👍'];

export const FEED_REACTIONS = [
  'thumbsUp',
  'thumbsDown',
  'laugh',
  'hooray',
  'confused',
  'heart',
  'eyes',
  'rocket',
];
export const FIRST_FEED_SELECTOR =
  '[data-testid="activity-feed-widget"] [data-testid="message-container"]:first-child';

export const checkDescriptionInEditModal = async (
  page: Page,
  taskValue: TaskDetails
) => {
  const taskContent = await page.getByTestId('task-title').innerText();

  expect(taskContent).toContain(`Request to update description for`);

  await page.getByRole('button', { name: 'down' }).click();
  await page.waitForSelector('.ant-dropdown', {
    state: 'visible',
  });

  await page.getByRole('menuitem', { name: 'edit' }).click();

  await expect(page.locator('[role="dialog"].ant-modal')).toBeVisible();

  await expect(page.locator('.ant-modal-title')).toContainText(
    `Update description for table ${taskValue.term} columns/${taskValue.columnName}`
  );

  await expect(page.locator(descriptionBox)).toContainText(
    taskValue.description ?? ''
  );

  // click on the Current tab
  await page.getByRole('tab', { name: 'current' }).click();

  await expect(page.getByTestId('markdown-parser')).toContainText(
    taskValue.oldDescription ?? ''
  );
};

export const deleteFeedComments = async (page: Page, feed: Locator) => {
  await feed.click();
  await page.locator('[data-testid="delete-message"]').click();

  await page.waitForSelector('[role="dialog"].ant-modal');

  const deleteResponse = page.waitForResponse('/api/v1/feed/*/posts/*');

  await page.getByTestId('save-button').click();

  await deleteResponse;
};

export const reactOnFeed = async (page: Page) => {
  for (const reaction of FEED_REACTIONS) {
    await page
      .locator(
        '[data-testid="activity-feed-widget"] [data-testid="message-container"]:first-child'
      )
      .locator('[data-testid="feed-reaction-container"]')
      .locator('[data-testid="add-reactions"]')
      .click();

    await page
      .locator('.ant-popover-feed-reactions .ant-popover-inner-content')
      .waitFor({ state: 'visible' });
    await page
      .locator(`[data-testid="reaction-button"][title="${reaction}"]`)
      .click();
  }
};

export const addMentionCommentInFeed = async (page: Page, user: string) => {
  await removeLandingBanner(page);

  // Click on add reply
  const feedResponse = page.waitForResponse('/api/v1/feed/*');
  await page
    .locator(FIRST_FEED_SELECTOR)
    .locator('[data-testid="thread-count"]')
    .click();

  await feedResponse;

  await page.waitForSelector('.ant-drawer-content', {
    state: 'visible',
  });

  // Type reply with mention
  await page
    .locator(
      '[data-testid="editor-wrapper"] [contenteditable="true"].ql-editor'
    )
    .click();

  const userSuggestionsResponse = page.waitForResponse(
    `/api/v1/search/query?q=*${user}***`
  );

  await page
    .locator(
      '[data-testid="editor-wrapper"] [contenteditable="true"].ql-editor'
    )
    .type(`Can you resolve this thread for me? @${user}`);
  await userSuggestionsResponse;

  await page.locator(`[data-value="@${user}"]`).click();

  // Send reply
  await expect(page.locator('[data-testid="send-button"]')).toBeVisible();
  await expect(page.locator('[data-testid="send-button"]')).not.toBeDisabled();

  const postReplyResponse = page.waitForResponse('/api/v1/feed/*/posts');
  await page.locator('[data-testid="send-button"]').click();
  await postReplyResponse;
};
