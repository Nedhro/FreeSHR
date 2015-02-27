package org.freeshr.validations;

import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.application.fhir.FhirMessageFilter;
import org.freeshr.config.SHRConfig;
import org.freeshr.config.SHREnvironmentMock;
import org.freeshr.utils.FileUtil;
import org.freeshr.utils.ResourceOrFeedDeserializer;
import org.hl7.fhir.instance.validation.ValidationMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = SHREnvironmentMock.class, classes = SHRConfig.class)
public class ProviderValidatorTest {

    @Autowired
    ProviderValidator providerValidator;
    ResourceOrFeedDeserializer resourceOrFeedDeserializer;
    FhirMessageFilter fhirMessageFilter;

    @Before
    public void setup() {
        resourceOrFeedDeserializer = new ResourceOrFeedDeserializer();
        fhirMessageFilter = new FhirMessageFilter();
    }

    @Test
    public void shouldValidateEncounterWithValidProvider() throws Exception {
        final String xml = FileUtil.asString("xmls/encounters/encounter_with_valid_participant_and_orderer.xml");
        List<ValidationMessage> validationMessages = providerValidator.validate(getEncounterContext(xml, "5893922485019082753")
                .extract().feedFragment());
        assertTrue(validationMessages.isEmpty());
    }

    @Test
    public void shouldFailEncounterWithInvalidProvider() throws Exception {
        final String xml = FileUtil.asString("xmls/encounters/encounter_with_invalid_participant_and_orderer.xml");
        List<ValidationMessage> validationMessages = providerValidator.validate(getEncounterContext(xml, "5893922485019082753")
                .extract().feedFragment());
        assertEquals(1, validationMessages.size());
    }

    @Test
    public void shouldValidateEncounterWithoutAnyProvider() throws Exception {
        final String xml = FileUtil.asString("xmls/encounters/encounter_without_provider.xml");
        List<ValidationMessage> validationMessages = providerValidator.validate(getEncounterContext(xml, "5893922485019082753")
                .extract().feedFragment());
        assertTrue(validationMessages.isEmpty());
    }

    private ValidationSubject<EncounterValidationContext> getEncounterContext(final String xml, final String healthId) {
        return new ValidationSubject<EncounterValidationContext>() {
            @Override
            public EncounterValidationContext extract() {
                EncounterBundle encounterBundle = new EncounterBundle();
                encounterBundle.setEncounterContent(xml);
                encounterBundle.setHealthId(healthId);
                return new EncounterValidationContext(encounterBundle, resourceOrFeedDeserializer);

            }
        };
    }
}