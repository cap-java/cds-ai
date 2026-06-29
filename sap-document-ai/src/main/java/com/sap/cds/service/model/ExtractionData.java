/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.model;

/**
 * Immutable value object holding the raw poll response returned by the DIE service for a job.
 *
 * @param dieJobId the job ID assigned by DIE
 * @param dieStatus the status string as returned by DIE (e.g. {@code PENDING}, {@code RUNNING},
 *     {@code DONE}, {@code FAILED})
 * @param rawResult the full JSON response body; only meaningful when {@code dieStatus} is {@code
 *     DONE}
 */
public record ExtractionData(String dieJobId, String dieStatus, String rawResult) {}
