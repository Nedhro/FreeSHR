package org.freeshr.domain.service;

import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.application.fhir.EncounterResponse;
import org.freeshr.application.fhir.EncounterValidationResponse;
import org.freeshr.config.SHRProperties;
import org.freeshr.domain.model.Requester;
import org.freeshr.domain.model.patient.Patient;
import org.freeshr.events.EncounterEvent;
import org.freeshr.infrastructure.persistence.EncounterRepository;
import org.freeshr.infrastructure.persistence.RxMaps;
import org.freeshr.infrastructure.security.UserInfo;
import org.freeshr.utils.Confidentiality;
import org.freeshr.utils.FhirFeedUtil;
import org.freeshr.validations.EncounterValidationContext;
import org.freeshr.validations.HapiEncounterValidator;
import org.freeshr.validations.ShrEncounterValidator;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rx.Observable;
import rx.functions.Func1;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.freeshr.utils.Confidentiality.getConfidentiality;
import static org.freeshr.utils.FhirFeedUtil.FHIR_SCHEMA_VERSION;
import static org.freeshr.validations.ValidationMessages.*;

@Service
public class PatientEncounterService {
    private EncounterRepository encounterRepository;
    private PatientService patientService;
    private ShrEncounterValidator shrEncounterValidator;
    private FhirFeedUtil fhirFeedUtil;
    private SHRProperties shrProperties;

    private static final Logger logger = LoggerFactory.getLogger(PatientEncounterService.class);

    @Autowired
    public PatientEncounterService(EncounterRepository encounterRepository, PatientService patientService,
                                   HapiEncounterValidator hapiEncounterValidator,
                                   FhirFeedUtil fhirFeedUtil, SHRProperties shrProperties) {
        this.encounterRepository = encounterRepository;
        this.patientService = patientService;
        this.fhirFeedUtil = fhirFeedUtil;
        this.shrProperties = shrProperties;
        this.shrEncounterValidator = hapiEncounterValidator;
    }

    public Observable<EncounterResponse> ensureCreated(final EncounterBundle encounterBundle, UserInfo userInfo) throws ExecutionException, InterruptedException {
        EncounterValidationResponse validationResult = validate(encounterBundle);
        if (validationResult.isSuccessful()) {
            Observable<Patient> patientObservable = patientService.ensurePresent(encounterBundle.getHealthId(), userInfo);
            Confidentiality confidentiality = determineEncounterConfidentiality(validationResult);
            return patientObservable.flatMap(patientFetchSuccessCallbackOnCreate(encounterBundle, confidentiality, userInfo),
                    RxMaps.<EncounterResponse>logAndForwardError(logger),
                    RxMaps.<EncounterResponse>completeResponds());

        } else {
            return Observable.just(new EncounterResponse().setValidationFailure(validationResult));
        }
    }

    public Observable<EncounterResponse> ensureUpdated(final EncounterBundle encounterBundle, final UserInfo userInfo) throws ExecutionException, InterruptedException {
        final EncounterValidationResponse validationResult = validate(encounterBundle);
        if (validationResult.isSuccessful()) {
            final Confidentiality confidentiality = determineEncounterConfidentiality(validationResult);
            Observable<Patient> patientObservable = patientService.ensurePresent(encounterBundle.getHealthId(),
                    userInfo);
            return patientObservable.flatMap(patientFetchSuccessCallBackOnUpdate(encounterBundle, confidentiality, userInfo),
                    RxMaps.<EncounterResponse>logAndForwardError(logger),
                    RxMaps.<EncounterResponse>completeResponds());

        } else {
            return Observable.just(new EncounterResponse().setValidationFailure(validationResult));
        }
    }

    public Observable<EncounterBundle> findEncounter(final String healthId, String encounterId) {
        return encounterRepository.findEncounterById(encounterId).filter(new Func1<EncounterBundle, Boolean>() {
            @Override
            public Boolean call(EncounterBundle encounterBundle) {
                return encounterBundle.getHealthId().equals(healthId);
            }
        });
    }

    public Observable<List<EncounterEvent>> getEncounterFeedForPatient(String healthId, Date sinceDate,
                                                                       int limit) throws ExecutionException,
            InterruptedException {
        return encounterRepository.getEncounterFeedForPatient(healthId, sinceDate, limit);
    }

    private Confidentiality determineEncounterConfidentiality(EncounterValidationResponse validationResult) {
        if (FHIR_SCHEMA_VERSION.equals(shrProperties.getFhirDocumentSchemaVersion())) {
            return getEncounterConfidentiality(validationResult.getBundle());
        } else {
            return null;
//            return getEncounterConfidentiality(validationResult.getFeed());
        }
    }

    private Func1<EncounterBundle, Observable<EncounterResponse>> encounterFetchSuccessCallbackForUpdate(
            final EncounterBundle encounterBundle, final Patient patient, final Confidentiality confidentiality, final UserInfo userInfo) {
        return new Func1<EncounterBundle, Observable<EncounterResponse>>() {
            @Override
            public Observable<EncounterResponse> call(EncounterBundle existingEncounterBundle) {
                if (existingEncounterBundle != null) {
                    return updateEncounter(existingEncounterBundle, userInfo, encounterBundle, confidentiality, patient);
                } else {
                    return Observable.just(new EncounterResponse().preconditionFailure("encounterId", "invalid",
                            String.format(ENCOUNTER_NOT_FOUND_MSG_PATTERN, encounterBundle.getEncounterId())));
                }
            }
        };
    }

