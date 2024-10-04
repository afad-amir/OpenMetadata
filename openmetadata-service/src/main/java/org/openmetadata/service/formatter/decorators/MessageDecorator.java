/*
 *  Copyright 2021 Collate
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

package org.openmetadata.service.formatter.decorators;

import static org.openmetadata.common.utils.CommonUtil.nullOrEmpty;
import static org.openmetadata.service.events.subscription.AlertUtil.convertInputListToString;
import static org.openmetadata.service.events.subscription.AlertsRuleEvaluator.getEntity;
import static org.openmetadata.service.events.subscription.AlertsRuleEvaluator.getThread;
import static org.openmetadata.service.formatter.entity.IngestionPipelineFormatter.getIngestionPipelineUrl;
import static org.openmetadata.service.resources.feeds.MessageParser.replaceEntityLinks;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.openmetadata.common.utils.CommonUtil;
import org.openmetadata.schema.EntityInterface;
import org.openmetadata.schema.entity.feed.Thread;
import org.openmetadata.schema.tests.TestCase;
import org.openmetadata.schema.type.ChangeEvent;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.ThreadType;
import org.openmetadata.service.Entity;
import org.openmetadata.service.exception.UnhandledServerException;
import org.openmetadata.service.resources.feeds.MessageParser;
import org.openmetadata.service.util.FeedUtils;

public interface MessageDecorator<T> {
  String CONNECTION_TEST_DESCRIPTION =
      "This is a test message, receiving this message confirms that you have successfully configured OpenMetadata to receive alerts.";

  String TEMPLATE_FOOTER = "Change Event By OpenMetadata";

  String getBold();

  String getBoldWithSpace();

  String getLineBreak();

  String getAddMarker();

  String getAddMarkerClose();

  String getRemoveMarker();

  default String httpAddMarker() {
    return "<!add>";
  }

  default String httpRemoveMarker() {
    return "<!remove>";
  }

  String getRemoveMarkerClose();

  String getEntityUrl(String prefix, String fqn, String additionalInput);

  T buildEntityMessage(String publisherName, ChangeEvent event);

  T buildThreadMessage(String publisherName, ChangeEvent event);

  T buildTestMessage(String publisherName);

  default String buildEntityUrl(String entityType, EntityInterface entityInterface) {
    String fqn = resolveFullyQualifiedName(entityType, entityInterface);

    switch (entityType) {
      case Entity.TEST_CASE:
        if (entityInterface instanceof TestCase testCase) {
          return getEntityUrl(
              "incident-manager", testCase.getFullyQualifiedName(), "test-case-results");
        }
        break;

      case Entity.GLOSSARY_TERM:
        return getEntityUrl(Entity.GLOSSARY, fqn, "");

      case Entity.TAG:
        return getEntityUrl("tags", fqn.split("\\.")[0], "");

      case Entity.INGESTION_PIPELINE:
        return getIngestionPipelineUrl(this, entityType, entityInterface);

      default:
        return getEntityUrl(entityType, fqn, "");
    }

    // Fallback in case of no match
    return getEntityUrl(entityType, fqn, "");
  }

  default String buildThreadUrl(
      ThreadType threadType, String entityType, EntityInterface entityInterface) {

    String activeTab =
        threadType.equals(ThreadType.Task) ? "activity_feed/tasks" : "activity_feed/all";

    String fqn = resolveFullyQualifiedName(entityType, entityInterface);

    switch (entityType) {
      case Entity.TEST_CASE:
        if (entityInterface instanceof TestCase) {
          TestCase testCase = (TestCase) entityInterface;
          return getEntityUrl("incident-manager", testCase.getFullyQualifiedName(), "issues");
        }
        break;

      case Entity.GLOSSARY_TERM:
        return getEntityUrl(Entity.GLOSSARY, fqn, activeTab);

      case Entity.TAG:
        return getEntityUrl("tags", fqn.split("\\.")[0], "");

      case Entity.INGESTION_PIPELINE:
        return getIngestionPipelineUrl(this, entityType, entityInterface);

      default:
        return getEntityUrl(entityType, fqn, activeTab);
    }

    // Fallback in case of no match
    return getEntityUrl(entityType, fqn, activeTab);
  }

  // Helper function to resolve FQN if null or empty
  private String resolveFullyQualifiedName(String entityType, EntityInterface entityInterface) {
    String fqn = entityInterface.getFullyQualifiedName();
    if (CommonUtil.nullOrEmpty(fqn)) {
      EntityInterface result =
          Entity.getEntity(entityType, entityInterface.getId(), "id", Include.NON_DELETED);
      fqn = result.getFullyQualifiedName();
    }
    return fqn;
  }

  default String getFQNForChangeEventEntity(ChangeEvent event) {
    return Optional.ofNullable(event.getEntityFullyQualifiedName())
        .filter(fqn -> !CommonUtil.nullOrEmpty(fqn))
        .orElseGet(
            () -> {
              EntityInterface entityInterface = getEntity(event);
              String fqn = entityInterface.getFullyQualifiedName();

              if (CommonUtil.nullOrEmpty(fqn)) {
                EntityInterface result =
                    Entity.getEntity(
                        event.getEntityType(), entityInterface.getId(), "id", Include.NON_DELETED);
                fqn = result.getFullyQualifiedName();
              }

              return fqn;
            });
  }

  default T buildOutgoingMessage(String publisherName, ChangeEvent event) {
    if (event.getEntityType().equals(Entity.THREAD)) {
      return buildThreadMessage(publisherName, event);
    } else if (Entity.getEntityList().contains(event.getEntityType())) {
      return buildEntityMessage(publisherName, event);
    } else {
      throw new IllegalArgumentException(
          "Cannot Build Message, Unsupported Entity Type: " + event.getEntityType());
    }
  }

  default T buildOutgoingTestMessage(String publisherName) {
    return buildTestMessage(publisherName);
  }

  default String getPlaintextDiff(String oldValue, String newValue) {
    // create a configured DiffRowGenerator
    oldValue = oldValue == null ? StringUtils.EMPTY : oldValue;
    String addMarker = this.httpAddMarker();
    String removeMarker = this.httpRemoveMarker();

    DiffMatchPatch dmp = new DiffMatchPatch();
    LinkedList<DiffMatchPatch.Diff> diffs = dmp.diffMain(oldValue, newValue);
    dmp.diffCleanupSemantic(diffs);
    StringBuilder outputStr = new StringBuilder();
    for (DiffMatchPatch.Diff d : diffs) {
      if (DiffMatchPatch.Operation.EQUAL.equals(d.operation)) {
        // merging equal values of both string
        outputStr.append(d.text.trim()).append(" ");
      } else if (DiffMatchPatch.Operation.INSERT.equals(d.operation)) {
        // merging added values with addMarker before and after of new values added
        outputStr.append(addMarker).append(d.text.trim()).append(addMarker).append(" ");
      } else {
        // merging deleted values with removeMarker before and after of old value removed ..
        outputStr.append(removeMarker).append(d.text.trim()).append(removeMarker).append(" ");
      }
    }
    String diff = outputStr.toString().trim();
    // The additions and removals will be wrapped by <!add> and <!remove> tags
    // Replace them with html tags to render nicely in the UI
    // Example: This is a test <!remove>sentence<!remove><!add>line<!add>
    // This is a test <span class="diff-removed">sentence</span><span class="diff-added">line</span>
    diff = this.replaceMarkers(diff, addMarker, this.getAddMarker(), this.getAddMarkerClose());
    diff =
        this.replaceMarkers(
            diff, removeMarker, this.getRemoveMarker(), this.getRemoveMarkerClose());
    return diff;
  }

  default String replaceMarkers(String diff, String marker, String openTag, String closeTag) {
    int index = 0;
    while (diff.contains(marker)) {
      String replacement = index % 2 == 0 ? openTag : closeTag;
      diff = diff.replaceFirst(marker, replacement);
      index++;
    }
    return diff;
  }

  default OutgoingMessage createEntityMessage(String publisherName, ChangeEvent event) {
    OutgoingMessage message = new OutgoingMessage();
    message.setUserName(event.getUserName());
    EntityInterface entityInterface = getEntity(event);
    if (event.getEntity() != null) {
      String eventType;
      if (event.getEntity() instanceof TestCase) {
        eventType = "testSuite";
      } else {
        eventType = event.getEntityType();
      }
      String headerTxt;
      String headerText;
      if (eventType.equals(Entity.QUERY)) {
        headerTxt = "[%s] %s posted on " + eventType;
        headerText = String.format(headerTxt, publisherName, event.getUserName());
      } else {
        String entityUrl = this.buildEntityUrl(event.getEntityType(), entityInterface);
        message.setEntityUrl(entityUrl);
        headerTxt = "[%s] %s posted on " + eventType + " %s";
        headerText = String.format(headerTxt, publisherName, event.getUserName(), entityUrl);
      }
      message.setHeader(headerText);
    }
    List<Thread> thread = FeedUtils.getThreadWithMessage(this, event);
    List<String> messages = new ArrayList<>();
    thread.forEach(entry -> messages.add(entry.getMessage()));
    message.setMessages(messages);
    return message;
  }

  default OutgoingMessage createThreadMessage(String publisherName, ChangeEvent event) {
    OutgoingMessage message = new OutgoingMessage();
    message.setUserName(event.getUserName());
    Thread thread = getThread(event);

    MessageParser.EntityLink entityLink = MessageParser.EntityLink.parse(thread.getAbout());
    EntityInterface entityInterface = Entity.getEntity(entityLink, "", Include.ALL);
    String entityUrl = buildEntityUrl(entityLink.getEntityType(), entityInterface);

    String headerMessage = "";
    List<String> attachmentList = new ArrayList<>();

    String assetUrl =
        getThreadAssetsUrl(thread.getType(), MessageParser.EntityLink.parse(thread.getAbout()));
    switch (thread.getType()) {
      case Conversation -> {
        switch (event.getEventType()) {
          case THREAD_CREATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s started a conversation for asset %s",
                    publisherName, thread.getCreatedBy(), assetUrl);
            attachmentList.add(replaceEntityLinks(thread.getMessage()));
          }
          case POST_CREATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s posted a message on asset %s",
                    publisherName, thread.getCreatedBy(), assetUrl);
            attachmentList.add(
                String.format(
                    "@%s : %s", thread.getCreatedBy(), replaceEntityLinks(thread.getMessage())));
            thread
                .getPosts()
                .forEach(
                    post ->
                        attachmentList.add(
                            String.format(
                                "@%s : %s",
                                post.getFrom(), replaceEntityLinks(post.getMessage()))));
          }
          case THREAD_UPDATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s posted update on Conversation for asset %s",
                    publisherName, thread.getUpdatedBy(), assetUrl);
            attachmentList.add(replaceEntityLinks(thread.getMessage()));
          }
        }
      }
      case Task -> {
        switch (event.getEventType()) {
          case THREAD_CREATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s created a Task for %s %s",
                    publisherName, thread.getCreatedBy(), entityLink.getEntityType(), assetUrl);
            attachmentList.add(String.format("Task Type : %s", thread.getTask().getType().value()));
            attachmentList.add(
                String.format(
                    "Assignees : %s",
                    convertInputListToString(
                        thread.getTask().getAssignees().stream()
                            .map(assignee -> String.format("@%s", assignee.getName()))
                            .toList())));
            attachmentList.add(String.format("Current Status : %s", thread.getTask().getStatus()));
          }
          case POST_CREATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s posted a message on the Task with Id : %s for Asset %s",
                    publisherName, thread.getCreatedBy(), thread.getTask().getId(), assetUrl);
            thread
                .getPosts()
                .forEach(
                    post ->
                        attachmentList.add(
                            String.format(
                                "@%s : %s",
                                post.getFrom(), replaceEntityLinks(post.getMessage()))));
          }
          case THREAD_UPDATED -> {
            headerMessage =
                String.format(
                    "[%s] @%s posted update on the Task with Id : %s for Asset %s",
                    publisherName, thread.getUpdatedBy(), thread.getTask().getId(), assetUrl);
            attachmentList.add(String.format("Task Type : %s", thread.getTask().getType().value()));
            attachmentList.add(
                String.format(
                    "Assignees : %s",
                    convertInputListToString(
                        thread.getTask().getAssignees().stream()
                            .map(assignee -> String.format("@%s", assignee.getName()))
                            .toList())));
            attachmentList.add(String.format("Current Status : %s", thread.getTask().getStatus()));
          }
          case TASK_CLOSED -> {
            headerMessage =
                String.format(
                    "[%s] @%s closed Task with Id : %s for Asset %s",
                    publisherName, thread.getCreatedBy(), thread.getTask().getId(), assetUrl);
            attachmentList.add(String.format("Current Status : %s", thread.getTask().getStatus()));
          }
          case TASK_RESOLVED -> {
            headerMessage =
                String.format(
                    "[%s] @%s resolved Task with Id : %s for Asset %s",
                    publisherName, thread.getCreatedBy(), thread.getTask().getId(), assetUrl);
            attachmentList.add(String.format("Current Status : %s", thread.getTask().getStatus()));
          }
        }
      }
      case Announcement -> {
        switch (event.getEventType()) {
          case THREAD_CREATED -> {
            headerMessage =
                String.format(
                    "[%s] **@%s** posted an **Announcement**",
                    publisherName, thread.getCreatedBy());
            attachmentList.add(
                String.format("Description : %s", thread.getAnnouncement().getDescription()));
            attachmentList.add(
                String.format(
                    "Started At : %s", getDateString(thread.getAnnouncement().getStartTime())));
            attachmentList.add(
                String.format(
                    "Ends At : %s", getDateString(thread.getAnnouncement().getEndTime())));
          }
          case POST_CREATED -> {
            headerMessage =
                String.format(
                    "**@%s** posted a message on **Announcement**", thread.getCreatedBy());
            thread
                .getPosts()
                .forEach(
                    post ->
                        attachmentList.add(
                            String.format(
                                "@%s : %s",
                                post.getFrom(), replaceEntityLinks(post.getMessage()))));
          }
          case THREAD_UPDATED -> {
            headerMessage =
                String.format(
                    "[%s] **@%s** posted an update on  **Announcement**",
                    publisherName, thread.getUpdatedBy());
            attachmentList.add(
                String.format("Description : %s", thread.getAnnouncement().getDescription()));
            attachmentList.add(
                String.format(
                    "Started At : %s", getDateString(thread.getAnnouncement().getStartTime())));
            attachmentList.add(
                String.format(
                    "Ends At : %s", getDateString(thread.getAnnouncement().getEndTime())));
          }
          case ENTITY_DELETED -> {
            headerMessage =
                String.format(
                    "[%s] **@%s** posted an update on  **Announcement**",
                    publisherName, thread.getUpdatedBy());
            attachmentList.add(
                String.format(
                    "Announcement Deleted: %s", thread.getAnnouncement().getDescription()));
          }
        }
      }
    }
    if (nullOrEmpty(headerMessage) || attachmentList.isEmpty()) {
      throw new UnhandledServerException("Unable to build message");
    }
    message.setHeader(headerMessage);
    message.setMessages(attachmentList);

    message.setEntityUrl(entityUrl);
    return message;
  }

  default String getThreadAssetsUrl(
      ThreadType threadType, MessageParser.EntityLink aboutEntityLink) {
    try {
      return this.buildThreadUrl(
          threadType,
          aboutEntityLink.getEntityType(),
          Entity.getEntity(aboutEntityLink, "id", Include.ALL));
    } catch (Exception ex) {
      return "";
    }
  }

  static String getDateString(long epochTimestamp) {
    Instant instant = Instant.ofEpochSecond(epochTimestamp);
    return getDateString(instant);
  }

  static String getDateStringEpochMilli(long epochTimestamp) {
    Instant instant = Instant.ofEpochMilli(epochTimestamp);
    return getDateString(instant);
  }

  private static String getDateString(Instant instant) {
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

    // Format LocalDateTime to a specific date and time format
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return localDateTime.format(formatter);
  }

  /**
   * A builder class for creating a map of template data organized by sections and keys.
   * @param <S> The type of template sections (must be an enum).
   */
  class TemplateDataBuilder<S extends Enum<S>> {
    private final Map<S, Map<Enum<?>, Object>> templateMap = new HashMap<>();

    /**
     * @param section The section of the template.
     * @param key     The key for the section, represented as an enum.
     * @param <K>     The type of the enum used as the key (must extend Enum).
     */
    @SuppressWarnings("unchecked")
    public <K extends Enum<K>> TemplateDataBuilder<S> add(S section, K key, Object value) {
      // Ensure type-safe EnumMap by explicitly specifying EnumMap<K, Object>
      templateMap
          .computeIfAbsent(
              section, k -> (Map<Enum<?>, Object>) new EnumMap<>(key.getDeclaringClass()))
          .put(key, value);
      return this;
    }

    public Map<S, Map<Enum<?>, Object>> build() {
      return Collections.unmodifiableMap(templateMap);
    }
  }

  enum General_Template_Section {
    EVENT_DETAILS,
  }

  enum DQ_Template_Section {
    EVENT_DETAILS,
    TEST_CASE_DETAILS,
    TEST_CASE_RESULT,
    TEST_DEFINITION
  }

  enum EventDetailsKeys {
    EVENT_TYPE,
    UPDATED_BY,
    ENTITY_TYPE,
    ENTITY_FQN,
    PUBLISHER,
    TIME,
    OUTGOING_MESSAGE
  }

  enum DQ_TestCaseDetailsKeys {
    ID,
    NAME,
    OWNERS,
    TAGS,
    DESCRIPTION,
    TEST_CASE_FQN,
    INSPECTION_QUERY,
    SAMPLE_DATA
  }

  enum DQ_TestCaseResultKeys {
    STATUS,
    PARAMETER_VALUE,
    RESULT_MESSAGE
  }

  enum DQ_TestDefinitionKeys {
    TEST_DEFINITION_NAME,
    TEST_DEFINITION_DESCRIPTION
  }
}
