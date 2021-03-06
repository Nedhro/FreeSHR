package org.freeshr.validations.bundle;


import org.freeshr.config.SHRProperties;
import org.freeshr.utils.StringUtils;
import org.freeshr.validations.*;
import org.hl7.fhir.dstu3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.freeshr.validations.ValidationMessages.HEALTH_ID_NOT_MATCH;
import static org.freeshr.validations.ValidationMessages.HEALTH_ID_NOT_PRESENT_IN_COMPOSITION;

@Component
public class HealthIdValidator implements ShrValidator<EncounterValidationContext> {

    private static final Logger logger = LoggerFactory.getLogger(HealthIdValidator.class);
    private SHRProperties shrProperties;
    //match all urls that have /api/*/patients, 2nd groups contains the variable
    private Pattern healthIdReferencePattern = Pattern.compile("(^(http|https)://(?<host>.+)\\/api\\/)(\\w+)(\\/patients\\/(?<hid>.+))");

    @Autowired
    public HealthIdValidator(SHRProperties shrProperties) {
        this.shrProperties = shrProperties;
    }

    @Override
    public List<ShrValidationMessage> validate(ValidationSubject<EncounterValidationContext> validationSubject) {
        EncounterValidationContext validationContext = validationSubject.extract();
        Bundle bundle = validationContext.getBundle();
        String expectedHealthId = validationContext.getHealthId();
        List<ShrValidationMessage> validationMessages = new ArrayList<>();
        List<Bundle.BundleEntryComponent> entries = bundle.getEntry();
        for (int i = 0; i < entries.size(); i++) {
            Bundle.BundleEntryComponent entry = entries.get(i);
            Resource resource = entry.getResource();
            if (!PatientReferenceIdentifier.canIdentify(resource)) {
                continue;
            }
            Reference patientRef = PatientReferenceIdentifier.identifyPatientReference(resource);
            String patientRefUrl = getPatientRefUrl(patientRef);

            if ((resource instanceof Composition) && (patientRefUrl == null)) {
                logger.error(String.format("Encounter failed for %s", HEALTH_ID_NOT_PRESENT_IN_COMPOSITION));
                ShrValidationMessage message = new ShrValidationMessage(Severity.ERROR, "Bundle.entry[1].resource.subject",
                        "invalid", "Composition:" + HEALTH_ID_NOT_PRESENT_IN_COMPOSITION);
                validationMessages.add(message);
                return validationMessages;
            }

            String healthIdFromUrl = validateAndIdentifyPatientId(patientRefUrl, expectedHealthId);
            if (healthIdFromUrl == null) {
                logger.debug(String.format("Encounter failed for %s", HEALTH_ID_NOT_MATCH));
                ShrValidationMessage message = new ShrValidationMessage(Severity.ERROR,
                        String.format("Bundle.entry[%s].resource.subject", i + 1), "invalid",
                        patientRef.getReference() + ":" + HEALTH_ID_NOT_MATCH);
                validationMessages.add(message);
            }
        }
        return validationMessages;
    }

    private String getPatientRefUrl(Reference patientRef) {
        if ((patientRef == null) || (patientRef.getReference() == null)) return null;
        return patientRef.getReference();
    }

    public String validateAndIdentifyPatientId(String patientRefUrl, String healthId) {
        if (org.apache.commons.lang3.StringUtils.isBlank(patientRefUrl)) return null;
        String expectedUrl = StringUtils.ensureSuffix(shrProperties.getPatientReferencePath(), "/") + healthId;
        Matcher actual = healthIdReferencePattern.matcher(patientRefUrl);
        Matcher expected = healthIdReferencePattern.matcher(expectedUrl);

        if (!actual.find() || !expected.find() || actual.groupCount() != 6) return null;
        if (expected.group("host").equalsIgnoreCase(actual.group("host")) && expected.group("hid").equalsIgnoreCase(actual.group("hid")))
            return actual.group("hid");
        return null;
    }

    public static class PatientReferenceIdentifier {
        public static List<Class<? extends DomainResource>> supportedTypes = Arrays.asList(Composition.class, Encounter.class, Condition.class, FamilyMemberHistory.class,
                Observation.class, ProcedureRequest.class, DiagnosticReport.class, Specimen.class,
                Immunization.class, Procedure.class, MedicationRequest.class);

        public static boolean canIdentify(Resource resource) {
            return supportedTypes.contains(resource.getClass());
        }

        public static Reference identifyPatientReference(Resource resource) {
            Reference patientRef = null;
            if (resource instanceof Composition) {
                patientRef = ((Composition) resource).getSubject();
            } else if (resource instanceof Encounter) {
                patientRef = ((Encounter) resource).getSubject();
            } else if (resource instanceof Condition) {
                patientRef = ((Condition) resource).getSubject();
            } else if (resource instanceof FamilyMemberHistory) {
                patientRef = ((FamilyMemberHistory) resource).getPatient();
            } else if (resource instanceof Observation) {
                patientRef = ((Observation) resource).getSubject();
            } else if (resource instanceof ProcedureRequest) {
                patientRef = ((ProcedureRequest) resource).getSubject();
            } else if (resource instanceof DiagnosticReport) {
                patientRef = ((DiagnosticReport) resource).getSubject();
            } else if (resource instanceof Specimen) {
                patientRef = ((Specimen) resource).getSubject();
            } else if (resource instanceof Immunization) {
                patientRef = ((Immunization) resource).getPatient();
            } else if (resource instanceof Procedure) {
                patientRef = ((Procedure) resource).getSubject();
            } else if (resource instanceof MedicationRequest) {
                patientRef = ((MedicationRequest) resource).getSubject();
            }
            return patientRef;
        }

    }

}