    private Func1<Patient, Observable<EncounterResponse>> patientFetchSuccessCallBackOnUpdate(final EncounterBundle encounterBundle, final Confidentiality confidentiality, final UserInfo userInfo) {
        return new Func1<Patient, Observable<EncounterResponse>>() {
            @Override
            public Observable<EncounterResponse> call(Patient patient) {
                EncounterResponse response = validatePatient(patient);
                if (response.isSuccessful()) {
                    Observable<EncounterBundle> encounterFetchObservable = findEncounter(encounterBundle.getHealthId(), encounterBundle.getEncounterId()).firstOrDefault(null);
                    return encounterFetchObservable.flatMap(encounterFetchSuccessCallbackForUpdate(encounterBundle, patient, confidentiality, userInfo),
                            RxMaps.<EncounterResponse>logAndForwardError(logger),
                            RxMaps.<EncounterResponse>completeResponds());


                }
                return Observable.just(response);
            }
        };
    }

    private Func1<Patient, Observable<EncounterResponse>> patientFetchSuccessCallbackOnCreate(final EncounterBundle encounterBundle, final Confidentiality bundleConfidentiality, final UserInfo userInfo) {
        return new Func1<Patient, Observable<EncounterResponse>>() {
            @Override
            public Observable<EncounterResponse> call(Patient patient) {
                final EncounterResponse response = validatePatient(patient);
                if (response.isSuccessful()) {
                    populateEncounterBundleFields(patient, encounterBundle, bundleConfidentiality, userInfo);

                    Observable<Boolean> save = encounterRepository.save(encounterBundle, patient);

                    return save.flatMap(new Func1<Boolean, Observable<EncounterResponse>>() {
                                            @Override
                                            public Observable<EncounterResponse> call(Boolean aBoolean) {
                                                if (aBoolean)
                                                    response.setEncounterId(encounterBundle.getEncounterId());
                                                return Observable.just(response);
                                            }
                                        },
                            RxMaps.<EncounterResponse>logAndForwardError(logger),
                            RxMaps.<EncounterResponse>completeResponds());
                }
                return Observable.just(response);
            }
        };
    }

    private Observable<EncounterResponse> updateEncounter(EncounterBundle existingEncounterBundle, UserInfo userInfo, final EncounterBundle encounterBundle, Confidentiality confidentiality, Patient patient) {
        Requester updatedBy = new Requester(userInfo.getProperties().getFacilityId(), userInfo.getProperties().getProviderId());
        if (isEncounterEditAllowed(existingEncounterBundle, updatedBy)) {
            final EncounterResponse response = new EncounterResponse();
            encounterBundle.setEncounterConfidentiality(confidentiality);
            encounterBundle.setUpdatedAt(new Date());
            encounterBundle.setUpdatedBy(updatedBy);
            encounterBundle.setContentVersion(existingEncounterBundle.getContentVersion() + 1);
            encounterBundle.setReceivedAt(existingEncounterBundle.getReceivedAt());
            encounterBundle.setReceivedEventReference(existingEncounterBundle.getReceivedEventReference());

            Observable<Boolean> update = encounterRepository.updateEncounter(encounterBundle, existingEncounterBundle, patient);

            return update.flatMap(new Func1<Boolean, Observable<EncounterResponse>>() {
                                      @Override
                                      public Observable<EncounterResponse> call(Boolean aBoolean) {
                                          if (aBoolean)
                                              response.setEncounterId(encounterBundle.getEncounterId());
                                          return Observable.just(response);
                                      }
                                  },
                    RxMaps.<EncounterResponse>logAndForwardError(logger),
                    RxMaps.<EncounterResponse>completeResponds());

        } else {
            return Observable.just(new EncounterResponse().forbidden("updatedBy", "not authorized",
                    String.format("Access is denied to requester (%s) to edit the encounter", updatedBy)));
        }
    }

    private boolean isEncounterEditAllowed(EncounterBundle existingEncounterBundle, Requester updatedBy) {
        return existingEncounterBundle.getCreatedBy().equals(updatedBy);
    }

    private EncounterResponse validatePatient(Patient patient) {
        EncounterResponse encounterResponse = new EncounterResponse();
        if (patient == null) {
            encounterResponse.preconditionFailure("healthId", "invalid", INVALID_PATIENT);
        } else if (patient.getMergedWith() != null) {
            encounterResponse.activePatientFailure("healthId", "inactive", String.format(INACTIVE_PATIENT_MSG_PATTERN, patient.getHealthId(), patient.getMergedWith()));
        }

        return encounterResponse;
    }

    private void populateEncounterBundleFields(Patient patient, EncounterBundle encounterBundle, Confidentiality confidentiality, UserInfo userInfo) {
        encounterBundle.setEncounterId(UUID.randomUUID().toString());
        encounterBundle.setPatientConfidentiality(patient.getConfidentiality());
        encounterBundle.setEncounterConfidentiality(confidentiality);
        Date currentTimestamp = new Date();
        encounterBundle.setReceivedAt(currentTimestamp);
        encounterBundle.setUpdatedAt(currentTimestamp);

        Requester requester = new Requester(userInfo.getProperties().getFacilityId(), userInfo.getProperties().getProviderId());
        encounterBundle.setCreatedBy(requester);
        encounterBundle.setUpdatedBy(requester);
    }

    private EncounterValidationResponse validate(EncounterBundle encounterBundle) {
        EncounterValidationContext validationContext = new EncounterValidationContext(encounterBundle, fhirFeedUtil);
        return shrEncounterValidator.validate(validationContext);
    }


    private Confidentiality getEncounterConfidentiality(Bundle bundle) {
        Confidentiality encounterConfidentiality = Confidentiality.Normal;
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource().getResourceType().equals(ResourceType.Composition)) {
                Composition composition = (Composition) entry.getResource();
                if (composition.getConfidentiality() != null) {
                    encounterConfidentiality = getConfidentiality(composition.getConfidentiality().toString());
                }
                break;
            }
        }
        return encounterConfidentiality;
    }
}
