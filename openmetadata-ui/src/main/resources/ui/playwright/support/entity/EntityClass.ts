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
import { Page } from '@playwright/test';
import {
  assignDomain,
  removeDomain,
  updateDomain,
} from '../../utils/domainUtils';
import {
  addOwner,
  assignGlossaryTerm,
  assignTag,
  assignTier,
  removeGlossaryTerm,
  removeOwner,
  removeTag,
  removeTier,
  updateDescription,
  updateOwner,
} from '../../utils/entityUtils';
import { Domain } from '../domain/Domain';
import { GlossaryTerm } from '../glossary/GlossaryTerm';

export class EntityClass {
  type: string;

  public getType() {
    return this.type;
  }

  async domain(
    page: Page,
    domain1: Domain['responseData'],
    domain2: Domain['responseData']
  ) {
    await assignDomain(page, domain1);
    await updateDomain(page, domain2);
    await removeDomain(page);
  }

  async owner(
    page: Page,
    owner1: string,
    owner2: string,
    type: 'Teams' | 'Users' = 'Users'
  ) {
    await addOwner(page, owner1, type, 'data-assets-header');
    await updateOwner(page, owner2, type, 'data-assets-header');
    await removeOwner(page, 'data-assets-header');
  }

  async tier(page: Page, tier1: string, tier2: string) {
    await assignTier(page, tier1);
    await assignTier(page, tier2);
    await removeTier(page);
  }

  async descriptionUpdate(page: Page) {
    const description =
      // eslint-disable-next-line max-len
      'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus varius quam eu mi ullamcorper, in porttitor magna mollis. Duis a tellus aliquet nunc commodo bibendum. Donec euismod maximus porttitor. Aenean quis lacus ultrices, tincidunt erat ac, dapibus felis.';

    await updateDescription(page, description);
  }

  async tag(page: Page, tag1: string, tag2: string) {
    await assignTag(page, tag1);
    await assignTag(page, tag2, 'Edit');
    await removeTag(page, [tag1, tag2]);

    await page
      .getByTestId('entity-right-panel')
      .getByTestId('tags-container')
      .getByTestId('Add')
      .isVisible();
  }

  async glossaryTerm(
    page: Page,
    glossaryTerm1: GlossaryTerm['responseData'],
    glossaryTerm2: GlossaryTerm['responseData']
  ) {
    await assignGlossaryTerm(page, glossaryTerm1);
    await assignGlossaryTerm(page, glossaryTerm2, 'Edit');
    await removeGlossaryTerm(page, [glossaryTerm1, glossaryTerm2]);

    await page
      .getByTestId('entity-right-panel')
      .getByTestId('glossary-container')
      .getByTestId('Add')
      .isVisible();
  }
}