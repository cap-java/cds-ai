/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.utils;

import static com.sap.cds.service.ExtractionStatus.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StatusTransitionValidatorTest {

  @Test
  void pendingToSubmittedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PENDING, SUBMITTED)).isTrue();
  }

  @Test
  void pendingToFailedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PENDING, FAILED)).isTrue();
  }

  @Test
  void submittedToRunningIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(SUBMITTED, RUNNING)).isTrue();
  }

  @Test
  void submittedToFailedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(SUBMITTED, FAILED)).isTrue();
  }

  @Test
  void submittedToDoneIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(SUBMITTED, DONE)).isTrue();
  }

  @Test
  void runningToDoneIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(RUNNING, DONE)).isTrue();
  }

  @Test
  void runningToFailedIsValid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(RUNNING, FAILED)).isTrue();
  }

  @Test
  void pendingToDoneIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(PENDING, DONE)).isFalse();
  }

  @Test
  void runningToPendingIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(RUNNING, PENDING)).isFalse();
  }

  @Test
  void doneToRunningIsInvalid() {
    Assertions.assertThat(StatusTransitionValidator.isValid(DONE, RUNNING)).isFalse();
  }

  @Test
  void sameTransitionIsIdempotent() {
    Assertions.assertThat(StatusTransitionValidator.isValid(RUNNING, RUNNING)).isTrue();
  }
}
