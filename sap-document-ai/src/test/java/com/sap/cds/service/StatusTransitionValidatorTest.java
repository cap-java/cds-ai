/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StatusTransitionValidatorTest {

  @Test
  void pendingToProcessingIsValid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(
                ExtractionStatus.PENDING, ExtractionStatus.PROCESSING))
        .isTrue();
  }

  @Test
  void processingToCompletedIsValid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(
                ExtractionStatus.PROCESSING, ExtractionStatus.COMPLETED))
        .isTrue();
  }

  @Test
  void processingToFailedIsValid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(ExtractionStatus.PROCESSING, ExtractionStatus.FAILED))
        .isTrue();
  }

  @Test
  void pendingToCompletedIsInvalid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(ExtractionStatus.PENDING, ExtractionStatus.COMPLETED))
        .isFalse();
  }

  @Test
  void processingToPendingIsInvalid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(
                ExtractionStatus.PROCESSING, ExtractionStatus.PENDING))
        .isFalse();
  }

  @Test
  void completedToProcessingIsInvalid() {
    Assertions.assertThat(
            StatusTransitionValidator.isValid(
                ExtractionStatus.COMPLETED, ExtractionStatus.PROCESSING))
        .isFalse();
  }
}
