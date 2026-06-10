/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StatusTransitionValidatorTest {

  @Test
  void pendingToProcessingIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PENDING, PROCESSING)).isTrue();
  }

  @Test
  void processingToCompletedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PROCESSING, COMPLETED)).isTrue();
  }

  @Test
  void processingToFailedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PROCESSING, FAILED)).isTrue();
  }

  @Test
  void pendingToCompletedIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PENDING, COMPLETED)).isFalse();
  }

  @Test
  void processingToPendingIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PROCESSING, PENDING)).isFalse();
  }

  @Test
  void completedToProcessingIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(COMPLETED, PROCESSING)).isFalse();
  }

  @Test
  void sameTransitionIsIdempotent() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PROCESSING, PROCESSING)).isTrue();
  }
}
